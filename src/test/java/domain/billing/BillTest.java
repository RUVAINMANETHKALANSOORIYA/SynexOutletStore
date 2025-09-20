package domain.billing;

import domain.common.Money;
import domain.payment.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillTest {

    @Test
    @DisplayName("draft allows add/remove; computes subtotal and render includes meta")
    void draft_add_remove_compute_render() {
        Bill b = new Bill("B-1");
        b.setUserName("alice");
        b.setChannel("POS");

        b.addLine(new BillLine("X", "ItemX", Money.of(10.0), 2, List.of()));
        b.addLine(new BillLine("Y", "ItemY", Money.of(5.5), 3, List.of()));
        assertEquals(2, b.lines().size());
        assertEquals(Money.of(10.0 * 2 + 5.5 * 3), b.computeSubtotal());

        b.removeLineByCode("X");
        assertEquals(1, b.lines().size());
        assertEquals("Y", b.lines().get(0).itemCode());

        b.setPricing(b.computeSubtotal(), Money.ZERO, Money.ZERO, b.computeSubtotal());
        String txt = b.renderText();
        assertTrue(txt.contains("Bill No: B-1"));
        assertTrue(txt.contains("User: alice"));
        assertTrue(txt.contains("Channel: POS"));
        assertTrue(txt.contains("Subtotal: "));
        assertTrue(txt.contains("Paid via: "));
    }

    @Test
    @DisplayName("setPayment transitions to Paid and guards against mutations")
    void payment_transitions_and_guards() {
        Bill b = new Bill("B-2");
        b.addLine(new BillLine("X", "ItemX", Money.of(10.0), 1, List.of()));
        b.setPricing(b.computeSubtotal(), Money.ZERO, Money.ZERO, b.computeSubtotal());

        Payment.Receipt r = new Payment.Receipt("CASH", Money.of(10.0), Money.ZERO, null);
        b.setPayment(r);

        assertEquals("CASH", b.paymentMethod());
        assertEquals(Money.of(10.0), b.paidAmount());
        assertEquals(Money.ZERO, b.changeAmount());
        assertThrows(IllegalStateException.class, () -> b.addLine(new BillLine("Y", "ItemY", Money.of(1), 1, List.of())));
        assertThrows(IllegalStateException.class, () -> b.removeLineByCode("X"));

        // idempotent setPayment when already paid
        b.setPayment(r);
        assertEquals("CASH", b.paymentMethod());
    }
}
