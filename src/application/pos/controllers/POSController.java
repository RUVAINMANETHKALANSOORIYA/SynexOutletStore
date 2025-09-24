package application.pos.controllers;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.billing.composite.BillComponent;
import domain.billing.state.BillState;
import domain.common.Money;
import domain.inventory.InventoryReservation;
import ports.in.InventoryService;
import application.inventory.InventoryAdminService;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import application.pricing.AutoDiscountService;
import ports.out.BillRepository;
import domain.billing.BillWriter;
import application.events.EventBus;
import application.events.NoopEventBus;
import application.pos.patterns.command.*;

import java.util.ArrayList;
import java.util.List;

public final class POSController {
    // Component managers - each handles a specific responsibility
    private final BillManager billManager;
    private final DiscountManager discountManager;
    private final BillStateManager stateManager;
    private final BillCompositeBuilder compositeBuilder;
    private final CommandInvoker commandInvoker;

    // Existing specialized components
    private final PaymentProcessor paymentProcessor;
    private final InventoryManager inventoryManager;
    private final CheckoutService checkoutService;
    private final PricingService pricing;

    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, null, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, invAdmin, pr, gen, br, bw, new NoopEventBus());
    }

    public POSController(InventoryService inv, InventoryAdminService invAdmin, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw, EventBus events) {
        // Initialize component managers
        this.billManager = new BillManager(gen);
        this.inventoryManager = new InventoryManager(inv, invAdmin);
        this.discountManager = new DiscountManager(pr, new AutoDiscountService(invAdmin), inventoryManager);
        this.stateManager = new BillStateManager();
        this.compositeBuilder = new BillCompositeBuilder();
        this.commandInvoker = new CommandInvoker();

        // Initialize existing specialized components
        this.paymentProcessor = new PaymentProcessor(pr);
        this.checkoutService = new CheckoutService(br, bw, (events == null) ? new NoopEventBus() : events, inventoryManager);
        this.pricing = pr;
    }

    // User and channel management - delegate to BillManager
    public void setUser(String user) {
        billManager.setUser(user);
    }

    public void setChannel(String channel) {
        billManager.setChannel(channel);
    }

    // Bill lifecycle methods - delegate to BillManager
    public void newBill() {
        billManager.createNewBill();
        discountManager.resetDiscount();
    }

    public void addItem(String code, int qty) {
        ensureActiveBill();

        try {
            // Use inventory manager for reservation
            var res = inventoryManager.reserveItems(code, qty, billManager.getCurrentChannel());
            billManager.addReservations(res, "POS".equalsIgnoreCase(billManager.getCurrentChannel()));

            // Calculate the best price using inventory manager
            Money effectivePrice = inventoryManager.calculateBestPrice(code, res, billManager.getCurrentChannel());

            // Create and add line to bill
            var line = new BillLine(code, inventoryManager.getItemName(code), effectivePrice, qty, res);
            billManager.addLine(line);

            // Auto-apply discount
            discountManager.autoApplyBestDiscount(billManager.getActiveBill());
        } catch (Exception e) {
            throw new POSOperationException("Failed to add item " + code + " to bill: " + e.getMessage(), e);
        }
    }

    public InventoryService.SmartPick addItemSmart(String code, int qty,
                                                   boolean approveUseOtherSide,
                                                   boolean managerApprovedBackfill) {
        ensureActiveBill();

        try {
            // Use inventory manager for smart reservation
            var pick = inventoryManager.reserveItemsSmart(code, qty, billManager.getCurrentChannel(),
                                                         approveUseOtherSide, managerApprovedBackfill);

            billManager.addReservations(pick.shelfReservations, false);
            billManager.addReservations(pick.storeReservations, true);

            var combined = new ArrayList<InventoryReservation>();
            combined.addAll(pick.shelfReservations);
            combined.addAll(pick.storeReservations);

            // Calculate the best price using inventory manager
            Money effectivePrice = inventoryManager.calculateBestPrice(code, combined, billManager.getCurrentChannel());

            // Create and add line to bill
            var line = new BillLine(code, inventoryManager.getItemName(code), effectivePrice, qty, combined);
            billManager.addLine(line);

            // Auto-apply discount
            discountManager.autoApplyBestDiscount(billManager.getActiveBill());

            return pick;
        } catch (Exception e) {
            throw new POSOperationException("Failed to add item " + code + " using smart pick: " + e.getMessage(), e);
        }
    }

    public void removeItem(String code) {
        ensureActiveBill();
        billManager.removeLineByCode(code);
        discountManager.autoApplyBestDiscount(billManager.getActiveBill());
    }

    public void removeItem(String code, int qty) {
        // For Command pattern compatibility
        removeItem(code);
    }

    // Discount methods - delegate to DiscountManager
    public void applyDiscount(DiscountPolicy p) {
        ensureActiveBill();
        discountManager.applyDiscount(billManager.getActiveBill(), p);
    }

    public String getCurrentDiscountInfo() {
        return discountManager.getCurrentDiscountInfo(billManager.getActiveBill());
    }

    public List<String> getAvailableDiscounts() {
        return discountManager.getAvailableDiscounts(billManager.getActiveBill());
    }

    // Pricing methods
    public Money total() {
        ensureActiveBill();
        ensureItemsAdded();
        pricing.finalizePricing(billManager.getActiveBill(), discountManager.getActiveDiscount());
        return billManager.getActiveBill().total();
    }

    // Payment methods - delegate to PaymentProcessor
    public void payCash(double amount) {
        ensureActiveBill();
        ensureItemsAdded();

        try {
            var receipt = paymentProcessor.processCashPayment(billManager.getActiveBill(), amount, discountManager.getActiveDiscount());
            billManager.setPaymentReceipt(receipt);
        } catch (Exception e) {
            throw new POSOperationException("Cash payment processing failed: " + e.getMessage(), e);
        }
    }

    public void payCard(String last4) {
        ensureActiveBill();
        ensureItemsAdded();

        try {
            var receipt = paymentProcessor.processCardPayment(billManager.getActiveBill(), last4, discountManager.getActiveDiscount());
            billManager.setPaymentReceipt(receipt);
        } catch (Exception e) {
            throw new POSOperationException("Card payment processing failed: " + e.getMessage(), e);
        }
    }

    // Checkout methods
    public void checkoutCash(double amount) {
        ensureActiveBill();
        ensureItemsAdded();

        try {
            var receipt = paymentProcessor.processCashPayment(billManager.getActiveBill(), amount, discountManager.getActiveDiscount());
            billManager.setPaymentReceipt(receipt);
            completeCheckout();
        } catch (Exception e) {
            throw new POSOperationException("Cash checkout failed: " + e.getMessage(), e);
        }
    }

    public void checkoutCard(String last4) {
        ensureActiveBill();
        ensureItemsAdded();

        try {
            var receipt = paymentProcessor.processCardPayment(billManager.getActiveBill(), last4, discountManager.getActiveDiscount());
            billManager.setPaymentReceipt(receipt);
            completeCheckout();
        } catch (Exception e) {
            throw new POSOperationException("Card checkout failed: " + e.getMessage(), e);
        }
    }

    public void checkout() {
        ensureActiveBill();
        ensureItemsAdded();

        if (billManager.getPaymentReceipt() == null) {
            throw new POSOperationException("Payment not completed. Please process payment before checkout");
        }

        try {
            pricing.finalizePricing(billManager.getActiveBill(), discountManager.getActiveDiscount());
            completeCheckout();
        } catch (Exception e) {
            throw new POSOperationException("Checkout failed: " + e.getMessage(), e);
        }
    }

    private void completeCheckout() {
        checkoutService.completeCheckout(
            billManager.getActiveBill(),
            billManager.getShelfReservations(),
            billManager.getStoreReservations(),
            billManager.getCurrentUser(),
            billManager.getCurrentChannel()
        );

        // Reset state
        billManager.resetBill();
        discountManager.resetDiscount();
    }

    // State Pattern methods - delegate to BillStateManager
    public void changeState(BillState newState) {
        stateManager.changeState(newState);
    }

    public String getCurrentStateName() {
        return stateManager.getCurrentStateName();
    }

    // Command Pattern methods - delegate to CommandInvoker
    public void executeCommand(Command command) {
        commandInvoker.execute(command);
    }

    public void doAddItem(String code, int qty) {
        Command addCommand = new AddItemCommand(this, code, qty);
        executeCommand(addCommand);
    }

    public boolean undoLastAction() {
        return commandInvoker.undo();
    }

    public boolean redoLastAction() {
        return commandInvoker.redo();
    }

    public List<String> getActionHistory() {
        return commandInvoker.getHistory();
    }

    // Internal methods for state and command pattern delegation
    public void addItemInternal(String code, int qty) {
        this.addItem(code, qty);
    }

    public void removeItemInternal(String code, int qty) {
        this.removeItem(code, qty);
    }

    public void processPaymentInternal(String method, Object... params) {
        if ("CASH".equals(method) && params.length > 0) {
            this.payCash((Double) params[0]);
        } else if ("CARD".equals(method) && params.length > 0) {
            this.payCard((String) params[0]);
        }
    }

    public void finalizeBillInternal() {
        this.checkout();
    }

    public Bill getActiveBill() {
        return billManager.getActiveBill();
    }

    // Composite Pattern methods - delegate to BillCompositeBuilder
    public BillComponent getBillAsComposite() {
        return compositeBuilder.buildComposite(billManager.getActiveBill());
    }

    // Summary method - now uses component managers
    public String getCurrentBillSummary() {
        if (!billManager.hasActiveBill()) {
            return "No active bill";
        }

        Bill bill = billManager.getActiveBill();
        pricing.finalizePricing(bill, discountManager.getActiveDiscount());

        StringBuilder summary = new StringBuilder();
        summary.append("=== CURRENT BILL ===\n");
        summary.append("Bill No: ").append(bill.number()).append("\n");
        summary.append("Items:\n");

        for (BillLine line : bill.lines()) {
            summary.append("  ").append(line.itemCode()).append(" - ")
                   .append(line.itemName()).append(" x").append(line.quantity())
                   .append(" @ ").append(line.unitPrice()).append(" = ")
                   .append(line.lineTotal()).append("\n");
        }

        summary.append("-------------------\n");
        summary.append("Subtotal: ").append(bill.subtotal()).append("\n");
        summary.append("Discount: -").append(bill.discount()).append("\n");
        summary.append("Tax: ").append(bill.tax()).append("\n");
        summary.append("TOTAL: ").append(bill.total()).append("\n");

        if (discountManager.getActiveDiscount() != null) {
            summary.append("Applied Discount: ").append(discountManager.getActiveDiscount().code()).append("\n");
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

    // Helper methods
    private void ensureActiveBill() {
        if (!billManager.hasActiveBill()) {
            throw new IllegalStateException("No active bill");
        }
    }

    private void ensureItemsAdded() {
        if (!billManager.hasItems()) {
            throw new IllegalStateException("Cannot proceed: No items have been added to the bill");
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
