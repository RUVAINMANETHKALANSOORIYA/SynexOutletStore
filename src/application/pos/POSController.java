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
    }

    public InventoryService.SmartPick addItemSmart(String code, int qty,
                                                   boolean approveUseOtherSide,
                                                   boolean managerApprovedBackfill) {
        ensure();
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
        pricing.finalizePricing(active, activeDiscount);
        var cash = new CashPayment();
        this.paymentReceipt = cash.pay(active.total(), Money.of(amount));
        active.setPayment(paymentReceipt);
    }

    public void payCard(String last4) {
        ensure();
        pricing.finalizePricing(active, activeDiscount);
        var card = new CardPayment(last4);
        this.paymentReceipt = card.pay(active.total(), active.total());
        active.setPayment(paymentReceipt);
    }

    public void checkoutCash(double amount) {
        ensure();
        ensureItemsAdded();
        pricing.finalizePricing(active, activeDiscount);
        var cash = new CashPayment();
        this.paymentReceipt = cash.pay(active.total(), Money.of(amount));
        active.setPayment(paymentReceipt);
        persistAndReset();
    }

    public void checkoutCard(String last4) {
        ensure();
        ensureItemsAdded();
        pricing.finalizePricing(active, activeDiscount);
        var card = new CardPayment(last4);
        this.paymentReceipt = card.pay(active.total(), active.total());
        active.setPayment(paymentReceipt);
        persistAndReset();
    }

    public void checkout() {
        ensure();
        ensureItemsAdded();
        pricing.finalizePricing(active, activeDiscount);
        if (paymentReceipt == null) throw new IllegalStateException("Payment not completed");
        persistAndReset();
    }

    private void persistAndReset() {
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        bills.saveBill(active);
        writer.write(active);

        if (!shelfReservations.isEmpty()) inventory.commitReservation(shelfReservations);
        if (!storeReservations.isEmpty()) inventory.commitStoreReservation(storeReservations);

        events.publish(new BillPaid(active.number(), active.total(), currentChannel, currentUser));

        // dedupe item codes in this bill
        Set<String> codes = new LinkedHashSet<>();
        for (BillLine l : active.lines()) codes.add(l.itemCode());
        for (String code : codes) {
            int shelf = inventory.shelfQty(code);
            int store = inventory.storeQty(code);
            int totalLeft = shelf + store;
            int threshold = Math.max(50, inventory.restockLevel(code));
            if (totalLeft == 0) {
                events.publish(new StockDepleted(code));
            } else if (totalLeft <= threshold) {
                events.publish(new RestockThresholdHit(code, totalLeft, threshold));
            }
        }

        active = null;
        shelfReservations.clear();
        storeReservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
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
        Money basePrice = inventory.priceOf(itemCode);
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

        System.out.println("🔍 Checking batch discounts for item: " + itemCode + " (Base price: LKR " +
            String.format("%.2f", basePrice.asBigDecimal().doubleValue()) + ")");

        // Check each batch for discounts and find the best price
        boolean foundDiscount = false;
        Money maxSavingsPerItem = Money.ZERO;

        for (InventoryReservation reservation : reservations) {
            System.out.println("   Checking batch ID: " + reservation.batchId);
            Money batchPrice = inventoryAdmin.calculateDiscountedPrice(itemCode, reservation.batchId);

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
}
