package infrastructure.security;

import ports.in.AuthService;
import domain.auth.User;
import domain.common.Money;
import domain.inventory.Item;
import domain.inventory.Batch;
import ports.out.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCheckedInventoryRepositoryTest {

    private PermissionCheckedInventoryRepository repository;
    private FakeInventoryRepository fakeRepo;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeInventoryRepository();
        FakeUserRepository userRepo = new FakeUserRepository();
        authService = new AuthService(userRepo); // Create real AuthService with fake repository
        repository = new PermissionCheckedInventoryRepository(fakeRepo, authService);

        // Set up test users in the fake repository
        userRepo.addUser(createManagerUser());
        userRepo.addUser(createAdminUser());
        userRepo.addUser(createRegularUser());
    }

    @Test
    @DisplayName("Manager can move items from main to shelf")
    void manager_can_move_main_to_shelf() {
        authService.login("manager1", "password123");

        assertDoesNotThrow(() -> {
            repository.moveMainToShelfFEFO("ITEM001", 10);
        });
    }

    @Test
    @DisplayName("Regular user cannot move items from main to shelf")
    void regular_user_cannot_move_main_to_shelf() {
        authService.login("user1", "password789");

        assertThrows(SecurityException.class, () -> {
            repository.moveMainToShelfFEFO("ITEM001", 10);
        });
    }

    @Test
    @DisplayName("Admin can move items from main to store")
    void admin_can_move_main_to_store() {
        authService.login("admin1", "password456");

        assertDoesNotThrow(() -> {
            repository.moveMainToStoreFEFO("ITEM002", 15);
        });
    }

    @Test
    @DisplayName("Regular user cannot move items from main to store")
    void regular_user_cannot_move_main_to_store() {
        authService.login("user1", "password789");

        assertThrows(SecurityException.class, () -> {
            repository.moveMainToStoreFEFO("ITEM002", 15);
        });
    }

    @Test
    @DisplayName("Anyone can read item information")
    void anyone_can_read_item_info() {
        authService.login("user1", "password789");

        Optional<Item> item = repository.findItemByCode("ITEM001");
        assertDoesNotThrow(() -> repository.priceOf("ITEM001"));
        assertDoesNotThrow(() -> repository.shelfQty("ITEM001"));
    }

    @Test
    @DisplayName("Anyone can read batch information")
    void anyone_can_read_batch_info() {
        authService.login("user1", "password789");

        assertDoesNotThrow(() -> {
            repository.findBatchesOnShelf("ITEM001");
            repository.findBatchesInStore("ITEM001");
        });
    }

    @Test
    @DisplayName("Manager can add batch discounts")
    void manager_can_add_batch_discounts() {
        authService.login("manager1", "password123");

        assertDoesNotThrow(() -> {
            repository.addBatchDiscount(1L,
                domain.inventory.BatchDiscount.DiscountType.PERCENTAGE,
                Money.of(10.0),
                "Seasonal discount",
                "manager1");
        });
    }

    @Test
    @DisplayName("Regular user cannot add batch discounts")
    void regular_user_cannot_add_batch_discounts() {
        authService.login("user1", "password789");

        assertThrows(SecurityException.class, () -> {
            repository.addBatchDiscount(1L,
                domain.inventory.BatchDiscount.DiscountType.PERCENTAGE,
                Money.of(10.0),
                "Unauthorized discount",
                "user1");
        });
    }

    @Test
    @DisplayName("Admin can remove batch discounts")
    void admin_can_remove_batch_discounts() {
        authService.login("admin1", "password456");

        assertDoesNotThrow(() -> {
            repository.removeBatchDiscount(1L);
        });
    }

    @Test
    @DisplayName("Regular user cannot remove batch discounts")
    void regular_user_cannot_remove_batch_discounts() {
        authService.login("user1", "password789");

        assertThrows(SecurityException.class, () -> {
            repository.removeBatchDiscount(1L);
        });
    }

    @Test
    @DisplayName("Null user is treated as unauthorized")
    void null_user_unauthorized() {
        authService.logout(); // Ensure no user is logged in

        assertThrows(SecurityException.class, () -> {
            repository.moveMainToShelfFEFO("ITEM001", 10);
        });
    }

    @Test
    @DisplayName("User with unknown role is unauthorized")
    void unknown_role_unauthorized() {
        authService.login("unknown1", "password000");

        assertThrows(SecurityException.class, () -> {
            repository.moveMainToShelfFEFO("ITEM001", 10);
        });
    }

    // Helper methods to create test users
    private User createManagerUser() {
        return new User(1L, "manager1", "INVENTORY_MANAGER", "manager1@store.com", "ACTIVE");
    }

    private User createAdminUser() {
        return new User(2L, "admin1", "ADMIN", "admin1@store.com", "ACTIVE");
    }

    private User createRegularUser() {
        return new User(3L, "user1", "USER", "user1@store.com", "ACTIVE");
    }

    private User createUserWithRole(String role) {
        return new User(4L, "unknown1", role, "unknown1@store.com", "ACTIVE");
    }

    // Fake implementations for testing
    static class FakeInventoryRepository implements InventoryRepository {
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
        @Override public Optional<Item> findItemByCode(String itemCode) { return Optional.empty(); }
        @Override public Money priceOf(String itemCode) { return Money.of(10.0); }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) { return new ArrayList<>(); }
        @Override public List<Batch> findBatchesInStore(String itemCode) { return new ArrayList<>(); }
        @Override public void commitReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<domain.inventory.InventoryReservation> reservations) {}
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {}
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

    static class FakeUserRepository implements ports.out.UserRepository {
        private final List<User> users = new ArrayList<>();

        public void addUser(User user) {
            users.add(user);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public String loadPasswordHash(String username) {
            return users.stream()
                    .filter(u -> u.username().equals(username))
                    .map(u -> "password123") // Return a fixed password for testing since User doesn't have password field
                    .findFirst()
                    .orElse(null);
        }
    }
}
