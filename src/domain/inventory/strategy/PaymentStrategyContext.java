package domain.inventory.strategy;

import domain.billing.Bill;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;

import java.util.HashMap;
import java.util.Map;

/**
 * Context class that manages payment strategies
 */
public class PaymentStrategyContext {
    private final Map<String, PaymentStrategy> strategies = new HashMap<>();

    public PaymentStrategyContext(PricingService pricing) {
        strategies.put("CASH", new CashPaymentStrategy(pricing));
        strategies.put("CARD", new CardPaymentStrategy(pricing));
    }

    public Payment.Receipt processPayment(String method, Bill bill, DiscountPolicy activeDiscount, Object... params) {
        PaymentStrategy strategy = strategies.get(method.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
        return strategy.processPayment(bill, activeDiscount, params);
    }

    public void addStrategy(String method, PaymentStrategy strategy) {
        strategies.put(method.toUpperCase(), strategy);
    }
}
