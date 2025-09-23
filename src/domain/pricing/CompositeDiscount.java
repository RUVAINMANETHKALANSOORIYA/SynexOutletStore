package domain.pricing;

import domain.billing.Bill;
import domain.common.Money;

import java.util.List;

public final class CompositeDiscount implements DiscountPolicy {
    private final List<DiscountPolicy> policies;
    public CompositeDiscount(List<DiscountPolicy> policies) {
        this.policies = List.copyOf(policies);
    }
    @Override public Money computeDiscount(Bill bill) {
        Money sum = Money.ZERO;
        for (DiscountPolicy p : policies) {
            if (p == null) continue;
            sum = sum.plus(p.computeDiscount(bill));
        }
        // caller will clamp to subtotal if needed
        return sum;
    }
    @Override public String code() { return "COMPOSITE"; }
}
