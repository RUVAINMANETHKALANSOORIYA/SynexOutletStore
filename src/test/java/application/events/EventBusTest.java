package application.events;

import application.events.events.BillPaid;
import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    @DisplayName("SimpleEventBus delivers events to subscribed handlers by type")
    void simple_bus_subscribe_and_publish() {
        SimpleEventBus bus = new SimpleEventBus();
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(BillPaid.class, e -> {
            assertEquals("B-1", e.billNo());
            assertEquals(Money.of(10.0), e.total());
            count.incrementAndGet();
        });
        bus.publish(new BillPaid("B-1", Money.of(10.0), "POS", "alice"));
        assertEquals(1, count.get());

        // Unsubscribed type should not affect count
        bus.publish("a string event");
        assertEquals(1, count.get());
    }

    @Test
    @DisplayName("NoopEventBus is safe to use and does nothing")
    void noop_bus_noops() {
        EventBus bus = new NoopEventBus();
        // Should not throw and should not call handler
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(BillPaid.class, e -> count.incrementAndGet());
        bus.publish(new BillPaid("B-2", Money.of(5.0), "POS", "bob"));
        assertEquals(0, count.get());
    }
}
