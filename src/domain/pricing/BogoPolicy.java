package domain.pricing;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;

public final class BogoPolicy implements DiscountPolicy {
    @Override
    public Money computeDiscount(Bill bill) {

        Money d = Money.ZERO;
        for (BillLine l : bill.lines()) {
            int free = l.quantity() / 2;
            if (free > 0) d = d.plus(l.unitPrice().multiply(free));
        }
        return d;
    }

    @Override
    public String code() { return "BOGO"; }
}
