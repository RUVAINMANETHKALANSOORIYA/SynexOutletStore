package application.pos;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.billing.BillWriter;
import domain.common.Money;
import domain.inventory.InventoryReservation;
import application.inventory.InventoryService;
import domain.payment.CashPayment;
import domain.payment.CardPayment;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import ports.out.BillRepository;

import application.events.EventBus;
import application.events.NoopEventBus;
import application.events.events.BillPaid;
import application.events.events.RestockThresholdHit;
import application.events.events.StockDepleted;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class POSController {
    private final InventoryService inventory;
    private final PricingService pricing;
    private final BillNumberGenerator billNos;
    private final BillRepository bills;
    private final BillWriter writer;
    private final EventBus events; // Observer

    private Bill active = null;
    private DiscountPolicy activeDiscount = null;

    // track reservations by area
    private final List<InventoryReservation> shelfReservations = new ArrayList<>();
    private final List<InventoryReservation> storeReservations = new ArrayList<>();

    private Payment.Receipt paymentReceipt = null;

    private String currentUser = "operator";
    private String currentChannel = "POS";

    // Backward-compatible constructor (no events)
    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this(inv, pr, gen, br, bw, new NoopEventBus());
    }

    // New constructor with EventBus
    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw, EventBus events) {
        this.inventory = inv;
        this.pricing = pr;
        this.billNos = gen;
        this.bills = br;
        this.writer = bw;
        this.events = (events == null) ? new NoopEventBus() : events;
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
        if (active != null) throw new IllegalStateException("Already active");
        active = new Bill(billNos.next());
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        shelfReservations.clear();
        storeReservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
    }

    /** Legacy addItem (kept). */
    public void addItem(String code, int qty) {
        ensure();
        var res = inventory.reserveByChannel(code, qty, currentChannel);
        if ("POS".equalsIgnoreCase(currentChannel)) {
            storeReservations.addAll(res);
        } else {
            shelfReservations.addAll(res);
        }
        var line = new BillLine(code, inventory.itemName(code), inventory.priceOf(code), qty, res);
        active.addLine(line);
    }

    /** Smart add (kept). */
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

        var line = new BillLine(code, inventory.itemName(code), inventory.priceOf(code), qty, combined);
        active.addLine(line);

        return pick;
    }

    public void removeItem(String code) {
        ensure();
        active.removeLineByCode(code);
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

        bills.save(active);
        writer.write(active);

        // Commit by area
        if (!shelfReservations.isEmpty()) inventory.commitReservation(shelfReservations);
        if (!storeReservations.isEmpty()) inventory.commitStoreReservation(storeReservations);

        // ===== Observer: publish bill + stock events =====
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
}
