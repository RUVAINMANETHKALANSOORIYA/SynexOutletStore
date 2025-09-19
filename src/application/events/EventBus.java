package application.events;

public interface EventBus {
    void publish(Object event);
    <T> void subscribe(Class<T> type, java.util.function.Consumer<T> handler);
}
