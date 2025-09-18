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

import java.util.ArrayList;
import java.util.List;

public final class POSController {
    private final InventoryService inventory;
    private final PricingService pricing;
    private final BillNumberGenerator billNos;
    private final BillRepository bills;
    private final BillWriter writer;

    private Bill active = null;
    private DiscountPolicy activeDiscount = null;
    private final List<InventoryReservation> reservations = new ArrayList<>();
    private Payment.Receipt paymentReceipt = null;

    // ---- Step 8: User & Channel (defaults) ----
    private String currentUser = "operator";
    private String currentChannel = "POS";

    public POSController(InventoryService inv, PricingService pr, BillNumberGenerator gen, BillRepository br, BillWriter bw) {
        this.inventory = inv;
        this.pricing = pr;
        this.billNos = gen;
        this.bills = br;
        this.writer = bw;
    }

    // allow UI/CLI to set user & channel
    public void setUser(String user) {
        this.currentUser = (user == null || user.isBlank()) ? "operator" : user.trim();
        // if a bill is already active, reflect change immediately
        if (active != null) active.setUserName(this.currentUser);
    }
    public void setChannel(String channel) {
        this.currentChannel = (channel == null || channel.isBlank()) ? "POS" : channel.trim().toUpperCase();
        if (active != null) active.setChannel(this.currentChannel);
    }

    public void newBill() {
        if (active != null) throw new IllegalStateException("Already active");
        active = new Bill(billNos.next());
        // attach current meta to this bill
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        reservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
    }

    public void addItem(String code, int qty) {
        ensure();
        var res = inventory.reserveByChannel(code, qty, currentChannel); // ✅ channel-aware
        reservations.addAll(res);
        var line = new BillLine(code, inventory.itemName(code), inventory.priceOf(code), qty, res);
        active.addLine(line);
    }

    public void removeItem(String code) {
        ensure();
        active.removeLineByCode(code);
        // (optional) implement reservation release if you want to return stock
    }

    public void applyDiscount(DiscountPolicy p) {
        ensure();
        activeDiscount = p;
    }

    public Money total() {
        ensure();
        pricing.finalizePricing(active, activeDiscount);
        return active.total();
    }

    // -------- Optional pre-domain.payment APIs (still supported) --------
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

    // -------- New: checkout & pay in a single call --------
    public void checkoutCash(double amount) {
        ensure();
        pricing.finalizePricing(active, activeDiscount);
        var cash = new CashPayment();
        this.paymentReceipt = cash.pay(active.total(), Money.of(amount));
        active.setPayment(paymentReceipt);
        persistAndReset();
    }

    public void checkoutCard(String last4) {
        ensure();
        pricing.finalizePricing(active, activeDiscount);
        var card = new CardPayment(last4);
        this.paymentReceipt = card.pay(active.total(), active.total());
        active.setPayment(paymentReceipt);
        persistAndReset();
    }

    // -------- Legacy: checkout only (requires pre-domain.payment) --------
    public void checkout() {
        ensure();
        pricing.finalizePricing(active, activeDiscount);
        if (paymentReceipt == null) throw new IllegalStateException("Payment not completed");
        persistAndReset();
    }

    private void persistAndReset() {
        active.setUserName(currentUser);
        active.setChannel(currentChannel);

        bills.save(active);
        writer.write(active);
        inventory.commitReservationByChannel(reservations, currentChannel); // ✅ channel-aware commit
        active = null;
        reservations.clear();
        activeDiscount = null;
        paymentReceipt = null;
    }

    private void ensure() {
        if (active == null) throw new IllegalStateException("No active bill");
    }
}
