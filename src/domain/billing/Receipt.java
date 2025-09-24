package domain.billing;

import domain.common.Money;
import java.time.LocalDateTime;
import java.util.List;

public final class Receipt {
    private final String billNumber;
    private final LocalDateTime timestamp;
    private final List<BillLine> items;
    private final Money subtotal;
    private final Money discount;
    private final Money tax;
    private final Money total;
    private final String paymentMethod;
    private final Money paidAmount;
    private final Money changeAmount;
    private final String cardLast4;
    private final String channel;
    private final String cashier;

    public Receipt(String billNumber, LocalDateTime timestamp, List<BillLine> items,
                   Money subtotal, Money discount, Money tax, Money total,
                   String paymentMethod, Money paidAmount, Money changeAmount,
                   String cardLast4, String channel, String cashier) {
        this.billNumber = billNumber;
        this.timestamp = timestamp;
        this.items = List.copyOf(items); // Defensive copy
        this.subtotal = subtotal;
        this.discount = discount;
        this.tax = tax;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.paidAmount = paidAmount;
        this.changeAmount = changeAmount;
        this.cardLast4 = cardLast4;
        this.channel = channel;
        this.cashier = cashier;
    }

    // Factory method to create Receipt from a paid Bill
    public static Receipt fromBill(Bill bill, String cashier) {
        return new Receipt(
            bill.number(),
            bill.createdAt(),
            bill.lines(),
            bill.subtotal(),
            bill.discount(),
            bill.tax(),
            bill.total(),
            bill.paymentMethod(),
            bill.paidAmount(),
            bill.changeAmount(),
            bill.cardLast4(),
            bill.channel(),
            cashier
        );
    }

    // Getters
    public String getBillNumber() { return billNumber; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public List<BillLine> getItems() { return items; }
    public Money getSubtotal() { return subtotal; }
    public Money getDiscount() { return discount; }
    public Money getTax() { return tax; }
    public Money getTotal() { return total; }
    public String getPaymentMethod() { return paymentMethod; }
    public Money getPaidAmount() { return paidAmount; }
    public Money getChangeAmount() { return changeAmount; }
    public String getCardLast4() { return cardLast4; }
    public String getChannel() { return channel; }
    public String getCashier() { return cashier; }
}
