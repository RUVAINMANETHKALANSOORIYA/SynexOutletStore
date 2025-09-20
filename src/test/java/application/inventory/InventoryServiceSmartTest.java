package application.inventory;

import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import domain.inventory.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.InventoryRepository;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceSmartTest {

    private FakeRepo repo;
    private InventoryService inv;

    @BeforeEach
    void setup() {
        repo = new FakeRepo();
        inv = new InventoryService(repo, new FefoBatchSelector());
        repo.setItem("SKU1", "Item1", 100.0, 5);
        repo.setQuantities("SKU1", 5, 2, 10); // shelf, store, main

        repo.setItem("SKU2", "Item2", 50.0, 3);
        repo.setQuantities("SKU2", 3, 0, 0);
    }

    @Test
    @DisplayName("POS: uses store first, then shelf with approval; triggers backfill to restock level")
    void pos_primary_store_then_shelf_with_backfill() {
        // Request 6: store has 2, shelf has 5, restock 5 -> secondaryAfter = 1 -> backfill to 5
        InventoryService.SmartPick pick = inv.reserveSmart("SKU1", 6, "POS", true, true);
        assertFalse(pick.shelfReservations.isEmpty());
        assertFalse(pick.storeReservations.isEmpty());
        assertFalse(pick.usedMainToFulfill); // fulfilled from existing secondary
        assertTrue(pick.backfilledSecondaryToRestockLevel); // manager approved backfill
        assertEquals(5, pick.restockLevel);
    }

    @Test
    @DisplayName("Approval required to use secondary when primary insufficient")
    void approval_required_for_secondary() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inv.reserveSmart("SKU1", 6, "POS", false, false));
        assertTrue(ex.getMessage().contains("Approval to use secondary"));
    }

    @Test
    @DisplayName("Manager approval required to pull from MAIN when secondary insufficient")
    void manager_approval_required_for_main() {
        // Make shelf too small so that after using existing shelf we still need MAIN
        repo.setQuantities("SKU1", 0, 2, 10); // shelf 0, store 2, main 10
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inv.reserveSmart("SKU1", 6, "POS", true, false));
        assertTrue(ex.getMessage().contains("Manager approval required"));
    }

    @Test
    @DisplayName("Insufficient MAIN triggers error")
    void insufficient_main_error() {
        repo.setQuantities("SKU1", 0, 2, 0);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inv.reserveSmart("SKU1", 6, "POS", true, true));
        assertTrue(ex.getMessage().contains("Insufficient quantity in MAIN"));
    }

    @Test
    @DisplayName("ONLINE: primary is shelf; out-of-stock flag when buying exactly restock level")
    void online_out_of_stock_flag() {
        // For ONLINE: primary shelf. Set shelf to restock level 3, request exactly 3 -> flag true.
        repo.setItem("SHELF", "S", 10.0, 3);
        repo.setQuantities("SHELF", 3, 0, 0);
        var pick = inv.reserveSmart("SHELF", 3, "ONLINE", true, false);
        assertTrue(pick.showOutOfStockMessage);
        assertEquals(3, pick.restockLevel);
    }

    // --- minimal fake repo ---
    static final class FakeRepo implements InventoryRepository {
        static final class State { Item item; Money price; int shelf, store, main, restock; List<Batch> shelfBatches = new ArrayList<>(), storeBatches = new ArrayList<>(); }
        private final Map<String, State> map = new HashMap<>();
        private long seq = 1;

        void setItem(String code, String name, double price, int restock) {
            State s = map.computeIfAbsent(code, k -> new State());
            s.item = new Item(seq++, code, name, Money.of(price), restock);
            s.price = Money.of(price);
            s.restock = restock;
            if (s.shelfBatches.isEmpty()) s.shelfBatches.add(new Batch(seq++, code, LocalDate.now().plusDays(10), 0, 0));
            if (s.storeBatches.isEmpty()) s.storeBatches.add(new Batch(seq++, code, LocalDate.now().plusDays(10), 0, 0));
        }
        void setQuantities(String code, int shelf, int store, int main){
            State s = map.get(code);
            s.shelf = shelf; s.store = store; s.main = main;
            s.shelfBatches = new ArrayList<>(List.of(new Batch(seq++, code, LocalDate.now().plusDays(5), shelf, 0)));
            s.storeBatches = new ArrayList<>(List.of(new Batch(seq++, code, LocalDate.now().plusDays(7), 0, store)));
        }

        @Override public Optional<Item> findItemByCode(String itemCode) { return Optional.ofNullable(map.get(itemCode)).map(st -> st.item); }
        @Override public Money priceOf(String itemCode) { return map.get(itemCode).price; }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) { return map.get(itemCode).shelfBatches; }
        @Override public List<Batch> findBatchesInStore(String itemCode) { return map.get(itemCode).storeBatches; }
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) {
            for (InventoryReservation r: reservations) { State s = map.get(r.itemCode); s.shelf -= r.quantity; }
        }
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) {
            for (InventoryReservation r: reservations) { State s = map.get(r.itemCode); s.store -= r.quantity; }
        }
        @Override public int shelfQty(String itemCode) { return map.get(itemCode).shelf; }
        @Override public int storeQty(String itemCode) { return map.get(itemCode).store; }
        @Override public int mainStoreQty(String itemCode) { return map.get(itemCode).main; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) { State s = map.get(itemCode); int t = Math.min(qty, s.store); s.store -= t; s.shelf += t; }
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) { State s = map.get(itemCode); int t = Math.min(qty, s.main); s.main -= t; s.shelf += t; }
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) { State s = map.get(itemCode); int t = Math.min(qty, s.main); s.main -= t; s.store += t; }
        @Override public void createItem(String code, String name, Money price) {}
        @Override public void renameItem(String code, String newName) {}
        @Override public void setItemPrice(String code, Money newPrice) {}
        @Override public void setItemRestockLevel(String code, int level) { map.get(code).restock = level; }
        @Override public void deleteItem(String code) {}
        @Override public List<Item> listAllItems() { return new ArrayList<>(); }
        @Override public List<Item> searchItemsByNameOrCode(String query) { return new ArrayList<>(); }
        @Override public int restockLevel(String itemCode) { return map.get(itemCode).restock; }
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {}
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {}
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) {}
        @Override public void deleteBatch(long batchId) {}
    }
}
