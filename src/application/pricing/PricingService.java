package application.pricing;

import domain.billing.Bill;
import domain.common.Money;
import domain.pricing.DiscountPolicy;
import ports.in.InventoryService;


public final class PricingService {
    private final double taxPercent; // e.g., 15.0 for 15%
    private final InventoryService inventoryService;

    public PricingService(double taxPercent, InventoryService inventoryService) {
        if (taxPercent < 0) throw new IllegalArgumentException("taxPercent must be >= 0");
        this.taxPercent = taxPercent;
        this.inventoryService = inventoryService;
    }

    // Backward compatibility constructor
    public PricingService(double taxPercent) {
        this(taxPercent, null);
    }

    public void finalizePricing(Bill bill, DiscountPolicy policy) {
        new StandardPricing().price(bill, policy, this.taxPercent, this.inventoryService);
    }

    private static abstract class PricingTemplate {
        final void price(Bill bill, DiscountPolicy policy, double taxPercent, InventoryService inventoryService) {
            // Calculate original subtotal (without batch discounts) for discount display
            Money originalSubtotal = computeOriginalSubtotal(bill, inventoryService);

            // Use the already calculated line totals (which include batch discounts)
            Money actualSubtotal = bill.computeSubtotal(); // This uses BillLine.lineTotal() which respects batch discounts

            // Calculate batch discount amount for display
            Money batchDiscountAmount = originalSubtotal.minus(actualSubtotal);

            // Only apply additional policy discounts (percentage, BOGO) on top of batch discounts
            Money additionalDiscount = computeDiscount(bill, actualSubtotal, policy);
            Money totalDiscount = batchDiscountAmount.plus(additionalDiscount);

            Money baseAfterAllDiscounts = clampNonNegative(originalSubtotal.minus(totalDiscount));

            PriceCalculator calc = new BaseCalculator();
            calc = new TaxDecorator(calc, taxPercent);

            Money tax = calc.extras(baseAfterAllDiscounts);
            Money total = baseAfterAllDiscounts.plus(tax);

            // Show total discount amount (batch + policy discounts)
            bill.setPricing(originalSubtotal, totalDiscount, tax, total);
        }

        protected Money computeSubtotal(Bill bill) {
            // This correctly uses BillLine.lineTotal() which includes batch-discounted unit prices
            return bill.computeSubtotal();
        }

        /**
         * Calculate what the subtotal would be without any batch discounts (for discount display)
         */
        protected Money computeOriginalSubtotal(Bill bill, InventoryService inventoryService) {
            Money original = Money.ZERO;
            for (var line : bill.lines()) {
                // Get original price from inventory and multiply by quantity
                Money originalLineTotal = getOriginalItemPrice(line.itemCode(), inventoryService).multiply(line.quantity());
                original = original.plus(originalLineTotal);
            }
            return original;
        }

        /**
         * Get the original item price without any batch discounts
         */
        private Money getOriginalItemPrice(String itemCode, InventoryService inventoryService) {
            if (inventoryService == null) {
                // Fallback for backward compatibility - this won't show proper discounts
                return Money.ZERO;
            }
            return inventoryService.priceOf(itemCode);
        }

        protected Money computeDiscount(Bill bill, Money subtotal, DiscountPolicy policy) {
            // Avoid double-counting batch discounts: unit prices may already include batch discounts
            if (policy != null && "BATCH_DISCOUNTS".equals(policy.code())) {
                // Pricing already accounts for batch discounts via original vs actual subtotal
                return Money.ZERO;
            }

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
