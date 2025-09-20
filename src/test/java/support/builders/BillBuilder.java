package support.builders;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;

import java.util.ArrayList;
import java.util.List;

public class BillBuilder {
    private String number = "B-TEST-0001";
    private String user = "tester";
    private String channel = "POS";
    private final List<BillLine> lines = new ArrayList<>();

    public BillBuilder number(String number){ this.number = number; return this; }
    public BillBuilder user(String user){ this.user = user; return this; }
    public BillBuilder channel(String channel){ this.channel = channel; return this; }
    public BillBuilder addLine(BillLine line){ this.lines.add(line); return this; }

    public Bill build(){
        Bill b = new Bill(number);
        b.setUserName(user);
        b.setChannel(channel);
        for (BillLine l: lines) b.addLine(l);
        Money sub = b.computeSubtotal();
        b.setPricing(sub, Money.ZERO, Money.ZERO, sub);
        return b;
    }
}