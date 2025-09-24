package infrastructure.factory;

import ports.in.InventoryService;
import application.pricing.PricingService;
import domain.billing.BillNumberGenerator;
import ports.out.BillRepository;
import domain.billing.BillWriter;
import application.events.EventBus;
import application.pos.POSController;

/**
 * Main factory for creating POS-related services and components
 */
public class POSServiceFactory {
    private final InventoryServiceFactory inventoryFactory = new InventoryServiceFactory();
    private final PricingServiceFactory pricingFactory = new PricingServiceFactory();

    public InventoryService createInventoryService(String type) {
        return inventoryFactory.create(type);
    }

    public PricingService createPricingService(String type, double taxPercent) {
        return pricingFactory.create(type, taxPercent);
    }

    public PricingService createPricingService(String type, double taxPercent, InventoryService inventoryService) {
        return pricingFactory.create(type, taxPercent, inventoryService);
    }

    public PricingService createPricingService(String type) {
        // Default tax rate of 15%
        return pricingFactory.create(type, 15.0);
    }

    public POSController createPOSController(String inventoryType, String pricingType,
                                           BillNumberGenerator billGen,
                                           BillRepository billRepo,
                                           BillWriter billWriter,
                                           EventBus eventBus) {
        InventoryService inventoryService = createInventoryService(inventoryType);
        PricingService pricingService = createPricingService(pricingType, 15.0, inventoryService);

        return new POSController(inventoryService, null, pricingService,
                               billGen, billRepo, billWriter, eventBus);
    }

    public POSController createPOSController(String inventoryType, String pricingType,
                                           BillNumberGenerator billGen,
                                           BillRepository billRepo,
                                           BillWriter billWriter) {
        return createPOSController(inventoryType, pricingType, billGen, billRepo, billWriter, null);
    }
}
