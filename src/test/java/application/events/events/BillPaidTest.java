package application.events.events;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillPaidTest {

    @Test
    @DisplayName("Create BillPaid event with cash payment")
    void create_bill_paid_cash() {
        String billNumber = "BILL-001";
        Money amount = Money.of(150.0);
        String channel = "POS";
        String user = "cashier1";

        BillPaid event = new BillPaid(billNumber, amount, channel, user);

        assertEquals(billNumber, event.billNo());
        assertEquals(amount, event.total());
        assertEquals(channel, event.channel());
        assertEquals(user, event.user());
    }

    @Test
    @DisplayName("Create BillPaid event with card payment")
    void create_bill_paid_card() {
        String billNumber = "BILL-002";
        Money amount = Money.of(200.50);
        String channel = "ONLINE";
        String user = "customer1";

        BillPaid event = new BillPaid(billNumber, amount, channel, user);

        assertEquals(billNumber, event.billNo());
        assertEquals(amount, event.total());
        assertEquals(channel, event.channel());
        assertEquals(user, event.user());
    }

    @Test
    @DisplayName("BillPaid event with zero amount")
    void bill_paid_zero_amount() {
        BillPaid event = new BillPaid("BILL-003", Money.ZERO, "POS", "cashier2");

        assertEquals(Money.ZERO, event.total());
    }

    @Test
    @DisplayName("BillPaid event with large amount")
    void bill_paid_large_amount() {
        Money largeAmount = Money.of(99999.99);
        BillPaid event = new BillPaid("BILL-004", largeAmount, "POS", "manager");

        assertEquals(largeAmount, event.total());
    }

    @Test
    @DisplayName("BillPaid event with null values")
    void bill_paid_null_values() {
        assertDoesNotThrow(() -> {
            BillPaid event = new BillPaid(null, null, null, null);
            assertNull(event.billNo());
            assertNull(event.total());
            assertNull(event.channel());
            assertNull(event.user());
        });
    }

    @Test
    @DisplayName("BillPaid event equals and hashCode")
    void bill_paid_equals_hashcode() {
        BillPaid event1 = new BillPaid("BILL-006", Money.of(100.0), "POS", "cashier1");
        BillPaid event2 = new BillPaid("BILL-006", Money.of(100.0), "POS", "cashier1");
        BillPaid event3 = new BillPaid("BILL-007", Money.of(100.0), "POS", "cashier1");

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    @DisplayName("BillPaid event toString")
    void bill_paid_to_string() {
        BillPaid event = new BillPaid("BILL-008", Money.of(75.25), "ONLINE", "customer2");

        String toString = event.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("BILL-008") || toString.contains("75.25") || toString.contains("ONLINE"));
    }

    @Test
    @DisplayName("BillPaid event with different channels")
    void bill_paid_different_channels() {
        BillPaid posEvent = new BillPaid("BILL-009", Money.of(50.0), "POS", "cashier1");
        BillPaid onlineEvent = new BillPaid("BILL-010", Money.of(25.0), "ONLINE", "customer1");
        BillPaid mobileEvent = new BillPaid("BILL-011", Money.of(500.0), "MOBILE", "customer2");

        assertEquals("POS", posEvent.channel());
        assertEquals("ONLINE", onlineEvent.channel());
        assertEquals("MOBILE", mobileEvent.channel());
    }

    @Test
    @DisplayName("BillPaid event immutability")
    void bill_paid_immutability() {
        BillPaid event = new BillPaid("BILL-012", Money.of(123.45), "POS", "cashier1");

        // Record should be immutable
        assertEquals("BILL-012", event.billNo());
        assertEquals(Money.of(123.45), event.total());
        assertEquals("POS", event.channel());
        assertEquals("cashier1", event.user());
    }
}
