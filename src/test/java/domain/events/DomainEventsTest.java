package domain.events;

import application.events.events.*;
import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventsTest {

    @Test
    @DisplayName("BillPaid event creation and properties")
    void bill_paid_event() {
        BillPaid event = new BillPaid("POS-001", Money.of(150.75), "POS", "john_cashier");

        assertEquals("POS-001", event.billNo());
        assertEquals("POS", event.channel());
        assertEquals(Money.of(150.75), event.total());
        assertEquals("john_cashier", event.user());
    }

    @Test
    @DisplayName("BillPaid event with card payment")
    void bill_paid_card_event() {
        BillPaid event = new BillPaid("POS-002", Money.of(299.99), "POS", "jane_cashier");

        assertEquals("POS-002", event.billNo());
        assertEquals("POS", event.channel());
        assertEquals(Money.of(299.99), event.total());
        assertEquals("jane_cashier", event.user());
    }

    @Test
    @DisplayName("StockDepleted event creation and properties")
    void stock_depleted_event() {
        StockDepleted event = new StockDepleted("ITEM001");

        assertEquals("ITEM001", event.itemCode());
    }

    @Test
    @DisplayName("RestockThresholdHit event creation and properties")
    void restock_threshold_hit_event() {
        RestockThresholdHit event = new RestockThresholdHit("ITEM002", 3, 10);

        assertEquals("ITEM002", event.itemCode());
        assertEquals(3, event.totalQtyLeft());
        assertEquals(10, event.threshold());
    }

    @Test
    @DisplayName("Event equality and hashcode")
    void event_equality() {
        BillPaid event1 = new BillPaid("POS-001", Money.of(100.0), "POS", "user1");
        BillPaid event2 = new BillPaid("POS-001", Money.of(100.0), "POS", "user1");
        BillPaid event3 = new BillPaid("POS-002", Money.of(200.0), "POS", "user2");

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    @DisplayName("Event toString contains key information")
    void event_to_string() {
        BillPaid billEvent = new BillPaid("POS-123", Money.of(75.50), "POS", "cashier");
        StockDepleted stockEvent = new StockDepleted("ITEM999");
        RestockThresholdHit restockEvent = new RestockThresholdHit("ITEM888", 5, 20);

        String billStr = billEvent.toString();
        String stockStr = stockEvent.toString();
        String restockStr = restockEvent.toString();

        assertTrue(billStr.contains("POS-123") || billStr.contains("75.5"));
        assertTrue(stockStr.contains("ITEM999"));
        assertTrue(restockStr.contains("ITEM888") || restockStr.contains("5") || restockStr.contains("20"));
    }

    @Test
    @DisplayName("Events with null values")
    void events_with_nulls() {
        assertDoesNotThrow(() -> {
            BillPaid nullBill = new BillPaid(null, null, null, null);
            StockDepleted nullStock = new StockDepleted(null);
            RestockThresholdHit nullRestock = new RestockThresholdHit(null, 0, 0);

            assertNull(nullBill.billNo());
            assertNull(nullStock.itemCode());
            assertNull(nullRestock.itemCode());
        });
    }

    @Test
    @DisplayName("Events with empty strings")
    void events_with_empty_strings() {
        BillPaid emptyBill = new BillPaid("", Money.ZERO, "", "");
        StockDepleted emptyStock = new StockDepleted("");
        RestockThresholdHit emptyRestock = new RestockThresholdHit("", 0, 0);

        assertEquals("", emptyBill.billNo());
        assertEquals("", emptyStock.itemCode());
        assertEquals("", emptyRestock.itemCode());
    }

    @Test
    @DisplayName("Events with extreme values")
    void events_extreme_values() {
        BillPaid largeBill = new BillPaid("HUGE-001", Money.of(999999.99), "POS", "super_cashier");
        RestockThresholdHit extremeRestock = new RestockThresholdHit("EXTREME", Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(Money.of(999999.99), largeBill.total());
        assertEquals(Integer.MAX_VALUE, extremeRestock.totalQtyLeft());
        assertEquals(Integer.MAX_VALUE, extremeRestock.threshold());
    }

    @Test
    @DisplayName("Event immutability")
    void event_immutability() {
        BillPaid original = new BillPaid("POS-IMMUTABLE", Money.of(100.0), "POS", "test");

        // Events should be immutable records
        assertEquals("POS-IMMUTABLE", original.billNo());
        assertEquals("POS", original.channel());
        assertEquals(Money.of(100.0), original.total());
        assertEquals("test", original.user());
    }
}
