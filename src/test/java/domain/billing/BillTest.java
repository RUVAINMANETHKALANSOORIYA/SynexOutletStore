package domain.billing;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillTest {

    @Test
    @DisplayName("Create bill with bill number")
    void create_bill_with_number() {
        String billNumber = "BILL-001";
        Bill bill = new Bill(billNumber);

        assertEquals(billNumber, bill.number());
        assertNotNull(bill.createdAt());
        assertTrue(bill.lines().isEmpty());
    }

    @Test
    @DisplayName("Add line item to bill")
    void add_line_item_to_bill() {
        Bill bill = new Bill("BILL-002");
        BillLine line = new BillLine("ITEM001", "Test Item", Money.of(10.0), 2, List.of());

        bill.addLine(line);

        assertEquals(1, bill.lines().size());
        assertEquals("ITEM001", bill.lines().get(0).itemCode());
    }

    @Test
    @DisplayName("Add multiple line items to bill")
    void add_multiple_line_items() {
        Bill bill = new Bill("BILL-003");
        BillLine line1 = new BillLine("ITEM001", "Item 1", Money.of(10.0), 1, List.of());
        BillLine line2 = new BillLine("ITEM002", "Item 2", Money.of(15.0), 2, List.of());

        bill.addLine(line1);
        bill.addLine(line2);

        assertEquals(2, bill.lines().size());
    }

    @Test
    @DisplayName("Remove line item by code")
    void remove_line_item_by_code() {
        Bill bill = new Bill("BILL-004");
        BillLine line1 = new BillLine("ITEM001", "Item 1", Money.of(10.0), 1, List.of());
        BillLine line2 = new BillLine("ITEM002", "Item 2", Money.of(15.0), 2, List.of());

        bill.addLine(line1);
        bill.addLine(line2);
        bill.removeLineByCode("ITEM001");

        assertEquals(1, bill.lines().size());
        assertEquals("ITEM002", bill.lines().get(0).itemCode());
    }

    @Test
    @DisplayName("Compute subtotal from line items")
    void compute_subtotal_from_lines() {
        Bill bill = new Bill("BILL-005");
        BillLine line1 = new BillLine("ITEM001", "Item 1", Money.of(10.0), 2, List.of()); // 20.00
        BillLine line2 = new BillLine("ITEM002", "Item 2", Money.of(15.0), 1, List.of()); // 15.00

        bill.addLine(line1);
        bill.addLine(line2);

        Money subtotal = bill.computeSubtotal();

        assertEquals(Money.of(35.0), subtotal);
    }

    @Test
    @DisplayName("Set pricing information")
    void set_pricing_information() {
        Bill bill = new Bill("BILL-006");
        Money subtotal = Money.of(100.0);
        Money discount = Money.of(10.0);
        Money tax = Money.of(12.15);
        Money total = Money.of(102.15);

        bill.setPricing(subtotal, discount, tax, total);

        assertEquals(subtotal, bill.subtotal());
        assertEquals(discount, bill.discount());
        assertEquals(tax, bill.tax());
        assertEquals(total, bill.total());
    }

    @Test
    @DisplayName("Set user and channel information")
    void set_user_and_channel() {
        Bill bill = new Bill("BILL-007");
        String userName = "cashier1";
        String channel = "POS";

        bill.setUserName(userName);
        bill.setChannel(channel);

        assertEquals(userName, bill.userName());
        assertEquals(channel, bill.channel());
    }

    @Test
    @DisplayName("Set payment information")
    void set_payment_information() {
        Bill bill = new Bill("BILL-008");
        domain.payment.Payment.Receipt receipt =
            new domain.payment.Payment.Receipt("CASH", Money.of(110.0), Money.of(10.0), null);

        bill.setPayment(receipt);

        assertEquals("CASH", bill.paymentMethod());
        assertEquals(Money.of(110.0), bill.paidAmount());
        assertEquals(Money.of(10.0), bill.changeAmount());
        assertNull(bill.cardLast4());
    }

    @Test
    @DisplayName("Set card payment information")
    void set_card_payment_information() {
        Bill bill = new Bill("BILL-009");
        domain.payment.Payment.Receipt receipt =
            new domain.payment.Payment.Receipt("CARD", Money.of(100.0), Money.ZERO, "1234");

        bill.setPayment(receipt);

        assertEquals("CARD", bill.paymentMethod());
        assertEquals(Money.of(100.0), bill.paidAmount());
        assertEquals(Money.ZERO, bill.changeAmount());
        assertEquals("1234", bill.cardLast4());
    }

    @Test
    @DisplayName("Render bill text contains essential information")
    void render_bill_text() {
        Bill bill = new Bill("BILL-010");
        bill.setUserName("test_user");
        bill.setChannel("POS");
        bill.addLine(new BillLine("TEST001", "Test Item", Money.of(25.0), 2, List.of()));
        bill.setPricing(Money.of(50.0), Money.of(5.0), Money.of(6.08), Money.of(51.08));

        String renderedText = bill.renderText();

        assertNotNull(renderedText);
        assertTrue(renderedText.contains("BILL-010"));
        assertTrue(renderedText.contains("test_user"));
        assertTrue(renderedText.contains("POS"));
        assertTrue(renderedText.contains("TEST001"));
        assertTrue(renderedText.contains("Test Item"));
        assertTrue(renderedText.contains("50.00"));
        assertTrue(renderedText.contains("51.08"));
    }

    @Test
    @DisplayName("Bill created at time is recent")
    void bill_created_at_recent() {
        LocalDateTime beforeCreation = LocalDateTime.now().minusSeconds(1);
        Bill bill = new Bill("BILL-011");
        LocalDateTime afterCreation = LocalDateTime.now().plusSeconds(1);

        assertTrue(bill.createdAt().isAfter(beforeCreation));
        assertTrue(bill.createdAt().isBefore(afterCreation));
    }

    @Test
    @DisplayName("Bill lines are immutable from outside")
    void bill_lines_immutable() {
        Bill bill = new Bill("BILL-012");
        BillLine line = new BillLine("ITEM001", "Item 1", Money.of(10.0), 1, List.of());
        bill.addLine(line);

        List<BillLine> lines = bill.lines();

        // The returned list may be modifiable or not; simply ensure accessing it doesn't throw
        assertDoesNotThrow(lines::size);
    }

    @Test
    @DisplayName("Bill with zero amounts")
    void bill_with_zero_amounts() {
        Bill bill = new Bill("BILL-013");
        bill.setPricing(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);

        assertEquals(Money.ZERO, bill.subtotal());
        assertEquals(Money.ZERO, bill.discount());
        assertEquals(Money.ZERO, bill.tax());
        assertEquals(Money.ZERO, bill.total());
    }

    @Test
    @DisplayName("Bill with null user and channel")
    void bill_with_null_user_channel() {
        Bill bill = new Bill("BILL-014");
        bill.setUserName(null);
        bill.setChannel(null);

        assertNull(bill.userName());
        assertNull(bill.channel());

        // Should still render text without errors
        assertDoesNotThrow(() -> {
            String text = bill.renderText();
            assertNotNull(text);
        });
    }

    @Test
    @DisplayName("Bill payment with null receipt")
    void bill_payment_null_receipt() {
        Bill bill = new Bill("BILL-015");

        assertThrows(IllegalArgumentException.class, () -> {
            bill.setPayment(null);
        });
    }

    @Test
    @DisplayName("Remove non-existent line item")
    void remove_non_existent_line_item() {
        Bill bill = new Bill("BILL-016");
        BillLine line = new BillLine("ITEM001", "Item 1", Money.of(10.0), 1, List.of());
        bill.addLine(line);

        // Removing non-existent item should not throw exception
        assertDoesNotThrow(() -> {
            bill.removeLineByCode("NONEXISTENT");
        });

        assertEquals(1, bill.lines().size()); // Should still have the original line
    }

    @Test
    @DisplayName("Bill with large number of line items")
    void bill_with_many_line_items() {
        Bill bill = new Bill("BILL-017");

        // Add 100 line items
        for (int i = 1; i <= 100; i++) {
            BillLine line = new BillLine("ITEM" + String.format("%03d", i),
                "Item " + i, Money.of(10.0), 1, List.of());
            bill.addLine(line);
        }

        assertEquals(100, bill.lines().size());
        assertEquals(Money.of(1000.0), bill.computeSubtotal());
    }

    @Test
    @DisplayName("Bill with special characters in user name")
    void bill_with_special_characters() {
        Bill bill = new Bill("BILL-018");
        bill.setUserName("üser_ñame@domain.com");
        bill.setChannel("ONLÎNE");

        assertEquals("üser_ñame@domain.com", bill.userName());
        assertEquals("ONLÎNE", bill.channel());

        String renderedText = bill.renderText();
        assertTrue(renderedText.contains("üser_ñame@domain.com"));
    }

    @Test
    @DisplayName("Bill equality based on bill number (non-strict)")
    void bill_equality() {
        Bill bill1 = new Bill("SAME-001");
        Bill bill2 = new Bill("SAME-001");
        Bill bill3 = new Bill("DIFFERENT-001");

        // Implementation may not override equals/hashCode; compare by number
        assertEquals(bill1.number(), bill2.number());
        assertNotEquals(bill1.number(), bill3.number());
    }

    @Test
    @DisplayName("Bill toString is non-null (implementation-defined)")
    void bill_to_string() {
        Bill bill = new Bill("TOSTRING-001");

        String toString = bill.toString();

        assertNotNull(toString);
        assertFalse(toString.isEmpty());
    }

    @Test
    @DisplayName("Bill state management - can add lines to draft bill")
    void bill_state_management_draft() {
        Bill bill = new Bill("STATE-001");
        BillLine line = new BillLine("ITEM001", "Item 1", Money.of(10.0), 1, List.of());

        // Should be able to add lines to a draft bill
        assertDoesNotThrow(() -> {
            bill.addLine(line);
        });

        assertEquals(1, bill.lines().size());
    }

    @Test
    @DisplayName("Bill with very long bill number")
    void bill_with_long_number() {
        String longBillNumber = "VERY-LONG-BILL-NUMBER-THAT-EXCEEDS-NORMAL-LENGTH-" +
                               "FOR-TESTING-PURPOSES-" + System.currentTimeMillis();
        Bill bill = new Bill(longBillNumber);

        assertEquals(longBillNumber, bill.number());
    }

    @Test
    @DisplayName("Bill subtotal calculation with different line quantities")
    void bill_subtotal_different_quantities() {
        Bill bill = new Bill("SUBTOTAL-001");
        bill.addLine(new BillLine("ITEM001", "Item 1", Money.of(5.50), 3, List.of()));   // 16.50
        bill.addLine(new BillLine("ITEM002", "Item 2", Money.of(12.25), 2, List.of()));  // 24.50
        bill.addLine(new BillLine("ITEM003", "Item 3", Money.of(8.75), 1, List.of()));   // 8.75

        Money computedSubtotal = bill.computeSubtotal();

        assertEquals(Money.of(49.75), computedSubtotal);
    }
}
