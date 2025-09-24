package domain.inventory.strategy;

import domain.billing.Bill;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;

/**
 * Strategy interface for different payment processing methods
 */
public interface PaymentStrategy {
    Payment.Receipt processPayment(Bill bill, DiscountPolicy activeDiscount, Object... params);
    String getPaymentMethod();
}
