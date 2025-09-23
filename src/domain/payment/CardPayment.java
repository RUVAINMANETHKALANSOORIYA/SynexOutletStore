package domain.payment;

import domain.common.Money;

public final class CardPayment implements Payment {
    private final String last4;

    public CardPayment(String last4) {
        if (last4 == null || !last4.matches("\\d{4}"))
            throw new IllegalArgumentException("Card last4 must be 4 digits");
        this.last4 = last4;
    }

    @Override
    public Receipt pay(Money billTotal, Money tendered) {
        // In coursework we assume authorization success and the "tendered" equals total.
        if (tendered == null || tendered.compareTo(billTotal) != 0)
            throw new IllegalArgumentException("Card amount must equal total (" + billTotal + ")");
        return new Receipt("CARD", tendered, Money.ZERO, last4);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CardPayment that = (CardPayment) obj;
        return last4.equals(that.last4);
    }

    @Override
    public int hashCode() {
        return last4.hashCode();
    }
}
