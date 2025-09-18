package domain.pricing;

import domain.billing.Bill;
import domain.common.Money;

/**
 * Strategy for computing a discount amount for a given bill.
 * Implementations must return a non-negative Money value.
 */
public interface DiscountPolicy {
    Money computeDiscount(Bill bill);
    String code();
}
