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

    public void finalizePricing(Bill bill, DiscountPolicy policy) {
        new StandardPricing().price(bill, policy, this.taxPercent);
    }

    private static abstract class PricingTemplate {
        final void price(Bill bill, DiscountPolicy policy, double taxPercent) {
            Money subtotal = computeSubtotal(bill);
            Money discount = computeDiscount(bill, subtotal, policy);
            Money baseAfterDiscount = clampNonNegative(subtotal.minus(discount));

            PriceCalculator calc = new BaseCalculator();
            calc = new TaxDecorator(calc, taxPercent);

            Money tax = calc.extras(baseAfterDiscount);
            Money total = baseAfterDiscount.plus(tax);
            bill.setPricing(subtotal, discount, tax, total);
        }

        protected Money computeSubtotal(Bill bill) {
            return bill.computeSubtotal();
        }

        protected Money computeDiscount(Bill bill, Money subtotal, DiscountPolicy policy) {
            DiscountHandler pipeline = new NoopDiscountHandler();
            if (policy != null) {
                pipeline = new PolicyHandler(policy, pipeline);
            }


            Money dis = pipeline.apply(bill, subtotal);
            if (dis.compareTo(subtotal) > 0) dis = subtotal;
            return clampNonNegative(dis);
        }

        protected static Money clampNonNegative(Money m) {
            return (m.compareTo(Money.ZERO) < 0) ? Money.ZERO : m;
        }
    }

    private static final class StandardPricing extends PricingTemplate { }

    private interface DiscountHandler {
        Money apply(Bill bill, Money subtotal);
    }

    private static final class NoopDiscountHandler implements DiscountHandler {
        @Override public Money apply(Bill bill, Money subtotal) { return Money.ZERO; }
    }

    private static final class PolicyHandler implements DiscountHandler {
        private final DiscountPolicy policy;
        private final DiscountHandler next;
        PolicyHandler(DiscountPolicy policy, DiscountHandler next) {
            this.policy = policy; this.next = next == null ? new NoopDiscountHandler() : next;
        }
        @Override public Money apply(Bill bill, Money subtotal) {
            Money here = policy.computeDiscount(bill);
            Money rest = next.apply(bill, subtotal);
            Money total = here.plus(rest);
            return (total.compareTo(subtotal) > 0) ? subtotal : total;
        }
    }

    // ===================== Decorator =====================
    private interface PriceCalculator {
        Money extras(Money baseAfterDiscount);
    }

    private static final class BaseCalculator implements PriceCalculator {
        @Override public Money extras(Money baseAfterDiscount) { return Money.ZERO; }
    }

    private static final class TaxDecorator implements PriceCalculator {
        private final PriceCalculator inner;
        private final double taxPercent;
        TaxDecorator(PriceCalculator inner, double taxPercent) {
            this.inner = inner; this.taxPercent = Math.max(0, taxPercent);
        }
        @Override public Money extras(Money baseAfterDiscount) {
            Money innerExtras = inner.extras(baseAfterDiscount);
            Money tax = (taxPercent == 0.0) ? Money.ZERO : baseAfterDiscount.multiply(taxPercent).divide(100);
            return innerExtras.plus(tax);
        }
    }
}
