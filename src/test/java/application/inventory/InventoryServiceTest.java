package application.inventory;

import domain.common.Money;
import domain.inventory.Item;
import domain.inventory.InventoryReservation;
import ports.out.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryService inventoryService;
    private FakeInventoryRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeInventoryRepository();
        FefoBatchSelector batchSelector = new FefoBatchSelector();
        inventoryService = new InventoryService(fakeRepository, batchSelector); // Fixed to use non-deprecated constructor
    }

    @Test
    @DisplayName("InventoryService can get item name")
    void get_item_name() {
        fakeRepository.addTestItem(new Item(1L, "ITEM001", "Test Item", Money.of(10.0)));

        String name = inventoryService.itemName("ITEM001"); // Fixed method name
        assertEquals("Test Item", name);
    }

    @Test
    @DisplayName("InventoryService throws exception for non-existent item")
    void throw_exception_nonexistent_item() {
        assertThrows(java.util.NoSuchElementException.class, () -> {
            inventoryService.itemName("NONEXISTENT"); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryService can get item price")
    void get_item_price() {
        fakeRepository.addTestItem(new Item(1L, "ITEM002", "Test Item 2", Money.of(15.0)));

        Money price = inventoryService.priceOf("ITEM002");
        assertEquals(Money.of(15.0), price);
    }

    @Test
    @DisplayName("InventoryService can reserve from shelf FEFO")
    void reserve_from_shelf_fefo() {
        fakeRepository.addTestItem(new Item(1L, "ITEM003", "Test Item 3", Money.of(20.0)));
        fakeRepository.setShelfQty("ITEM003", 10);

        List<InventoryReservation> reservations = inventoryService.reserveFromShelfFEFO("ITEM003", 5); // Fixed method name
        assertNotNull(reservations);
    }

    @Test
    @DisplayName("InventoryService can reserve from store FEFO")
    void reserve_from_store_fefo() {
        fakeRepository.addTestItem(new Item(1L, "ITEM004", "Test Item 4", Money.of(25.0)));
        fakeRepository.setStoreQty("ITEM004", 15);

        List<InventoryReservation> reservations = inventoryService.reserveFromStoreFEFO("ITEM004", 8); // Fixed method name
        assertNotNull(reservations);
    }

    @Test
    @DisplayName("InventoryService can commit reservations")
    void commit_reservations() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM005", 3)
        );

        assertDoesNotThrow(() -> {
            inventoryService.commitReservation(reservations); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryService can commit store reservations")
    void commit_store_reservations() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM006", 5)
        );

        assertDoesNotThrow(() -> {
            inventoryService.commitStoreReservation(reservations); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryService can move main to store with user")
    void move_main_to_store_with_user() {
        assertDoesNotThrow(() -> {
            inventoryService.moveMainToStoreFEFOWithUser("ITEM007", 10, "admin");
        });
    }

    @Test
    @DisplayName("InventoryService creates valid service instance")
    void creates_valid_service_instance() {
        assertNotNull(inventoryService);
    }

    // Fake repository implementation for testing
    static class FakeInventoryRepository implements InventoryRepository {
        private final List<Item> items = new ArrayList<>();
        private final java.util.Map<String, Integer> shelfQuantities = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> storeQuantities = new java.util.HashMap<>();

        public void addTestItem(Item item) {
            items.add(item);
        }

        public void setShelfQty(String itemCode, int qty) {
            shelfQuantities.put(itemCode, qty);
        }

        public void setStoreQty(String itemCode, int qty) {
            storeQuantities.put(itemCode, qty);
        }

        @Override
        public Optional<Item> findItemByCode(String itemCode) {
            return items.stream().filter(item -> item.code().equals(itemCode)).findFirst();
        }

        @Override
        public Money priceOf(String itemCode) {
            return items.stream()
                    .filter(item -> item.code().equals(itemCode))
                    .map(Item::unitPrice)
                    .findFirst()
                    .orElse(Money.of(0.0));
        }

        @Override
        public int shelfQty(String itemCode) {
            return shelfQuantities.getOrDefault(itemCode, 0);
        }

        @Override
        public int storeQty(String itemCode) {
            return storeQuantities.getOrDefault(itemCode, 0);
        }

        @Override
        public List<domain.inventory.Batch> findBatchesOnShelf(String itemCode) {
            return List.of(new domain.inventory.Batch(1L, itemCode, LocalDate.now().plusDays(30),
                                                    shelfQuantities.getOrDefault(itemCode, 0), 0));
        }

        @Override
        public List<domain.inventory.Batch> findBatchesInStore(String itemCode) {
            return List.of(new domain.inventory.Batch(2L, itemCode, LocalDate.now().plusDays(30),
                                                     0, storeQuantities.getOrDefault(itemCode, 0)));
        }

        // Minimal implementations for other required methods
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {}
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) {}
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
