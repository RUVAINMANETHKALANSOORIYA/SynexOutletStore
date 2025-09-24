package infrastructure.factory;

import application.pricing.PricingService;
import ports.in.InventoryService;

/**
 * Factory for creating pricing services
 */
public class PricingServiceFactory implements ServiceFactory<PricingService> {

    @Override
    public PricingService create(String type, Object... params) {
        switch (type.toLowerCase()) {
            case "default":
                if (params.length >= 2 && params[0] instanceof Double && params[1] instanceof InventoryService) {
                    return new PricingService((Double) params[0], (InventoryService) params[1]);
                } else if (params.length >= 1 && params[0] instanceof Double) {
                    return new PricingService((Double) params[0]);
                } else {
                    // Default tax rate of 15% if no parameters provided
                    return new PricingService(15.0);
                }
            default:
                // Default implementation with 15% tax rate
                if (params.length >= 2 && params[0] instanceof Double && params[1] instanceof InventoryService) {
                    return new PricingService((Double) params[0], (InventoryService) params[1]);
                } else if (params.length >= 1 && params[0] instanceof Double) {
                    return new PricingService((Double) params[0]);
                } else {
                    return new PricingService(15.0);
                }
        }
    }

    @Override
    public boolean supports(String type) {
        return "default".equalsIgnoreCase(type);
    }
}
