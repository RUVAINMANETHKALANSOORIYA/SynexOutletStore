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
            @SuppressWarnings("unchecked")
            Consumer<Object> c = (Consumer<Object>) h;
            c.accept(event);
        }
    }

    @Override
    public synchronized <T> void subscribe(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }
}
