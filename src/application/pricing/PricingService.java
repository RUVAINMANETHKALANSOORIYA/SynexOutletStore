package application.pricing;

import domain.billing.Bill;
import domain.common.Money;
import domain.pricing.DiscountPolicy;

public final class PricingService {
    private final double taxPercent; // e.g., 15.0 for 15%

    public PricingService(double taxPercent) {
        if (taxPercent < 0) throw new IllegalArgumentException("taxPercent must be >= 0");
        this.taxPercent = taxPercent;
    }

    /** Compute subtotal, discount (via policy), tax, and total; with safety caps. */
    public void finalizePricing(Bill bill, DiscountPolicy policy) {
        Money sub = bill.computeSubtotal();

        // Compute discount (never let it exceed the subtotal)
        Money dis = (policy == null) ? Money.ZERO : policy.computeDiscount(bill);
        if (dis.compareTo(sub) > 0) dis = sub;

        // Amount after discount (never negative)
        Money after = sub.minus(dis);
        if (after.compareTo(Money.ZERO) < 0) after = Money.ZERO;

        // Tax on the post-discount amount (percent -> divide by 100)
        Money tax = (taxPercent == 0.0) ? Money.ZERO : after.multiply(taxPercent).divide(100);

        // Total = after + tax
        bill.setPricing(sub, dis, tax, after.plus(tax));
    }
}
