package domain.payment;

import domain.common.Money;

public final class CashPayment implements Payment {
    @Override
    public Receipt pay(Money billTotal, Money tendered) {
        if (tendered == null) throw new IllegalArgumentException("Amount tendered required");
        if (tendered.compareTo(billTotal) < 0)
            throw new IllegalArgumentException("Insufficient cash. Needed " + billTotal + ", got " + tendered);
        Money change = tendered.minus(billTotal);
        return new Receipt("CASH", tendered, change, null);
    }
}
