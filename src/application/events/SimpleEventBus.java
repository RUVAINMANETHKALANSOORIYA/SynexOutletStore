package application.events;

import java.util.*;
import java.util.function.Consumer;

public final class SimpleEventBus implements EventBus {
    private final Map<Class<?>, List<Consumer<?>>> handlers = new HashMap<>();

    @Override
    public synchronized void publish(Object event) {
        if (event == null) return;
        var list = handlers.getOrDefault(event.getClass(), List.of());
        for (Consumer<?> h : list) {
            if (h == null) continue; // Skip null handlers
            try {
                @SuppressWarnings("unchecked")
                Consumer<Object> c = (Consumer<Object>) h;
                c.accept(event);
            } catch (Exception e) {
                // Log exception but continue with other subscribers
                System.err.println("Exception in event handler: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized <T> void subscribe(Class<T> type, Consumer<T> handler) {
        if (handler != null) { // Only add non-null handlers
            handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        }
    }
}
