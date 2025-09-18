package domain.payment;

import domain.common.Money;

public interface Payment {
    Receipt pay(Money billTotal, Money amountTendered);

    record Receipt(String method, Money paid, Money change, String cardLast4) {}
}
