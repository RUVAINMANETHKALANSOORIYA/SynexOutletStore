package application.events;

import application.events.events.*;
import domain.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EventBusImplTest {

    private SimpleEventBus eventBus;
    private List<Object> capturedEvents;
    private AtomicInteger callCount;

    @BeforeEach
    void setup() {
        eventBus = new SimpleEventBus();
        capturedEvents = new ArrayList<>();
        callCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Subscribe and publish BillPaid event")
    void subscribe_and_publish_bill_paid() {
        // Subscribe to BillPaid events
        eventBus.subscribe(BillPaid.class, event -> {
            capturedEvents.add(event);
            callCount.incrementAndGet();
        });

        // Publish a BillPaid event
        BillPaid event = new BillPaid("POS-001", Money.of(150.0), "POS", "john");
        eventBus.publish(event);

        // Verify event was received
        assertEquals(1, capturedEvents.size());
        assertEquals(1, callCount.get());
        assertTrue(capturedEvents.get(0) instanceof BillPaid);

        BillPaid receivedEvent = (BillPaid) capturedEvents.get(0);
        assertEquals("POS-001", receivedEvent.billNo());
        assertEquals("POS", receivedEvent.channel());
        assertEquals(Money.of(150.0), receivedEvent.total());
        assertEquals("john", receivedEvent.user());
    }

    @Test
    @DisplayName("Subscribe and publish StockDepleted event")
    void subscribe_and_publish_stock_depleted() {
        eventBus.subscribe(StockDepleted.class, event -> {
            capturedEvents.add(event);
            callCount.incrementAndGet();
        });

        StockDepleted event = new StockDepleted("ITEM001");
        eventBus.publish(event);

        assertEquals(1, capturedEvents.size());
        assertEquals(1, callCount.get());

        StockDepleted receivedEvent = (StockDepleted) capturedEvents.get(0);
        assertEquals("ITEM001", receivedEvent.itemCode());
    }

    @Test
    @DisplayName("Subscribe and publish RestockThresholdHit event")
    void subscribe_and_publish_restock_threshold() {
        eventBus.subscribe(RestockThresholdHit.class, event -> {
            capturedEvents.add(event);
            callCount.incrementAndGet();
        });

        RestockThresholdHit event = new RestockThresholdHit("ITEM002", 5, 10);
        eventBus.publish(event);

        assertEquals(1, capturedEvents.size());
        RestockThresholdHit receivedEvent = (RestockThresholdHit) capturedEvents.get(0);
        assertEquals("ITEM002", receivedEvent.itemCode());
        assertEquals(5, receivedEvent.totalQtyLeft());
        assertEquals(10, receivedEvent.threshold());
    }

    @Test
    @DisplayName("Multiple subscribers receive same event")
    void multiple_subscribers_same_event() {
        AtomicInteger subscriber1Count = new AtomicInteger(0);
        AtomicInteger subscriber2Count = new AtomicInteger(0);

        // Subscribe with two different handlers
        eventBus.subscribe(BillPaid.class, event -> subscriber1Count.incrementAndGet());
        eventBus.subscribe(BillPaid.class, event -> subscriber2Count.incrementAndGet());

        // Publish one event
        BillPaid event = new BillPaid("POS-002", Money.of(200.0), "POS", "jane");
        eventBus.publish(event);

        // Both subscribers should receive the event
        assertEquals(1, subscriber1Count.get());
        assertEquals(1, subscriber2Count.get());
    }

    @Test
    @DisplayName("Different event types go to correct subscribers")
    void different_event_types_correct_subscribers() {
        List<BillPaid> billPaidEvents = new ArrayList<>();
        List<StockDepleted> stockEvents = new ArrayList<>();

        eventBus.subscribe(BillPaid.class, billPaidEvents::add);
        eventBus.subscribe(StockDepleted.class, stockEvents::add);

        // Publish different types of events
        eventBus.publish(new BillPaid("POS-003", Money.of(100.0), "POS", "user1"));
        eventBus.publish(new StockDepleted("ITEM003"));
        eventBus.publish(new BillPaid("POS-004", Money.of(250.0), "POS", "user2"));

        // Verify correct distribution
        assertEquals(2, billPaidEvents.size());
        assertEquals(1, stockEvents.size());

        assertEquals("POS-003", billPaidEvents.get(0).billNo());
        assertEquals("POS-004", billPaidEvents.get(1).billNo());
        assertEquals("ITEM003", stockEvents.get(0).itemCode());
    }

    @Test
    @DisplayName("No subscribers for event type doesn't cause errors")
    void no_subscribers_no_errors() {
        // Publish event with no subscribers
        assertDoesNotThrow(() -> {
            eventBus.publish(new BillPaid("POS-005", Money.of(75.0), "POS", "test"));
            eventBus.publish(new StockDepleted("ITEM004"));
        });

        assertEquals(0, capturedEvents.size());
        assertEquals(0, callCount.get());
    }

    @Test
    @DisplayName("Exception in subscriber doesn't affect other subscribers")
    void exception_in_subscriber_isolation() {
        List<BillPaid> successfulEvents = new ArrayList<>();

        // Subscriber that throws exception
        eventBus.subscribe(BillPaid.class, event -> {
            throw new RuntimeException("Test exception");
        });

        // Subscriber that works normally
        eventBus.subscribe(BillPaid.class, successfulEvents::add);

        // Publish event - should not throw exception
        assertDoesNotThrow(() -> {
            eventBus.publish(new BillPaid("POS-006", Money.of(300.0), "POS", "test"));
        });

        // Successful subscriber should still receive event
        assertEquals(1, successfulEvents.size());
        assertEquals("POS-006", successfulEvents.get(0).billNo());
    }

    @Test
    @DisplayName("Null event handling")
    void null_event_handling() {
        eventBus.subscribe(BillPaid.class, event -> capturedEvents.add(event));

        // Publishing null should not cause errors
        assertDoesNotThrow(() -> eventBus.publish(null));

        assertEquals(0, capturedEvents.size());
    }

    @Test
    @DisplayName("Subscriber with null handler")
    void null_handler_subscription() {
        // Subscribing with null handler should not cause errors
        assertDoesNotThrow(() -> {
            Consumer<BillPaid> nullHandler = null;
            eventBus.subscribe(BillPaid.class, nullHandler);
        });

        // Publishing should still work
        assertDoesNotThrow(() -> {
            eventBus.publish(new BillPaid("POS-007", Money.of(50.0), "POS", "test"));
        });
    }
}
