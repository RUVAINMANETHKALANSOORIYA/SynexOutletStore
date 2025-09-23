package domain.payment;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CashPaymentTest {

    @Test
    @DisplayName("CashPayment pay method with sufficient cash")
    void cash_payment_sufficient_cash() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(100.0);
        Money tendered = Money.of(120.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals("CASH", receipt.method());
        assertEquals(tendered, receipt.paid());
        assertEquals(Money.of(20.0), receipt.change());
        assertNull(receipt.cardLast4());
    }

    @Test
    @DisplayName("CashPayment calculates change correctly")
    void cash_payment_change_calculation() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(75.50);
        Money tendered = Money.of(100.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals(Money.of(24.50), receipt.change());
    }

    @Test
    @DisplayName("CashPayment with exact amount")
    void cash_payment_exact_amount() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(50.0);
        Money tendered = Money.of(50.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals(Money.ZERO, receipt.change());
    }

    @Test
    @DisplayName("CashPayment with insufficient cash throws exception")
    void cash_payment_insufficient_cash() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(100.0);
        Money tendered = Money.of(80.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> payment.pay(billTotal, tendered));

        assertTrue(ex.getMessage().contains("Insufficient") || ex.getMessage().contains("Needed"));
    }

    @Test
    @DisplayName("CashPayment with zero amounts")
    void cash_payment_zero_amounts() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.ZERO;
        Money tendered = Money.ZERO;

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals(Money.ZERO, receipt.change());
        assertEquals("CASH", receipt.method());
    }

    @Test
    @DisplayName("CashPayment with null tendered amount")
    void cash_payment_null_tendered() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(50.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> payment.pay(billTotal, null));

        assertTrue(ex.getMessage().contains("tendered") || ex.getMessage().contains("required"));
    }

    @Test
    @DisplayName("CashPayment with null bill amount")
    void cash_payment_null_bill() {
        CashPayment payment = new CashPayment();
        Money tendered = Money.of(100.0);

        assertThrows(Exception.class, () -> payment.pay(null, tendered));
    }

    @Test
    @DisplayName("CashPayment equals and hashCode")
    void cash_payment_equals_hashcode() {
        CashPayment payment1 = new CashPayment();
        CashPayment payment2 = new CashPayment();

        assertEquals(payment1, payment2);
        assertEquals(payment1.hashCode(), payment2.hashCode());
    }

    @Test
    @DisplayName("CashPayment large amount calculation")
    void cash_payment_large_amounts() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(999.99);
        Money tendered = Money.of(1000.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals(Money.of(0.01), receipt.change());
    }

    @Test
    @DisplayName("CashPayment toString representation")
    void cash_payment_string_representation() {
        CashPayment payment = new CashPayment();
        String toString = payment.toString();

        assertNotNull(toString);
        assertFalse(toString.isEmpty());
    }

    @Test
    @DisplayName("CashPayment receipt contains correct information")
    void cash_payment_receipt_information() {
        CashPayment payment = new CashPayment();
        Money billTotal = Money.of(75.50);
        Money tendered = Money.of(80.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertEquals("CASH", receipt.method());
        assertEquals(tendered, receipt.paid());
        assertEquals(Money.of(4.50), receipt.change());
        assertNull(receipt.cardLast4());
    }
}
