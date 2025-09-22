package infrastructure.security;

import application.auth.AuthService;
import domain.auth.User;
import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.BatchDiscount;
import domain.inventory.InventoryReservation;
import domain.inventory.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.InventoryRepository;
import ports.out.UserRepository;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCheckedInventoryRepositoryTest {

    private RecordingInventoryRepo inner;
    private AuthService auth;
    private PermissionCheckedInventoryRepository secured;
    private InMemoryUserRepo users;

    @BeforeEach
    void setup() {
        inner = new RecordingInventoryRepo();
        users = new InMemoryUserRepo();
        // passwords stored in plaintext (AuthService supports plaintext fallback)
        users.add(new User(1, "manager", "INVENTORY_MANAGER", "m@example.com", "ACTIVE"), "pwd");
        users.add(new User(2, "admin", "ADMIN", "a@example.com", "ACTIVE"), "pwd");
        users.add(new User(3, "cashier", "CASHIER", "c@example.com", "ACTIVE"), "pwd");
        auth = new AuthService(users);
        secured = new PermissionCheckedInventoryRepository(inner, auth);
    }

    @Test
    @DisplayName("INVENTORY_MANAGER can move MAIN to shelf/store")
    void manager_can_move_main() {
        assertTrue(auth.login("manager", "pwd"));
        secured.moveMainToShelfFEFO("X", 5);
        secured.moveMainToStoreFEFO("X", 3);
        assertEquals(List.of("moveMainToShelfFEFO:X:5", "moveMainToStoreFEFO:X:3"), inner.calls);
    }

    @Test
    @DisplayName("ADMIN can move MAIN to shelf/store")
    void admin_can_move_main() {
        assertTrue(auth.login("admin", "pwd"));
        secured.moveMainToShelfFEFO("Y", 2);
        secured.moveMainToStoreFEFO("Y", 4);
        assertTrue(inner.calls.contains("moveMainToShelfFEFO:Y:2"));
        assertTrue(inner.calls.contains("moveMainToStoreFEFO:Y:4"));
    }

    @Test
    @DisplayName("CASHIER cannot move MAIN to shelf/store")
    void cashier_cannot_move_main() {
        assertTrue(auth.login("cashier", "pwd"));
        SecurityException ex1 = assertThrows(SecurityException.class, () -> secured.moveMainToShelfFEFO("Z", 1));
        assertTrue(ex1.getMessage().contains("Manager/Admin required"));
        SecurityException ex2 = assertThrows(SecurityException.class, () -> secured.moveMainToStoreFEFO("Z", 1));
        assertTrue(ex2.getMessage().contains("Manager/Admin required"));
        assertTrue(inner.calls.isEmpty());
    }

    @Test
    @DisplayName("No user logged in cannot move MAIN to shelf/store")
    void no_user_cannot_move_main() {
        // no login
        SecurityException ex1 = assertThrows(SecurityException.class, () -> secured.moveMainToShelfFEFO("A", 1));
        assertTrue(ex1.getMessage().contains("Manager/Admin required"));
        SecurityException ex2 = assertThrows(SecurityException.class, () -> secured.moveMainToStoreFEFO("A", 1));
        assertTrue(ex2.getMessage().contains("Manager/Admin required"));
        assertTrue(inner.calls.isEmpty());
    }

    @Test
    @DisplayName("Non-guarded methods delegate without role checks (moveStoreToShelfFEFO)")
    void non_guarded_delegate_without_checks() {
        // even without login, this should delegate
        secured.moveStoreToShelfFEFO("B", 7);
        assertEquals(List.of("moveStoreToShelfFEFO:B:7"), inner.calls);
    }

    // ===== Test doubles =====
    static final class RecordingInventoryRepo implements InventoryRepository {
        final List<String> calls = new ArrayList<>();
        final Map<String, Item> items = new HashMap<>();
        @Override public Optional<Item> findItemByCode(String itemCode) { return Optional.ofNullable(items.get(itemCode)); }
        @Override public Money priceOf(String itemCode) { return Money.of(1.0); }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) { return List.of(new Batch(1, itemCode, LocalDate.now().plusDays(1), 0, 0)); }
        @Override public List<Batch> findBatchesInStore(String itemCode) { return List.of(new Batch(2, itemCode, LocalDate.now().plusDays(1), 0, 0)); }
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) { calls.add("commitReservations"); }
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) { calls.add("commitStoreReservations"); }
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) { calls.add("moveStoreToShelfFEFO:"+itemCode+":"+qty); }
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) { calls.add("moveMainToShelfFEFO:"+itemCode+":"+qty); }
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) { calls.add("moveMainToStoreFEFO:"+itemCode+":"+qty); }
        @Override public void createItem(String code, String name, Money price) { items.put(code, new Item(1, code, name, price)); }
        @Override public void renameItem(String code, String newName) {}
        @Override public void setItemPrice(String code, Money newPrice) {}
        @Override public void setItemRestockLevel(String code, int level) {}
        @Override public void deleteItem(String code) { items.remove(code); }
        @Override public List<Item> listAllItems() { return new ArrayList<>(items.values()); }
        @Override public List<Item> searchItemsByNameOrCode(String query) { return listAllItems(); }
        @Override public int restockLevel(String itemCode) { return 50; }
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {}
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {}
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) {}
        @Override public void deleteBatch(long batchId) {}

        // Added missing batch discount methods
        @Override public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String reason, String createdBy) {}
        @Override public void removeBatchDiscount(long discountId) {}
        @Override public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) { return Optional.empty(); }
        @Override public List<BatchDiscount> findBatchDiscountsByBatch(long batchId) { return List.of(); }
        @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() { return List.of(); }
    }

    static final class InMemoryUserRepo implements UserRepository {
        private final Map<String, User> byName = new HashMap<>();
        private final Map<String, String> hashes = new HashMap<>();
        void add(User u, String passwordOrHash) { byName.put(u.username(), u); hashes.put(u.username(), passwordOrHash); }
        @Override public Optional<User> findByUsername(String username) { return Optional.ofNullable(byName.get(username)); }
        @Override public String loadPasswordHash(String username) { return hashes.get(username); }
    }
}
