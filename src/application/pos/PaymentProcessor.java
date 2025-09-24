package application.pos;

import domain.billing.Bill;
import domain.common.Money;
import domain.payment.CashPayment;
import domain.payment.CardPayment;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;

/**
 * Handles all payment processing operations for the POS system
 */
public final class PaymentProcessor {
    private final PricingService pricing;

    public PaymentProcessor(PricingService pricing) {
        this.pricing = pricing;
    }

    /**
     * Process cash payment
     */
    public Payment.Receipt processCashPayment(Bill bill, double amount, DiscountPolicy activeDiscount) {
        validateCashAmount(amount);

        try {
            pricing.finalizePricing(bill, activeDiscount);
            Money billTotal = bill.total();

            // Validate sufficient payment amount
            if (Money.of(amount).compareTo(billTotal) < 0) {
                throw new POSOperationException("Insufficient payment amount. Bill total: " + billTotal + ", Payment: LKR " + String.format("%.2f", amount));
            }

            var cash = new CashPayment();
            return cash.pay(billTotal, Money.of(amount));
        } catch (POSOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new POSOperationException("Cash payment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process card payment
     */
    public Payment.Receipt processCardPayment(Bill bill, String last4, DiscountPolicy activeDiscount) {
        validateCardNumber(last4);

        try {
            pricing.finalizePricing(bill, activeDiscount);
            var card = new CardPayment(last4);
            return card.pay(bill.total(), bill.total());
        } catch (IllegalArgumentException e) {
            throw new POSOperationException("Card payment declined or invalid: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Card payment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the cash payment amount
     */
    private void validateCashAmount(double amount) {
        if (amount <= 0) {
            throw new POSOperationException("Cash amount must be greater than zero. Provided: " + amount);
        }
        if (amount > 100000) {
            throw new POSOperationException("Cash amount too large. Maximum allowed: 100000. Provided: " + amount);
        }
    }

    /**
     * Validates the card number format (last 4 digits)
     */
    private void validateCardNumber(String last4) {
        if (last4 == null) {
            throw new POSOperationException("Card number cannot be null");
        }
        if (last4.trim().isEmpty()) {
            throw new POSOperationException("Card number cannot be empty");
        }
        if (!last4.matches("^\\d{4}$")) {
            throw new POSOperationException("Card number must be 4 digits");
        }
    }

    /**
     * Custom exception for POS operations
     */
    public static class POSOperationException extends RuntimeException {
        public POSOperationException(String message) {
            super(message);
        }

        public POSOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
