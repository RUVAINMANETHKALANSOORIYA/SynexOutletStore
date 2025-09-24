package infrastructure.factory;

/**
 * Abstract factory interface for creating POS services
 */
public interface ServiceFactory<T> {
    T create(String type, Object... params);
    boolean supports(String type);
}
