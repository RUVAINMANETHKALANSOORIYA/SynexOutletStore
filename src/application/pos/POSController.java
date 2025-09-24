package application.pos;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.common.Money;
import domain.inventory.InventoryReservation;
import ports.in.InventoryService;
import application.inventory.InventoryAdminService;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import application.pricing.AutoDiscountService;
import ports.out.BillRepository;
import domain.billing.BillWriter;
import application.events.EventBus;
import application.events.NoopEventBus;

import java.util.ArrayList;
import java.util.List;

public final class POSController {
    // Specialized components
    private final PaymentProcessor paymentProcessor;
    private final InventoryManager inventoryManager;
    private final CheckoutService checkoutService;

    // Core services
    private final PricingService pricing;
    private final AutoDiscountService autoDiscountService;
    private final BillNumberGenerator billNos;

    // Bill state
    private Bill active = null;
    private DiscountPolicy activeDiscount = null;
    private final List<InventoryReservation> shelfReservations = new ArrayList<>();
    private final List<InventoryReservation> storeReservations = new ArrayList<>();
    private Payment.Receipt paymentReceipt = null;

    // User context
    private String currentUser = "operator";
    private String currentChannel = "POS";

    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, null, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, invAdmin, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw, EventBus events) {
        // Initialize specialized components
        this.paymentProcessor = new PaymentProcessor(pr);
        this.inventoryManager = new InventoryManager(inv, invAdmin);
        this.checkoutService = new CheckoutService(br, bw, (events == null) ? new NoopEventBus() : events, inventoryManager);

        // Core services
        this.pricing = pr;
        this.autoDiscountService = new AutoDiscountService(invAdmin);
        this.billNos = gen;
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

        try {
            // Use inventory manager for reservation
            var res = inventoryManager.reserveItems(code, qty, currentChannel);
            if ("POS".equalsIgnoreCase(currentChannel)) {
                storeReservations.addAll(res);
            } else {
                shelfReservations.addAll(res);
            }

            // Calculate the best price using inventory manager
            Money effectivePrice = inventoryManager.calculateBestPrice(code, res, currentChannel);

            var line = new BillLine(code, inventoryManager.getItemName(code), effectivePrice, qty, res);
            active.addLine(line);

            // Auto-detect and apply the best available discount
            autoApplyBestDiscount();
        } catch (Exception e) {
            throw new POSOperationException("Failed to add item " + code + " to bill: " + e.getMessage(), e);
        }
    }

    public InventoryService.SmartPick addItemSmart(String code, int qty,
                                                   boolean approveUseOtherSide,
                                                   boolean managerApprovedBackfill) {
        ensure();

        try {
            // Use inventory manager for smart reservation
            var pick = inventoryManager.reserveItemsSmart(code, qty, currentChannel, approveUseOtherSide, managerApprovedBackfill);

            shelfReservations.addAll(pick.shelfReservations);
            storeReservations.addAll(pick.storeReservations);

            var combined = new ArrayList<InventoryReservation>(pick.shelfReservations.size() + pick.storeReservations.size());
            combined.addAll(pick.shelfReservations);
            combined.addAll(pick.storeReservations);

            // Calculate the best price using inventory manager
            Money effectivePrice = inventoryManager.calculateBestPrice(code, combined, currentChannel);

            var line = new BillLine(code, inventoryManager.getItemName(code), effectivePrice, qty, combined);
            active.addLine(line);

            autoApplyBestDiscount();
            return pick;
        } catch (Exception e) {
            throw new POSOperationException("Failed to add item " + code + " using smart pick: " + e.getMessage(), e);
        }
    }

    private void autoApplyBestDiscount() {
        if (active == null || active.lines().isEmpty()) {
            return;
        }

        DiscountPolicy batchDiscount = autoDiscountService.detectBatchDiscounts(active);

        if (batchDiscount != null) {
            activeDiscount = batchDiscount;

            BillLine lastLine = active.lines().get(active.lines().size() - 1);
            Money originalLineTotal = inventoryManager.getItemPrice(lastLine.itemCode()).multiply(lastLine.quantity());
            Money discountedLineTotal = lastLine.lineTotal();
            Money lineSavings = originalLineTotal.minus(discountedLineTotal);

            if (lineSavings.compareTo(Money.ZERO) > 0) {
                System.out.println("🎉 Batch Discount Applied - Total savings for " + lastLine.quantity() +
                    " items: LKR " + String.format("%.2f", lineSavings.asBigDecimal().doubleValue()));
            }
        }
    }

    public void removeItem(String code) {
        ensure();
        active.removeLineByCode(code);
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

        try {
            this.paymentReceipt = paymentProcessor.processCashPayment(active, amount, activeDiscount);
            active.setPayment(paymentReceipt);
        } catch (Exception e) {
            throw new POSOperationException("Cash payment processing failed: " + e.getMessage(), e);
        }
    }

    public void payCard(String last4) {
        ensure();
        ensureItemsAdded();

        try {
            this.paymentReceipt = paymentProcessor.processCardPayment(active, last4, activeDiscount);
            active.setPayment(paymentReceipt);
        } catch (Exception e) {
            throw new POSOperationException("Card payment processing failed: " + e.getMessage(), e);
        }
    }

    public void checkoutCash(double amount) {
        ensure();
        ensureItemsAdded();

        try {
            this.paymentReceipt = paymentProcessor.processCashPayment(active, amount, activeDiscount);
            active.setPayment(paymentReceipt);
            completeCheckout();
        } catch (Exception e) {
            throw new POSOperationException("Cash checkout failed: " + e.getMessage(), e);
        }
    }

    public void checkoutCard(String last4) {
        ensure();
        ensureItemsAdded();

        try {
            this.paymentReceipt = paymentProcessor.processCardPayment(active, last4, activeDiscount);
            active.setPayment(paymentReceipt);
            completeCheckout();
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
            completeCheckout();
        } catch (Exception e) {
            throw new POSOperationException("Checkout failed: " + e.getMessage(), e);
        }
    }

    private void completeCheckout() {
        checkoutService.completeCheckout(active, shelfReservations, storeReservations, currentUser, currentChannel);

        // Reset state
        active = null;
        shelfReservations.clear();
        storeReservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
    }

    // Helper methods
    private void ensure() {
        if (active == null) throw new IllegalStateException("No active bill");
    }

    private void ensureItemsAdded() {
        if (active == null || active.lines().isEmpty()) {
            throw new IllegalStateException("Cannot proceed: No items have been added to the bill");
        }
    }

    // Discount and summary methods using inventory manager
    public String getCurrentDiscountInfo() {
        if (active == null || activeDiscount == null) {
            return "No discount applied";
        }

        if ("BATCH_DISCOUNT".equals(activeDiscount.code())) {
            Money actualDiscount = calculateActualBatchDiscountAmount();
            return "Current Discount: BATCH_DISCOUNTS (Saves: " + actualDiscount + ")";
        }

        Money discountAmount = activeDiscount.computeDiscount(active);
        return "Current Discount: " + activeDiscount.code() + " (Saves: " + discountAmount + ")";
    }

    public List<String> getAvailableDiscounts() {
        if (active == null) {
            return List.of("No active bill");
        }

        return inventoryManager.getAvailableDiscounts(active.lines());
    }

    public String getCurrentBillSummary() {
        if (active == null) {
            return "No active bill";
        }

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

    private Money calculateActualBatchDiscountAmount() {
        Money totalOriginalPrice = Money.ZERO;
        Money totalDiscountedPrice = Money.ZERO;

        for (BillLine line : active.lines()) {
            Money originalPrice = inventoryManager.getItemPrice(line.itemCode());
            totalOriginalPrice = totalOriginalPrice.plus(originalPrice.multiply(line.quantity()));
            totalDiscountedPrice = totalDiscountedPrice.plus(line.lineTotal());
        }

        return totalOriginalPrice.minus(totalDiscountedPrice);
    }

//    // Method to support State pattern - allows state objects to change controller state
//    public void changeState(application.pos.patterns.BillState newState) {
//        // This method would be called by state objects but for now we'll keep it simple
//        // In a full implementation, this would update an internal state field
//    }
//
//    // Method to support Command pattern - allows commands to access controller operations
//    public void doAddItem(String code, int qty) {
//        // This wraps the existing addItem functionality for command pattern access
//        this.addItem(code, qty);
//    }

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
