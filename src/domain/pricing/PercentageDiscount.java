package domain.pricing;

import domain.billing.Bill;
import domain.common.Money;

public final class PercentageDiscount implements DiscountPolicy {
    private final int percent; // 0..100

    public PercentageDiscount(int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent must be 0..100");
        this.percent = percent;
    }

    @Override
    public Money computeDiscount(Bill bill) {
        // discount = subtotal * percent / 100
        Money sub = bill.computeSubtotal();
        if (percent == 0) return Money.ZERO;
        return sub.multiply(percent).divide(100);
    }

    @Override
    public String code() {
        return "PERCENTAGE(" + percent + "%)";
    }
}
