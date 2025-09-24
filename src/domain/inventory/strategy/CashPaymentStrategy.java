package domain.inventory.strategy;

import domain.billing.Bill;
import domain.common.Money;
import domain.payment.CashPayment;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;

/**
 * Cash payment strategy implementation
 */
public class CashPaymentStrategy implements PaymentStrategy {
    private final PricingService pricing;

    public CashPaymentStrategy(PricingService pricing) {
        this.pricing = pricing;
    }

    @Override
    public Payment.Receipt processPayment(Bill bill, DiscountPolicy activeDiscount, Object... params) {
        if (params.length == 0 || !(params[0] instanceof Double)) {
            throw new IllegalArgumentException("Cash payment requires amount as first parameter");
        }

        double amount = (Double) params[0];
        validateCashAmount(amount);

        pricing.finalizePricing(bill, activeDiscount);
        Money billTotal = bill.total();

        if (Money.of(amount).compareTo(billTotal) < 0) {
            throw new IllegalArgumentException("Insufficient payment amount. Bill total: " + billTotal + ", Payment: LKR " + String.format("%.2f", amount));
        }

        var cash = new CashPayment();
        return cash.pay(billTotal, Money.of(amount));
    }

    @Override
    public String getPaymentMethod() {
        return "CASH";
    }

    private void validateCashAmount(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Cash amount must be greater than zero. Provided: " + amount);
        }
        if (amount > 100000) {
            throw new IllegalArgumentException("Cash amount too large. Maximum allowed: 100000. Provided: " + amount);
        }
    }
}
