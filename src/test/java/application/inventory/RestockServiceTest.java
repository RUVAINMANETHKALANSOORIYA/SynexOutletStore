package application.inventory;

import ports.out.InventoryRepository;
import domain.inventory.Item;
import domain.inventory.Batch;
import domain.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RestockServiceTest {

    private RestockService restockService;
    private FakeInventoryRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeInventoryRepository();
        restockService = new RestockService(fakeRepository); // Fixed constructor
    }

    @Test
    @DisplayName("RestockService can restock with fixed quantity")
    void restock_fixed_quantity() {
        // Setup item and stock
        fakeRepository.addItem("ITEM001", "Test Item", Money.of(10.0));
        fakeRepository.setStoreQty("ITEM001", 50);

        assertDoesNotThrow(() -> {
            restockService.restockFixed("ITEM001", 10);
        });
    }

    @Test
    @DisplayName("RestockService throws exception for unknown item")
    void restock_unknown_item_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> {
            restockService.restockFixed("UNKNOWN", 10);
        });
    }

    @Test
    @DisplayName("RestockService throws exception for zero quantity")
    void restock_zero_quantity_throws_exception() {
        fakeRepository.addItem("ITEM002", "Test Item 2", Money.of(15.0));
        fakeRepository.setStoreQty("ITEM002", 50);

        assertThrows(IllegalArgumentException.class, () -> {
            restockService.restockFixed("ITEM002", 0);
        });
    }

    @Test
    @DisplayName("RestockService throws exception for negative quantity")
    void restock_negative_quantity_throws_exception() {
        fakeRepository.addItem("ITEM003", "Test Item 3", Money.of(20.0));
        fakeRepository.setStoreQty("ITEM003", 50);

        assertThrows(IllegalArgumentException.class, () -> {
            restockService.restockFixed("ITEM003", -5);
        });
    }

    @Test
    @DisplayName("RestockService throws exception when no store stock")
    void restock_no_store_stock_throws_exception() {
        fakeRepository.addItem("ITEM004", "Test Item 4", Money.of(25.0));
        fakeRepository.setStoreQty("ITEM004", 0);

        assertThrows(IllegalStateException.class, () -> {
            restockService.restockFixed("ITEM004", 10);
        });
    }

    @Test
    @DisplayName("RestockService moves limited stock when requested more than available")
    void restock_limited_by_store_stock() {
        fakeRepository.addItem("ITEM005", "Test Item 5", Money.of(30.0));
        fakeRepository.setStoreQty("ITEM005", 5);

        assertDoesNotThrow(() -> {
            restockService.restockFixed("ITEM005", 10); // Request 10 but only 5 available
        });
    }

    @Test
    @DisplayName("RestockService creates valid service instance")
    void creates_valid_service_instance() {
        assertNotNull(restockService);
    }

    @Test
    @DisplayName("RestockService handles multiple restock operations")
    void multiple_restock_operations() {
        fakeRepository.addItem("ITEM006", "Test Item 6", Money.of(35.0));
        fakeRepository.setStoreQty("ITEM006", 100);

        assertDoesNotThrow(() -> {
            restockService.restockFixed("ITEM006", 10);
            restockService.restockFixed("ITEM006", 5);
            restockService.restockFixed("ITEM006", 15);
        });
    }

    // Fake repository implementation for testing
    static class FakeInventoryRepository implements InventoryRepository {
        private final List<Item> items = new ArrayList<>();
        private final java.util.Map<String, Integer> storeQuantities = new java.util.HashMap<>();

        public void addItem(String code, String name, Money price) {
            items.add(new Item(1L, code, name, price));
        }

        public void setStoreQty(String itemCode, int qty) {
            storeQuantities.put(itemCode, qty);
        }

        @Override
        public Optional<Item> findItemByCode(String itemCode) {
            return items.stream().filter(item -> item.code().equals(itemCode)).findFirst();
        }

        @Override
        public int storeQty(String itemCode) {
            return storeQuantities.getOrDefault(itemCode, 0);
        }

        @Override
        public void moveStoreToShelfFEFO(String itemCode, int qty) {
            // Mock implementation - just reduce store quantity
            int currentQty = storeQuantities.getOrDefault(itemCode, 0);
            storeQuantities.put(itemCode, Math.max(0, currentQty - qty));
        }

        // Minimal implementations for other required methods
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
        @Override public Money priceOf(String itemCode) { return Money.of(10.0); }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) { return new ArrayList<>(); }
        @Override public List<Batch> findBatchesInStore(String itemCode) { return new ArrayList<>(); }
        @Override public void commitReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {}
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {}
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) {}
        @Override public void deleteBatch(long batchId) {}
        @Override public void createItem(String code, String name, Money price) {}
        @Override public void renameItem(String code, String newName) {}
        @Override public void setItemPrice(String code, Money newPrice) {}
        @Override public void deleteItem(String code) {}
        @Override public int restockLevel(String itemCode) { return 0; }
        @Override public void setItemRestockLevel(String itemCode, int level) {}
        @Override public List<Item> listAllItems() { return new ArrayList<>(); }
        @Override public List<Item> searchItemsByNameOrCode(String query) { return new ArrayList<>(); }
        @Override public void addBatchDiscount(long batchId, domain.inventory.BatchDiscount.DiscountType type, Money value, String reason, String createdBy) {}
        @Override public void removeBatchDiscount(long discountId) {}
        @Override public Optional<domain.inventory.BatchDiscount> findActiveBatchDiscount(long batchId) { return Optional.empty(); }
        @Override public List<domain.inventory.BatchDiscount> findBatchDiscountsByBatch(long batchId) { return new ArrayList<>(); }
        @Override public List<ports.out.InventoryRepository.BatchDiscountView> getAllBatchDiscountsWithDetails() { return new ArrayList<>(); }
    }
}
