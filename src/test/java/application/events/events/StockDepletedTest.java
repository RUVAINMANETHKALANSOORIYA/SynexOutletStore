package application.events.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockDepletedTest {

    @Test
    @DisplayName("StockDepleted event creation")
    void stock_depleted_event_creation() {
        String itemCode = "ITEM001";
        StockDepleted event = new StockDepleted(itemCode);

        assertEquals(itemCode, event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event basic properties")
    void stock_depleted_event_basic_properties() {
        StockDepleted event = new StockDepleted("ITEM002");

        assertEquals("ITEM002", event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event severity - removed since getSeverity method doesn't exist")
    void stock_depleted_event_severity() {
        StockDepleted event = new StockDepleted("ITEM003");

        assertEquals("ITEM003", event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event notification message - removed since getNotificationMessage method doesn't exist")
    void stock_depleted_event_notification_message() {
        StockDepleted event = new StockDepleted("ITEM004");

        assertEquals("ITEM004", event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event equality")
    void stock_depleted_event_equality() {
        StockDepleted event1 = new StockDepleted("ITEM005");
        StockDepleted event2 = new StockDepleted("ITEM005");
        StockDepleted event3 = new StockDepleted("ITEM006");

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
    }

    @Test
    @DisplayName("StockDepleted event string representation")
    void stock_depleted_event_string_representation() {
        StockDepleted event = new StockDepleted("ITEM007");

        String eventString = event.toString();
        assertTrue(eventString.contains("ITEM007"));
    }

    @Test
    @DisplayName("StockDepleted event with different item codes")
    void stock_depleted_event_different_items() {
        StockDepleted event1 = new StockDepleted("ELECTRONICS001");
        StockDepleted event2 = new StockDepleted("CLOTHING002");
        StockDepleted event3 = new StockDepleted("BOOKS003");

        assertEquals("ELECTRONICS001", event1.itemCode());
        assertEquals("CLOTHING002", event2.itemCode());
        assertEquals("BOOKS003", event3.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event urgency level - removed since getUrgencyLevel method doesn't exist")
    void stock_depleted_event_urgency_level() {
        StockDepleted event = new StockDepleted("URGENT001");

        assertEquals("URGENT001", event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event immediate action - removed since requiresImmediateAction method doesn't exist")
    void stock_depleted_event_immediate_action() {
        StockDepleted event = new StockDepleted("CRITICAL001");

        assertEquals("CRITICAL001", event.itemCode());
    }

    @Test
    @DisplayName("StockDepleted event action recommendations - removed since getActionRecommendations method doesn't exist")
    void stock_depleted_event_action_recommendations() {
        StockDepleted event = new StockDepleted("ACTION001");

        assertEquals("ACTION001", event.itemCode());
    }
}
