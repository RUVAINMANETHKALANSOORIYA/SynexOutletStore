package application.inventory;

    import domain.common.Money;
import domain.inventory.Item;
import ports.out.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InventoryAdminServiceTest {

    private InventoryAdminService inventoryAdminService;
    private FakeInventoryRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeInventoryRepository();
        inventoryAdminService = new InventoryAdminService(fakeRepository); // Fixed constructor
    }

    @Test
    @DisplayName("InventoryAdminService can add new item")
    void add_new_item() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.addNewItem("ITEM001", "Test Item", Money.of(10.0)); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can get item")
    void get_item() {
        fakeRepository.addTestItem(new Item(1L, "ITEM001", "Test Item", Money.of(10.0))); // Fixed constructor

        Optional<Item> item = inventoryAdminService.getItem("ITEM001");
        assertTrue(item.isPresent());
        assertEquals("ITEM001", item.get().code());
        assertEquals(Money.of(10.0), item.get().unitPrice()); // Fixed method name
    }

    @Test
    @DisplayName("InventoryAdminService throws exception for invalid item data")
    void throw_exception_invalid_item_data() {
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryAdminService.addNewItem("", "Test Item", Money.of(10.0)); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can update item price")
    void update_item_price() {
        fakeRepository.addTestItem(new Item(1L, "ITEM002", "Test Item 2", Money.of(15.0))); // Fixed constructor

        assertDoesNotThrow(() -> {
            inventoryAdminService.setItemPrice("ITEM002", Money.of(20.0)); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService throws exception for invalid price")
    void throw_exception_invalid_price() {
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryAdminService.setItemPrice("ITEM002", Money.of(-5.0)); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can list all items")
    void list_all_items() {
        List<Item> items = inventoryAdminService.listItems();
        assertNotNull(items);
    }

    @Test
    @DisplayName("InventoryAdminService can search items")
    void search_items() {
        List<Item> items = inventoryAdminService.searchItems("test");
        assertNotNull(items);
    }

    @Test
    @DisplayName("InventoryAdminService can rename item")
    void rename_item() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.renameItem("ITEM001", "New Name");
        });
    }

    @Test
    @DisplayName("InventoryAdminService can set restock level")
    void set_restock_level() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.setRestockLevel("ITEM001", 25); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService throws exception for invalid restock level")
    void throw_exception_invalid_restock_level() {
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryAdminService.setRestockLevel("ITEM001", -1); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can add batch")
    void add_batch() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.addBatch("ITEM001", LocalDate.now().plusDays(30), 10, 20);
        });
    }

    @Test
    @DisplayName("InventoryAdminService can move items from store to shelf")
    void move_store_to_shelf() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.moveStoreToShelfFEFO("ITEM001", 5); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can move items from main to shelf")
    void move_main_to_shelf() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.moveMainToShelfFEFO("ITEM001", 10); // Fixed method name
        });
    }

    @Test
    @DisplayName("InventoryAdminService can delete item")
    void delete_item() {
        assertDoesNotThrow(() -> {
            inventoryAdminService.deleteItem("ITEM001");
        });
    }

    // Fake repository implementation for testing
    static class FakeInventoryRepository implements InventoryRepository {
        private final List<Item> items = new ArrayList<>();

        public void addTestItem(Item item) {
            items.add(item);
        }

        @Override
        public Optional<Item> findItemByCode(String itemCode) {
            return items.stream().filter(item -> item.code().equals(itemCode)).findFirst();
        }

        @Override
        public List<Item> listAllItems() {
            return new ArrayList<>(items);
        }

        @Override
        public List<Item> searchItemsByNameOrCode(String query) {
            return items.stream()
                    .filter(item -> item.name().toLowerCase().contains(query.toLowerCase()) || 
                                   item.code().toLowerCase().contains(query.toLowerCase()))
                    .toList();
        }

        // Minimal implementations for other required methods
        @Override public void createItem(String code, String name, domain.common.Money price) {}
        @Override public void renameItem(String code, String newName) {}
        @Override public void setItemPrice(String code, domain.common.Money newPrice) {}
        @Override public void deleteItem(String code) {}
        @Override public void setItemRestockLevel(String itemCode, int level) {}
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {}
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {}
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) {}
        @Override public void deleteBatch(long batchId) {}
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
        @Override public domain.common.Money priceOf(String itemCode) { return domain.common.Money.of(10.0); }
        @Override public List<domain.inventory.Batch> findBatchesOnShelf(String itemCode) { return new ArrayList<>(); }
        @Override public List<domain.inventory.Batch> findBatchesInStore(String itemCode) { return new ArrayList<>(); }
        @Override public void commitReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public int restockLevel(String itemCode) { return 0; }
        @Override public void addBatchDiscount(long batchId, domain.inventory.BatchDiscount.DiscountType type, domain.common.Money value, String reason, String createdBy) {}
        @Override public void removeBatchDiscount(long discountId) {}
        @Override public Optional<domain.inventory.BatchDiscount> findActiveBatchDiscount(long batchId) { return Optional.empty(); }
        @Override public List<domain.inventory.BatchDiscount> findBatchDiscountsByBatch(long batchId) { return new ArrayList<>(); }
        @Override public List<ports.out.InventoryRepository.BatchDiscountView> getAllBatchDiscountsWithDetails() { return new ArrayList<>(); }
    }
}
