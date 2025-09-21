package domain.pricing;

import domain.billing.Bill;
import domain.common.Money;


public interface DiscountPolicy {
    Money computeDiscount(Bill bill);
    String code();
}
