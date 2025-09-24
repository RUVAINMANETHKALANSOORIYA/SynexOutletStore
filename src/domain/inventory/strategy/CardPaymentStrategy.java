package domain.inventory.strategy;

import domain.billing.Bill;
import domain.payment.CardPayment;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;

/**
 * Card payment strategy implementation
 */
public class CardPaymentStrategy implements PaymentStrategy {
    private final PricingService pricing;

    public CardPaymentStrategy(PricingService pricing) {
        this.pricing = pricing;
    }

    @Override
    public Payment.Receipt processPayment(Bill bill, DiscountPolicy activeDiscount, Object... params) {
        if (params.length == 0 || !(params[0] instanceof String)) {
            throw new IllegalArgumentException("Card payment requires last 4 digits as first parameter");
        }

        String last4 = (String) params[0];
        validateCardNumber(last4);

        pricing.finalizePricing(bill, activeDiscount);
        var card = new CardPayment(last4);
        return card.pay(bill.total(), bill.total());
    }

    @Override
    public String getPaymentMethod() {
        return "CARD";
    }

    private void validateCardNumber(String last4) {
        if (last4 == null || last4.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }
        if (!last4.matches("^\\d{4}$")) {
            throw new IllegalArgumentException("Card number must be 4 digits");
        }
    }
}
