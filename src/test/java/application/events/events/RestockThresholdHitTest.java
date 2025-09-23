package application.events.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestockThresholdHitTest {

    @Test
    @DisplayName("RestockThresholdHit event creation")
    void restock_event_creation() {
        String itemCode = "ITEM001";
        int currentStock = 5;
        int thresholdLevel = 20;

        RestockThresholdHit event = new RestockThresholdHit(itemCode, currentStock, thresholdLevel);

        assertEquals(itemCode, event.itemCode());
        assertEquals(currentStock, event.totalQtyLeft()); // Using correct field name
        assertEquals(thresholdLevel, event.threshold()); // Using correct field name
    }

    @Test
    @DisplayName("RestockThresholdHit event with zero current stock")
    void restock_event_zero_current_stock() {
        RestockThresholdHit event = new RestockThresholdHit("ITEM002", 0, 15);

        assertEquals(0, event.totalQtyLeft()); // Using correct field name
        assertEquals(15, event.threshold()); // Using correct field name
    }

    @Test
    @DisplayName("RestockThresholdHit event with high threshold")
    void restock_event_high_threshold() {
        RestockThresholdHit event = new RestockThresholdHit("ITEM003", 100, 500);

        assertEquals(100, event.totalQtyLeft()); // Using correct field name
        assertEquals(500, event.threshold()); // Using correct field name
    }

    @Test
    @DisplayName("RestockThresholdHit event basic properties")
    void restock_event_basic_properties() {
        // Removed timestamp test since timestamp() method doesn't exist
        RestockThresholdHit event = new RestockThresholdHit("ITEM004", 8, 25);

        assertEquals("ITEM004", event.itemCode());
        assertEquals(8, event.totalQtyLeft());
        assertEquals(25, event.threshold());
    }

    @Test
    @DisplayName("RestockThresholdHit event priority calculation - removed since getPriority method doesn't exist")
    void restock_event_priority() {
        // Test basic creation instead since getPriority doesn't exist
        RestockThresholdHit urgentEvent = new RestockThresholdHit("URGENT001", 1, 50);
        RestockThresholdHit normalEvent = new RestockThresholdHit("NORMAL001", 10, 20);

        assertEquals(1, urgentEvent.totalQtyLeft());
        assertEquals(10, normalEvent.totalQtyLeft());
    }

    @Test
    @DisplayName("RestockThresholdHit event shortage calculation - removed since calculateShortage method doesn't exist")
    void restock_event_shortage_calculation() {
        // Test basic shortage calculation manually since method doesn't exist
        RestockThresholdHit event = new RestockThresholdHit("ITEM005", 5, 25);

        assertEquals(5, event.totalQtyLeft());
        int shortage = event.threshold() - event.totalQtyLeft(); // Manual calculation
        assertEquals(20, shortage);
    }

    @Test
    @DisplayName("RestockThresholdHit event for item with no stock shortage")
    void restock_event_no_shortage() {
        RestockThresholdHit event = new RestockThresholdHit("ITEM006", 30, 25);

        assertEquals(30, event.totalQtyLeft());
        int shortage = Math.max(0, event.threshold() - event.totalQtyLeft()); // Manual calculation
        assertEquals(0, shortage);
    }

    @Test
    @DisplayName("RestockThresholdHit event equality")
    void restock_event_equality() {
        RestockThresholdHit event1 = new RestockThresholdHit("ITEM007", 15, 40);
        RestockThresholdHit event2 = new RestockThresholdHit("ITEM007", 15, 40);
        RestockThresholdHit event3 = new RestockThresholdHit("ITEM008", 15, 40);

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
    }

    @Test
    @DisplayName("RestockThresholdHit event string representation")
    void restock_event_string_representation() {
        RestockThresholdHit event = new RestockThresholdHit("ITEM009", 8, 30);

        String eventString = event.toString();
        assertTrue(eventString.contains("ITEM009"));
        assertTrue(eventString.contains("8"));
        assertTrue(eventString.contains("30"));
    }

    @Test
    @DisplayName("RestockThresholdHit event severity levels - modified since getSeverity method doesn't exist")
    void restock_event_severity_levels() {
        // Manual severity calculation since getSeverity doesn't exist
        RestockThresholdHit criticalEvent = new RestockThresholdHit("CRITICAL001", 1, 100);
        RestockThresholdHit moderateEvent = new RestockThresholdHit("MODERATE001", 25, 50);
        RestockThresholdHit minorEvent = new RestockThresholdHit("MINOR001", 40, 50);

        // Calculate severity manually based on percentage
        double criticalSeverity = (double) criticalEvent.totalQtyLeft() / criticalEvent.threshold();
        double moderateSeverity = (double) moderateEvent.totalQtyLeft() / moderateEvent.threshold();
        double minorSeverity = (double) minorEvent.totalQtyLeft() / minorEvent.threshold();

        assertTrue(criticalSeverity < moderateSeverity);
        assertTrue(moderateSeverity < minorSeverity);
    }

    @Test
    @DisplayName("RestockThresholdHit event notification message - removed since getNotificationMessage method doesn't exist")
    void restock_event_notification_message() {
        // Test basic properties instead since getNotificationMessage doesn't exist
        RestockThresholdHit event = new RestockThresholdHit("ITEM010", 5, 50);

        assertEquals("ITEM010", event.itemCode());
        assertEquals(5, event.totalQtyLeft());
        assertEquals(50, event.threshold());
    }
}
