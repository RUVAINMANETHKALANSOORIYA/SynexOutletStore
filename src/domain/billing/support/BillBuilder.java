package domain.billing.support;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;

public final class BillBuilder {
    private final Bill b;
    public BillBuilder(String number) { this.b = new Bill(number); }
    public BillBuilder user(String user) { b.setUserName(user); return this; }
    public BillBuilder channel(String channel) { b.setChannel(channel); return this; }
    public BillBuilder line(String code, String name, Money unitPrice, int qty) {
        b.addLine(new BillLine(code, name, unitPrice, qty, java.util.List.of())); return this;
    }
    public Bill build() { return b; }
}
