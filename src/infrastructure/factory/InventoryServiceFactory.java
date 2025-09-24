package infrastructure.factory;

import ports.in.InventoryService;
import application.inventory.FefoBatchSelector;
import infrastructure.jdbc.JdbcInventoryRepository;

/**
 * Factory for creating inventory services
 */
public class InventoryServiceFactory implements ServiceFactory<InventoryService> {

    @Override
    public InventoryService create(String type, Object... params) {
        switch (type.toLowerCase()) {
            case "jdbc":
                // Use existing JDBC repository with FEFO selector
                return new InventoryService(new JdbcInventoryRepository(), new FefoBatchSelector());
            case "default":
            default:
                // Default implementation using JDBC repository
                return new InventoryService(new JdbcInventoryRepository(), new FefoBatchSelector());
        }
    }

    @Override
    public boolean supports(String type) {
        return "jdbc".equalsIgnoreCase(type) || "default".equalsIgnoreCase(type);
    }
}
