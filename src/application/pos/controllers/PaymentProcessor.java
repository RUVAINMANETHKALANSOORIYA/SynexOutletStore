package application.pos.controllers;

import domain.billing.Bill;
import domain.payment.Payment;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import domain.inventory.strategy.PaymentStrategyContext;

/**
 * Handles all payment processing operations for the POS system using Strategy pattern
 */
public final class PaymentProcessor {
    private final PaymentStrategyContext strategyContext;

    public PaymentProcessor(PricingService pricing) {
        this.strategyContext = new PaymentStrategyContext(pricing);
    }

    /**
     * Process cash payment using strategy pattern
     */
    public Payment.Receipt processCashPayment(Bill bill, double amount, DiscountPolicy activeDiscount) {
        try {
            return strategyContext.processPayment("CASH", bill, activeDiscount, amount);
        } catch (IllegalArgumentException e) {
            throw new POSOperationException(e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Cash payment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process card payment using strategy pattern
     */
    public Payment.Receipt processCardPayment(Bill bill, String last4, DiscountPolicy activeDiscount) {
        try {
            return strategyContext.processPayment("CARD", bill, activeDiscount, last4);
        } catch (IllegalArgumentException e) {
            throw new POSOperationException("Card payment declined or invalid: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Card payment processing failed: " + e.getMessage(), e);
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
