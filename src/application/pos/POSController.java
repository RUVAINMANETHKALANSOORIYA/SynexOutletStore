package application.pos;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.billing.BillWriter;
import domain.common.Money;
import domain.inventory.InventoryReservation;
import domain.inventory.BatchDiscount; // Add this import
import ports.in.InventoryService;
import application.inventory.InventoryAdminService;
import domain.payment.CashPayment;
import domain.payment.CardPayment;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import application.pricing.AutoDiscountService;
import ports.out.BillRepository;

import application.events.EventBus;
import application.events.NoopEventBus;
import application.events.events.BillPaid;
import application.events.events.RestockThresholdHit;
import application.events.events.StockDepleted;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public final class POSController {
    private final InventoryService inventory;
    private final InventoryAdminService inventoryAdmin;
    private final PricingService pricing;
    private final BillNumberGenerator billNos;
    private final BillRepository bills;
    private final BillWriter writer;
    private final EventBus events; // Observer
    private final AutoDiscountService autoDiscountService; // New: Automatic discount detection

    private Bill active = null;
    private DiscountPolicy activeDiscount = null;

    private final List<InventoryReservation> shelfReservations = new ArrayList<>();
    private final List<InventoryReservation> storeReservations = new ArrayList<>();

    private Payment.Receipt paymentReceipt = null;

    private String currentUser = "operator";
    private String currentChannel = "POS";

    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, null, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, invAdmin, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw, EventBus events) {
        this.inventory = inv;
        this.inventoryAdmin = invAdmin;
        this.pricing = pr;
        this.billNos = gen;
        this.bills = br;
        this.writer = bw;
        this.events = (events == null) ? new NoopEventBus() : events;
        this.autoDiscountService = new AutoDiscountService(inventoryAdmin); // Pass inventoryAdmin for batch discounts
    }

    public void setUser(String user) {
        this.currentUser = (user == null || user.isBlank()) ? "operator" : user.trim();
        if (active != null) active.setUserName(this.currentUser);
    }
    public void setChannel(String channel) {
        this.currentChannel = (channel == null || channel.isBlank()) ? "POS" : channel.trim().toUpperCase();
        if (active != null) active.setChannel(this.currentChannel);
    }

    public void newBill() {
        // Allow starting a new bill even if one is active (reset state)
        active = new Bill(billNos.next());
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        shelfReservations.clear();
        storeReservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
    }

    public void addItem(String code, int qty) {
        ensure();

        // Input validation
        validateItemCode(code);
        validateQuantity(qty);

        // Inventory validation - check if item exists
        validateItemExists(code);

        // Check available stock before attempting reservation
        validateStockAvailability(code, qty);

        try {
            var res = inventory.reserveByChannel(code, qty, currentChannel);
            if ("POS".equalsIgnoreCase(currentChannel)) {
                storeReservations.addAll(res);
            } else {
                shelfReservations.addAll(res);
            }

            // Calculate the best price considering batch discounts
            Money effectivePrice = calculateBestPriceFromReservations(code, res);

            var line = new BillLine(code, inventory.itemName(code), effectivePrice, qty, res);
            active.addLine(line);

            // Auto-detect and apply the best available discount after adding the item
            autoApplyBestDiscount();
        } catch (IllegalStateException e) {
            // Handle insufficient stock or reservation failures
            throw new POSOperationException("Insufficient stock for item " + code + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Handle other inventory service exceptions
            throw new POSOperationException("Inventory operation failed for item " + code + ": " + e.getMessage(), e);
        } catch (Exception e) {
            // Handle any unexpected exceptions
            throw new POSOperationException("Failed to add item " + code + " to bill: " + e.getMessage(), e);
        }
    }

    public InventoryService.SmartPick addItemSmart(String code, int qty,
                                                   boolean approveUseOtherSide,
                                                   boolean managerApprovedBackfill) {
        ensure();

        // Input validation
        validateItemCode(code);
        validateQuantity(qty);
        validateBooleanParameters(approveUseOtherSide, managerApprovedBackfill);

        // Inventory validation - check if item exists
        validateItemExists(code);

        // Note: For smart pick, we don't validate stock availability upfront since it may use cross-channel logic

        try {
            var pick = inventory.reserveSmart(code, qty, currentChannel, approveUseOtherSide, managerApprovedBackfill);

            shelfReservations.addAll(pick.shelfReservations);
            storeReservations.addAll(pick.storeReservations);

            var combined = new ArrayList<InventoryReservation>(pick.shelfReservations.size() + pick.storeReservations.size());
            combined.addAll(pick.shelfReservations);
            combined.addAll(pick.storeReservations);

            // Calculate the best price considering batch discounts
            Money effectivePrice = calculateBestPriceFromReservations(code, combined);

            var line = new BillLine(code, inventory.itemName(code), effectivePrice, qty, combined);
            active.addLine(line);

            // Auto-detect and apply the best available discount after adding the item
            autoApplyBestDiscount();

            return pick;
        } catch (IllegalStateException e) {
            // Handle insufficient stock or reservation failures
            throw new POSOperationException("Smart pick failed for item " + code + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Handle other inventory service exceptions
            throw new POSOperationException("Inventory operation failed for item " + code + " using smart pick: " + e.getMessage(), e);
        } catch (Exception e) {
            // Handle any unexpected exceptions
            throw new POSOperationException("Failed to add item " + code + " using smart pick: " + e.getMessage(), e);
        }
    }

    /**
     * Automatically detects and applies batch-specific discounts for the current bill
     */
    private void autoApplyBestDiscount() {
        if (active == null || active.lines().isEmpty()) {
            return;
        }

        // Detect batch-specific discounts from the inventory admin service
        DiscountPolicy batchDiscount = autoDiscountService.detectBatchDiscounts(active);

        // Apply batch discounts if found
        if (batchDiscount != null) {
            activeDiscount = batchDiscount;

            // Calculate and display the total discount amount for the most recently added line
            BillLine lastLine = active.lines().get(active.lines().size() - 1);
            Money originalLineTotal = inventory.priceOf(lastLine.itemCode()).multiply(lastLine.quantity());
            Money discountedLineTotal = lastLine.lineTotal();
            Money lineSavings = originalLineTotal.minus(discountedLineTotal);

            if (lineSavings.compareTo(Money.ZERO) > 0) {
                System.out.println("🎉 Batch Discount Applied - Total savings for " + lastLine.quantity() +
                    " items: LKR " + String.format("%.2f", lineSavings.asBigDecimal().doubleValue()));
            }
        }
    }

    /**
     * Calculate the actual batch discount amount by comparing original prices with discounted line prices
     */
    private Money calculateActualBatchDiscountAmount() {
        Money totalOriginalPrice = Money.ZERO;
        Money totalDiscountedPrice = Money.ZERO;

        for (BillLine line : active.lines()) {
            Money originalPrice = inventory.priceOf(line.itemCode());
            totalOriginalPrice = totalOriginalPrice.plus(originalPrice.multiply(line.quantity()));
            totalDiscountedPrice = totalDiscountedPrice.plus(line.lineTotal());
        }

        return totalOriginalPrice.minus(totalDiscountedPrice);
    }

    public void removeItem(String code) {
        ensure();
        active.removeLineByCode(code);

        // Re-evaluate discounts after removing an item
        autoApplyBestDiscount();
    }

    public void applyDiscount(DiscountPolicy p) {
        ensure();
        activeDiscount = p;
    }

    public Money total() {
        ensure();
        ensureItemsAdded();
        pricing.finalizePricing(active, activeDiscount);
        return active.total();
    }

    public void payCash(double amount) {
        ensure();
        ensureItemsAdded();

        // Payment validation
        validateCashAmount(amount);

        try {
            pricing.finalizePricing(active, activeDiscount);
            Money billTotal = active.total();

            // Validate sufficient payment amount
            if (Money.of(amount).compareTo(billTotal) < 0) {
                throw new POSOperationException("Insufficient payment amount. Bill total: " + billTotal + ", Payment: LKR " + String.format("%.2f", amount));
            }

            var cash = new CashPayment();
            this.paymentReceipt = cash.pay(billTotal, Money.of(amount));
            active.setPayment(paymentReceipt);
        } catch (POSOperationException e) {
            throw e; // Re-throw our validation exceptions
        } catch (Exception e) {
            throw new POSOperationException("Cash payment processing failed: " + e.getMessage(), e);
        }
    }

    public void payCard(String last4) {
        ensure();
        ensureItemsAdded();

        // Payment validation
        validateCardNumber(last4);

        try {
            pricing.finalizePricing(active, activeDiscount);
            var card = new CardPayment(last4);
            this.paymentReceipt = card.pay(active.total(), active.total());
            active.setPayment(paymentReceipt);
        } catch (IllegalArgumentException e) {
            // Handle card processing errors (declined cards, invalid format, etc.)
            throw new POSOperationException("Card payment declined or invalid: " + e.getMessage(), e);
        } catch (Exception e) {
            // Handle any payment processing failures
            throw new POSOperationException("Card payment processing failed: " + e.getMessage(), e);
        }
    }

    public void checkoutCash(double amount) {
        ensure();
        ensureItemsAdded();

        // Payment validation
        validateCashAmount(amount);

        try {
            pricing.finalizePricing(active, activeDiscount);
            Money billTotal = active.total();

            // Validate sufficient payment amount
            if (Money.of(amount).compareTo(billTotal) < 0) {
                throw new POSOperationException("Insufficient payment amount. Bill total: " + billTotal + ", Payment: LKR " + String.format("%.2f", amount));
            }

            var cash = new CashPayment();
            this.paymentReceipt = cash.pay(billTotal, Money.of(amount));
            active.setPayment(paymentReceipt);
            persistAndReset();
        } catch (POSOperationException e) {
            throw e; // Re-throw our validation exceptions
        } catch (Exception e) {
            throw new POSOperationException("Cash checkout failed: " + e.getMessage(), e);
        }
    }

    public void checkoutCard(String last4) {
        ensure();
        ensureItemsAdded();

        // Payment validation
        validateCardNumber(last4);

        try {
            pricing.finalizePricing(active, activeDiscount);
            var card = new CardPayment(last4);
            this.paymentReceipt = card.pay(active.total(), active.total());
            active.setPayment(paymentReceipt);
            persistAndReset();
        } catch (IllegalArgumentException e) {
            // Handle card processing errors (declined cards, invalid format, etc.)
            throw new POSOperationException("Card payment declined or invalid: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Card checkout failed: " + e.getMessage(), e);
        }
    }

    public void checkout() {
        ensure();
        ensureItemsAdded();

        if (paymentReceipt == null) {
            throw new POSOperationException("Payment not completed. Please process payment before checkout");
        }

        try {
            pricing.finalizePricing(active, activeDiscount);
            persistAndReset();
        } catch (Exception e) {
            throw new POSOperationException("Checkout failed: " + e.getMessage(), e);
        }
    }

    private void persistAndReset() {
        try {
            active.setUserName(currentUser);
            active.setChannel(currentChannel);

            // Database operations with error handling
            try {
                bills.saveBill(active);
            } catch (Exception e) {
                throw new POSOperationException("Failed to save bill to database: " + e.getMessage(), e);
            }

            try {
                writer.write(active);
            } catch (Exception e) {
                throw new POSOperationException("Failed to write bill receipt: " + e.getMessage(), e);
            }

            // Inventory commitment operations with error handling
            try {
                if (!shelfReservations.isEmpty()) inventory.commitReservation(shelfReservations);
            } catch (Exception e) {
                throw new POSOperationException("Failed to commit shelf reservations: " + e.getMessage(), e);
            }

            try {
                if (!storeReservations.isEmpty()) inventory.commitStoreReservation(storeReservations);
            } catch (Exception e) {
                throw new POSOperationException("Failed to commit store reservations: " + e.getMessage(), e);
            }

            // Event publication with error handling
            try {
                events.publish(new BillPaid(active.number(), active.total(), currentChannel, currentUser));
            } catch (Exception e) {
                // Log the error but don't fail the checkout - bill is already saved
                System.err.println("Warning: Failed to publish BillPaid event: " + e.getMessage());
            }

            // Stock level events with error handling
            try {
                Set<String> codes = new LinkedHashSet<>();
                for (BillLine l : active.lines()) codes.add(l.itemCode());
                for (String code : codes) {
                    try {
                        int shelf = inventory.shelfQty(code);
                        int store = inventory.storeQty(code);
                        int totalLeft = shelf + store;
                        int threshold = Math.max(50, inventory.restockLevel(code));

                        if (totalLeft == 0) {
                            events.publish(new StockDepleted(code));
                        } else if (totalLeft <= threshold) {
                            events.publish(new RestockThresholdHit(code, totalLeft, threshold));
                        }
                    } catch (Exception e) {
                        // Log individual item stock check failures but continue with others
                        System.err.println("Warning: Failed to check stock levels for item " + code + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Log the error but don't fail the checkout
                System.err.println("Warning: Failed to process stock level events: " + e.getMessage());
            }

            // Reset state - this should always succeed
            active = null;
            shelfReservations.clear();
            storeReservations.clear();
            activeDiscount = null;
            paymentReceipt = null;

        } catch (POSOperationException e) {
            throw e; // Re-throw our specific exceptions
        } catch (Exception e) {
            throw new POSOperationException("Critical error during checkout process: " + e.getMessage(), e);
        }
    }

    private void ensure() {
        if (active == null) throw new IllegalStateException("No active bill");
    }

    private void ensureItemsAdded() {
        if (active == null || active.lines().isEmpty()) {
            throw new IllegalStateException("Cannot proceed: No items have been added to the bill");
        }
    }

    /**
     * Calculate the best (lowest) price from all reserved batches, considering any active discounts
     */
    private Money calculateBestPriceFromReservations(String itemCode, List<InventoryReservation> reservations) {
        // Validate input parameters
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new POSOperationException("Item code cannot be null or empty for price calculation");
        }

        if (reservations == null) {
            throw new POSOperationException("Reservations list cannot be null for price calculation");
        }

        if (reservations.isEmpty()) {
            System.out.println("⚠️ No reservations available for item " + itemCode + ", using base price");
        }

        Money basePrice;
        try {
            basePrice = inventory.priceOf(itemCode);
            if (basePrice == null) {
                throw new POSOperationException("Base price for item " + itemCode + " is null");
            }
        } catch (Exception e) {
            throw new POSOperationException("Failed to get base price for item " + itemCode + ": " + e.getMessage(), e);
        }

        Money bestPrice = basePrice;

        // Apply batch discounts only for in-store POS channel
        if (!"POS".equalsIgnoreCase(currentChannel)) {
            System.out.println("⚠️ Batch discounts only apply to POS channel. Current channel: " + currentChannel);
            return basePrice;
        }

        // If no inventory admin service available (backward compatibility), use base price
        if (inventoryAdmin == null) {
            System.out.println("⚠️ InventoryAdminService not available - no batch discounts");
            return basePrice;
        }

        // If no reservations, return base price
        if (reservations.isEmpty()) {
            return basePrice;
        }

        System.out.println("🔍 Checking batch discounts for item: " + itemCode + " (Base price: LKR " +
            String.format("%.2f", basePrice.asBigDecimal().doubleValue()) + ")");

        // Check each batch for discounts and find the best price
        boolean foundDiscount = false;
        Money maxSavingsPerItem = Money.ZERO;

        for (InventoryReservation reservation : reservations) {
            if (reservation == null) {
                System.err.println("Warning: Null reservation found for item " + itemCode + ", skipping");
                continue;
            }

            try {
                System.out.println("   Checking batch ID: " + reservation.batchId);
                Money batchPrice = inventoryAdmin.calculateDiscountedPrice(itemCode, reservation.batchId);

                if (batchPrice == null) {
                    System.err.println("Warning: Null price returned for batch " + reservation.batchId + ", using base price");
                    continue;
                }

                if (batchPrice.compareTo(bestPrice) < 0) {
                    Money savings = basePrice.minus(batchPrice);  // Calculate savings from original base price
                    if (savings.compareTo(maxSavingsPerItem) > 0) {
                        maxSavingsPerItem = savings;
                    }
                    bestPrice = batchPrice;
                    foundDiscount = true;
                    System.out.println("   ✅ Discounted price found: LKR " +
                        String.format("%.2f", batchPrice.asBigDecimal().doubleValue()) +
                        " (Save: LKR " + String.format("%.2f", savings.asBigDecimal().doubleValue()) + " per item)");
                } else {
                    System.out.println("   ❌ No discount for batch " + reservation.batchId +
                        " (Price: LKR " + String.format("%.2f", batchPrice.asBigDecimal().doubleValue()) + ")");
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to calculate discount for batch " + reservation.batchId +
                    " for item " + itemCode + ": " + e.getMessage());
                // Continue with other reservations instead of failing completely
                continue;
            }
        }

        // Only show discount info if found, but don't duplicate the message that will be shown later
        if (!foundDiscount) {
            System.out.println("ℹ️ No batch discounts available for " + itemCode);
        }

        return bestPrice;
    }

    /**
     * Gets information about the currently applied discount
     */
    public String getCurrentDiscountInfo() {
        if (active == null || activeDiscount == null) {
            return "No discount applied";
        }

        // For batch discounts, calculate the actual discount amount
        if ("BATCH_DISCOUNT".equals(activeDiscount.code())) {
            Money actualDiscount = calculateActualBatchDiscountAmount();
            return "Current Discount: BATCH_DISCOUNTS (Saves: " + actualDiscount + ")";
        }

        // For other discount types, use the computed discount
        Money discountAmount = activeDiscount.computeDiscount(active);
        return "Current Discount: " + activeDiscount.code() + " (Saves: " + discountAmount + ")";
    }

    /**
     * Gets a list of all batch discounts for the current bill
     */
    public List<String> getAvailableDiscounts() {
        if (active == null) {
            return List.of("No active bill");
        }

        List<String> discountDescriptions = new ArrayList<>();

        for (BillLine line : active.lines()) {
            for (InventoryReservation reservation : line.reservations()) {
                Optional<BatchDiscount> discount = inventoryAdmin.findActiveBatchDiscount(reservation.batchId);
                if (discount.isPresent() && discount.get().isValidNow()) {
                    String description = String.format("%s: %s",
                        line.itemName(),
                        discount.get().getDescription());
                    if (!discountDescriptions.contains(description)) {
                        discountDescriptions.add(description);
                    }
                }
            }
        }

        if (discountDescriptions.isEmpty()) {
            return List.of("No batch discounts available");
        }

        return discountDescriptions;
    }

    /**
     * Gets the current bill with pricing information including discounts
     */
    public String getCurrentBillSummary() {
        if (active == null) {
            return "No active bill";
        }

        // Apply current pricing to get up-to-date totals
        pricing.finalizePricing(active, activeDiscount);

        StringBuilder summary = new StringBuilder();
        summary.append("=== CURRENT BILL ===\n");
        summary.append("Bill No: ").append(active.number()).append("\n");
        summary.append("Items:\n");

        for (BillLine line : active.lines()) {
            summary.append("  ").append(line.itemCode()).append(" - ")
                   .append(line.itemName()).append(" x").append(line.quantity())
                   .append(" @ ").append(line.unitPrice()).append(" = ")
                   .append(line.lineTotal()).append("\n");
        }

        summary.append("-------------------\n");
        summary.append("Subtotal: ").append(active.subtotal()).append("\n");
        summary.append("Discount: -").append(active.discount()).append("\n");
        summary.append("Tax: ").append(active.tax()).append("\n");
        summary.append("TOTAL: ").append(active.total()).append("\n");

        if (activeDiscount != null) {
            summary.append("Applied Discount: ").append(activeDiscount.code()).append("\n");
        }

        List<String> availableDiscounts = getAvailableDiscounts();
        if (!availableDiscounts.isEmpty()) {
            summary.append("Available Discounts:\n");
            for (String discount : availableDiscounts) {
                summary.append("  • ").append(discount).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * Validates item code input
     */
    private void validateItemCode(String code) {
        if (code == null) {
            throw new POSOperationException("Item code cannot be null");
        }
        if (code.trim().isEmpty()) {
            throw new POSOperationException("Item code cannot be empty");
        }
        // Additional validation: check for valid format (alphanumeric with possible special characters)
        if (!code.matches("^[A-Za-z0-9_-]+$")) {
            throw new POSOperationException("Item code contains invalid characters. Only letters, numbers, underscore and hyphen are allowed");
        }
    }

    /**
     * Validates quantity input
     */
    private void validateQuantity(int qty) {
        if (qty <= 0) {
            throw new POSOperationException("Quantity must be greater than zero. Provided: " + qty);
        }
        if (qty > 10000) {
            throw new POSOperationException("Quantity too large. Maximum allowed: 10000. Provided: " + qty);
        }
    }

    /**
     * Validates boolean parameters for addItemSmart method
     */
    private void validateBooleanParameters(boolean approveUseOtherSide, boolean managerApprovedBackfill) {
        // Boolean parameters are inherently valid, but we can add business logic validation
        // For example, manager approval might require certain conditions
        if (managerApprovedBackfill && !approveUseOtherSide) {
            throw new POSOperationException("Manager approved backfill requires approval to use other side");
        }
    }

    /**
     * Validates if an item exists in the inventory
     */
    private void validateItemExists(String code) {
        try {
            // Try to get item name - this will throw NoSuchElementException if item doesn't exist
            inventory.itemName(code);
        } catch (NoSuchElementException e) {
            throw new POSOperationException("Item " + code + " does not exist in inventory");
        }
    }

    /**
     * Validates if there is enough stock available for the requested quantity
     */
    private void validateStockAvailability(String code, int qty) {
        int availableStock;
        if ("POS".equalsIgnoreCase(currentChannel)) {
            // For POS channel, check store quantity
            availableStock = inventory.storeQty(code);
        } else {
            // For other channels (like ONLINE), check shelf quantity
            availableStock = inventory.shelfQty(code);
        }

        if (availableStock < qty) {
            throw new POSOperationException("Not enough stock available for item " + code + ". Requested: " + qty + ", Available: " + availableStock);
        }
    }

    /**
     * Validates the cash payment amount
     */
    private void validateCashAmount(double amount) {
        if (amount <= 0) {
            throw new POSOperationException("Cash amount must be greater than zero. Provided: " + amount);
        }
        if (amount > 100000) {
            throw new POSOperationException("Cash amount too large. Maximum allowed: 100000. Provided: " + amount);
        }
    }

    /**
     * Validates the card number format (last 4 digits)
     */
    private void validateCardNumber(String last4) {
        if (last4 == null) {
            throw new POSOperationException("Card number cannot be null");
        }
        if (last4.trim().isEmpty()) {
            throw new POSOperationException("Card number cannot be empty");
        }
        // Additional validation: check for valid format (4 digits)
        if (!last4.matches("^\\d{4}$")) {
            throw new POSOperationException("Card number must be 4 digits");
        }
    }

    /**
     * Custom exception for POS operations
     */
    public static class POSOperationException extends RuntimeException {
        public POSOperationException(String message) {
            super(message);
        }

        public POSOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
