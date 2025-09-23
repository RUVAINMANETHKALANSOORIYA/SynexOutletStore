package domain.payment;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardPaymentTest {

    @Test
    @DisplayName("CardPayment constructor with valid 4-digit last4")
    void card_payment_valid_last4() {
        CardPayment payment = new CardPayment("1234");
        assertNotNull(payment);
    }

    @Test
    @DisplayName("CardPayment throws exception for non-4-digit last4")
    void card_payment_invalid_last4_length() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> new CardPayment("123"));
        assertTrue(ex1.getMessage().contains("4 digits"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> new CardPayment("12345"));
        assertTrue(ex2.getMessage().contains("4 digits"));
    }

    @Test
    @DisplayName("CardPayment throws exception for null last4")
    void card_payment_null_last4() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CardPayment(null));
        assertTrue(ex.getMessage().contains("4 digits"));
    }

    @Test
    @DisplayName("CardPayment throws exception for empty last4")
    void card_payment_empty_last4() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CardPayment(""));
        assertTrue(ex.getMessage().contains("4 digits"));
    }

    @Test
    @DisplayName("CardPayment with numeric last4")
    void card_payment_numeric_last4() {
        CardPayment payment = new CardPayment("0000");
        assertNotNull(payment);
    }

    @Test
    @DisplayName("CardPayment pay method with equal amount")
    void card_payment_pay_equal_amount() {
        CardPayment payment = new CardPayment("1234");
        Money billTotal = Money.of(100.0);
        Money tendered = Money.of(100.0);

        Payment.Receipt receipt = payment.pay(billTotal, tendered);

        assertNotNull(receipt);
        assertEquals("CARD", receipt.method());
        assertEquals(tendered, receipt.paid());
        assertEquals(Money.ZERO, receipt.change());
        assertEquals("1234", receipt.cardLast4());
    }

    @Test
    @DisplayName("CardPayment pay method with unequal amount throws exception")
    void card_payment_pay_unequal_amount() {
        CardPayment payment = new CardPayment("5678");
        Money billTotal = Money.of(100.0);
        Money tendered = Money.of(90.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> payment.pay(billTotal, tendered));
        assertTrue(ex.getMessage().contains("equal total"));
    }

    @Test
    @DisplayName("CardPayment pay method with null tendered amount")
    void card_payment_pay_null_tendered() {
        CardPayment payment = new CardPayment("9999");
        Money billTotal = Money.of(50.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> payment.pay(billTotal, null));
        assertTrue(ex.getMessage().contains("equal total"));
    }

    @Test
    @DisplayName("CardPayment equals and hashCode")
    void card_payment_equals_hashcode() {
        CardPayment payment1 = new CardPayment("1234");
        CardPayment payment2 = new CardPayment("1234");
        CardPayment payment3 = new CardPayment("5678");

        assertEquals(payment1, payment2);
        assertNotEquals(payment1, payment3);
        assertEquals(payment1.hashCode(), payment2.hashCode());
    }

    @Test
    @DisplayName("CardPayment with different last4 digits")
    void card_payment_different_last4() {
        CardPayment payment1 = new CardPayment("1111");
        CardPayment payment2 = new CardPayment("2222");
        CardPayment payment3 = new CardPayment("3333");

        assertNotEquals(payment1, payment2);
        assertNotEquals(payment2, payment3);
        assertNotEquals(payment1, payment3);
    }

    @Test
    @DisplayName("CardPayment toString representation")
    void card_payment_string_representation() {
        CardPayment payment = new CardPayment("4567");
        String toString = payment.toString();

        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("CardPayment receipt contains correct information")
    void card_payment_receipt_information() {
        CardPayment payment = new CardPayment("8765");
        Money amount = Money.of(75.50);

        Payment.Receipt receipt = payment.pay(amount, amount);

        assertEquals("CARD", receipt.method());
        assertEquals(amount, receipt.paid());
        assertEquals(Money.ZERO, receipt.change());
        assertEquals("8765", receipt.cardLast4());
    }
}
