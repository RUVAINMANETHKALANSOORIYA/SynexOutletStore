package application.events;

import java.util.function.Consumer;

public final class NoopEventBus implements EventBus {
    @Override public void publish(Object event) { /* no-op */ }
    @Override public <T> void subscribe(Class<T> type, Consumer<T> handler) { /* no-op */ }
}
