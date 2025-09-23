package application.inventory;

import domain.inventory.Batch;
import domain.inventory.Item;
import domain.inventory.InventoryReservation;
import domain.common.Money;
import ports.out.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FefoBatchSelectorTest {

    private FefoBatchSelector selector;
    private FakeInventoryRepository fakeRepo;

    @BeforeEach
    void setUp() {
        selector = new FefoBatchSelector();
        fakeRepo = new FakeInventoryRepository();
    }

    @Test
    @DisplayName("Select batches with sufficient quantity")
    void select_batches_sufficient_quantity() {
        // Setup batches with different expiry dates
        fakeRepo.addBatch(1L, "ITEM001", LocalDate.now().plusDays(5), 10, 0);
        fakeRepo.addBatch(2L, "ITEM001", LocalDate.now().plusDays(10), 15, 0);

        List<InventoryReservation> reservations = selector.selectFor("ITEM001", 8, fakeRepo);

        assertEquals(1, reservations.size());
        assertEquals(1L, reservations.get(0).batchId); // Using field access instead of method
        assertEquals(8, reservations.get(0).quantity); // Using field access instead of method
    }

    @Test
    @DisplayName("Select from multiple batches when needed")
    void select_from_multiple_batches() {
        // Setup batches
        fakeRepo.addBatch(1L, "ITEM002", LocalDate.now().plusDays(3), 5, 0);
        fakeRepo.addBatch(2L, "ITEM002", LocalDate.now().plusDays(7), 10, 0);

        List<InventoryReservation> reservations = selector.selectFor("ITEM002", 12, fakeRepo);

        assertEquals(2, reservations.size());
        assertEquals(1L, reservations.get(0).batchId); // Using field access
        assertEquals(5, reservations.get(0).quantity); // Using field access
        assertEquals(2L, reservations.get(1).batchId); // Using field access
        assertEquals(7, reservations.get(1).quantity); // Using field access
    }

    @Test
    @DisplayName("Select exactly the requested quantity")
    void select_exact_quantity() {
        fakeRepo.addBatch(1L, "ITEM003", LocalDate.now().plusDays(5), 20, 0);

        List<InventoryReservation> reservations = selector.selectFor("ITEM003", 20, fakeRepo);

        assertEquals(1, reservations.size());
        assertEquals(20, reservations.get(0).quantity); // Using field access
    }

    @Test
    @DisplayName("Throw exception when insufficient quantity")
    void insufficient_quantity_throws_exception() {
        fakeRepo.addBatch(1L, "ITEM004", LocalDate.now().plusDays(5), 5, 0);

        assertThrows(IllegalStateException.class, () -> {
            selector.selectFor("ITEM004", 10, fakeRepo);
        });
    }

    @Test
    @DisplayName("Throw exception for non-existent item")
    void non_existent_item_throws_exception() {
        assertThrows(java.util.NoSuchElementException.class, () -> {
            selector.selectFor("NONEXISTENT", 5, fakeRepo);
        });
    }

    @Test
    @DisplayName("Select with zero quantity throws exception")
    void zero_quantity_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> {
            selector.selectFor("ITEM001", 0, fakeRepo);
        });
    }

    @Test
    @DisplayName("Select with negative quantity throws exception")
    void negative_quantity_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> {
            selector.selectFor("ITEM001", -5, fakeRepo);
        });
    }

    @Test
    @DisplayName("Select from batches in FEFO order")
    void select_fefo_order() {
        // Add batches with different expiry dates (not in FEFO order)
        fakeRepo.addBatch(1L, "ITEM005", LocalDate.now().plusDays(10), 8, 0);
        fakeRepo.addBatch(2L, "ITEM005", LocalDate.now().plusDays(2), 5, 0);
        fakeRepo.addBatch(3L, "ITEM005", LocalDate.now().plusDays(5), 7, 0);

        List<InventoryReservation> reservations = selector.selectFor("ITEM005", 10, fakeRepo);

        // Should select from earliest expiry first (batch 2, then batch 3)
        assertEquals(2, reservations.size());
        assertEquals(2L, reservations.get(0).batchId); // Using field access
        assertEquals(5, reservations.get(0).quantity); // Using field access
        assertEquals(3L, reservations.get(1).batchId); // Using field access
        assertEquals(5, reservations.get(1).quantity); // Using field access
    }

    @Test
    @DisplayName("Select handles empty batches gracefully")
    void select_handles_empty_batches() {
        fakeRepo.addBatch(1L, "ITEM006", LocalDate.now().plusDays(5), 0, 0); // Empty batch
        fakeRepo.addBatch(2L, "ITEM006", LocalDate.now().plusDays(10), 15, 0);

        List<InventoryReservation> reservations = selector.selectFor("ITEM006", 8, fakeRepo);

        assertEquals(1, reservations.size());
        assertEquals(2L, reservations.get(0).batchId); // Using field access
        assertEquals(8, reservations.get(0).quantity); // Using field access
    }

    @Test
    @DisplayName("Select all available quantity when requested more than available")
    void select_all_available_when_insufficient() {
        fakeRepo.addBatch(1L, "ITEM007", LocalDate.now().plusDays(5), 3, 0);
        fakeRepo.addBatch(2L, "ITEM007", LocalDate.now().plusDays(10), 4, 0);

        // Total available is 7, but requesting 10
        assertThrows(IllegalStateException.class, () -> {
            selector.selectFor("ITEM007", 10, fakeRepo);
        });
    }

    // Fake repository implementation for testing
    static class FakeInventoryRepository implements InventoryRepository {
        private final List<Batch> batches = new ArrayList<>();
        private final List<Item> items = new ArrayList<>();

        public void addBatch(long id, String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {
            // Add item if it doesn't exist
            if (items.stream().noneMatch(item -> item.code().equals(itemCode))) {
                items.add(new Item(1L, itemCode, "Test Item " + itemCode, Money.of(10.0))); // Fixed constructor with id parameter
            }

            batches.add(new Batch(id, itemCode, expiry, qtyShelf, qtyStore));
        }

        @Override
        public Optional<Item> findItemByCode(String itemCode) {
            return items.stream().filter(item -> item.code().equals(itemCode)).findFirst();
        }

        @Override
        public List<Batch> findBatchesOnShelf(String itemCode) {
            return batches.stream()
                    .filter(batch -> batch.itemCode().equals(itemCode))
                    .filter(batch -> batch.qtyOnShelf() > 0)
                    .sorted((b1, b2) -> b1.expiryDate().compareTo(b2.expiryDate())) // Fixed method name
                    .toList();
        }

        // Minimal implementations for other required methods
        @Override public Money priceOf(String itemCode) { return Money.of(10.0); }
        @Override public List<Batch> findBatchesInStore(String itemCode) { return new ArrayList<>(); }
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) {}
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
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
