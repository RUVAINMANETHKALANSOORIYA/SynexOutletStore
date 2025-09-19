package domain.billing;

import domain.common.Money;
import domain.payment.Payment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Bill {
    private final String number;
    private final LocalDateTime createdAt = LocalDateTime.now();
    private final List<BillLine> lines = new ArrayList<>();

    // totals
    private Money subtotal = Money.ZERO, discount = Money.ZERO, tax = Money.ZERO, total = Money.ZERO;

    // payment & meta
    private String paymentMethod;          // "CASH" | "CARD" | null (unpaid)
    private Money paidAmount = Money.ZERO; // amount tendered
    private Money changeAmount = Money.ZERO;
    private String cardLast4;              // last 4 digits if paid by card
    private String channel;                // e.g., "POS", "ONLINE"
    private String userName;               // cashier/operator

    // ====== State pattern ======
    private BillState state = new Draft();

    public Bill(String number){ this.number = number; }

    // identity & time
    public String number(){ return number; }
    public LocalDateTime createdAt(){ return createdAt; }

    // lines (state-guarded)
    public List<BillLine> lines(){ return new ArrayList<>(lines); }

    public void addLine(BillLine l){ state.onAddLine(this, l); }
    void addLineInternal(BillLine l){ lines.add(l); } // used by states

    public void removeLineByCode(String code){ state.onRemoveLineByCode(this, code); }
    void removeLineInternal(String code){ lines.removeIf(l -> l.itemCode().equals(code)); }

    // pricing
    public Money computeSubtotal(){
        Money s = Money.ZERO;
        for (var l: lines) s = s.plus(l.lineTotal());
        return s;
    }
    public void setPricing(Money sub, Money dis, Money tx, Money tot){
        subtotal=sub; discount=dis; tax=tx; total=tot;
    }
    public Money subtotal(){ return subtotal; }
    public Money discount(){ return discount; }
    public Money tax(){ return tax; }
    public Money total(){ return total; }

    // payment (state-guarded)
    public void setPayment(Payment.Receipt r){ state.onSetPayment(this, r); }
    void applyPaymentFields(Payment.Receipt r){
        if (r == null) throw new IllegalArgumentException("Payment receipt required");
        this.paymentMethod = r.method();
        this.paidAmount = r.paid() == null ? Money.ZERO : r.paid();
        this.changeAmount = r.change() == null ? Money.ZERO : r.change();
        this.cardLast4 = r.cardLast4();
    }

    public String paymentMethod(){ return paymentMethod; }
    public Money paidAmount(){ return paidAmount; }
    public Money changeAmount(){ return changeAmount; }
    public String cardLast4(){ return cardLast4; }

    // meta
    public void setChannel(String channel){ this.channel = channel; }
    public void setUserName(String userName){ this.userName = userName; }
    public String channel(){ return channel; }
    public String userName(){ return userName; }

    // render
    public String renderText(){
        StringBuilder sb = new StringBuilder();
        sb.append("Bill No: ").append(number).append("\n")
                .append("Date: ").append(createdAt).append("\n");
        if (userName != null) sb.append("User: ").append(userName).append("\n");
        if (channel != null)  sb.append("Channel: ").append(channel).append("\n");

        sb.append("----------------------------------------\n");
        for (var l: lines) {
            sb.append(l.itemCode()).append(" ").append(l.itemName())
                    .append(" x").append(l.quantity())
                    .append(" = ").append(l.lineTotal()).append("\n");
        }
        sb.append("----------------------------------------\n")
                .append("Subtotal: ").append(subtotal).append("\n")
                .append("Discount: ").append(discount).append("\n")
                .append("Tax: ").append(tax).append("\n")
                .append("Total: ").append(total).append("\n");

        // payment section
        sb.append("Paid via: ").append(paymentMethod == null ? "(unpaid)" : paymentMethod);
        if ("CARD".equals(paymentMethod) && cardLast4 != null) sb.append(" (**** ").append(cardLast4).append(")");
        sb.append("\nPaid: ").append(paidAmount)
                .append("  Change: ").append(changeAmount)
                .append("\n");

        return sb.toString();
    }

    // ====== State types ======
    private interface BillState {
        void onAddLine(Bill b, BillLine l);
        void onRemoveLineByCode(Bill b, String code);
        void onSetPayment(Bill b, Payment.Receipt r);
    }

    private static final class Draft implements BillState {
        @Override public void onAddLine(Bill b, BillLine l) { b.addLineInternal(l); }
        @Override public void onRemoveLineByCode(Bill b, String code) { b.removeLineInternal(code); }
        @Override public void onSetPayment(Bill b, Payment.Receipt r) {
            b.applyPaymentFields(r);
            b.state = new Paid(); // transition
        }
    }

    private static final class Paid implements BillState {
        @Override public void onAddLine(Bill b, BillLine l) {
            throw new IllegalStateException("Bill is already paid; cannot add lines.");
        }
        @Override public void onRemoveLineByCode(Bill b, String code) {
            throw new IllegalStateException("Bill is already paid; cannot remove lines.");
        }
        @Override public void onSetPayment(Bill b, Payment.Receipt r) {
            // idempotent: already paid
        }
    }
}
