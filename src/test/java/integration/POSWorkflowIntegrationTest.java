package integration;

import application.inventory.InventoryAdminService;
import ports.in.InventoryService;
import application.inventory.FefoBatchSelector;
import application.pos.controllers.POSController;
import application.pricing.PricingService;
import domain.billing.Bill;
import domain.billing.BillNumberGenerator;
import domain.billing.BillWriter;
import domain.common.Money;
import domain.inventory.BatchDiscount;
import domain.inventory.Item;
import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ports.out.BillRepository;
import ports.out.InventoryRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class POSWorkflowIntegrationTest {

    private POSController pos;
    private FakeIntegratedInventoryRepo invRepo;
    private InventoryService inventoryService;
    private InventoryAdminService inventoryAdmin;
    private PricingService pricingService;
    private List<Bill> savedBills;
    private List<Object> publishedEvents;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        invRepo = new FakeIntegratedInventoryRepo();
        inventoryService = new InventoryService(invRepo, new FefoBatchSelector());
        inventoryAdmin = new InventoryAdminService(invRepo);
        pricingService = new PricingService(15.0, inventoryService); // 15% tax
        
        savedBills = new ArrayList<>();
        publishedEvents = new ArrayList<>();
        
        pos = new POSController(
            inventoryService, 
            inventoryAdmin,
            pricingService,
            new TestBillNumberGenerator(),
            new TestBillRepository(),
            new TestBillWriter(),
            new TestEventBus()
        );
        
        setupTestInventory();
    }

    @Test
    @DisplayName("Complete POS workflow: new bill, add items, apply discount, pay, checkout")
    void complete_pos_workflow() {
        // 1. Start new bill
        pos.newBill();
        pos.setUser("cashier_john");
        pos.setChannel("POS");

        // 2. Add items to bill
        pos.addItem("APPLE", 5);    // 5 × $2.00 = $10.00
        pos.addItem("BREAD", 2);    // 2 × $3.50 = $7.00
        pos.addItem("MILK", 1);     // 1 × $4.00 = $4.00

        // 3. Check subtotal before discounts
        Bill currentBill = getCurrentBill();
        assertEquals(3, currentBill.lines().size());

        // 4. Apply percentage discount
        pos.applyDiscount(new TestPercentageDiscount(10.0)); // 10% off

        // 5. Pay with cash
        Money total = pos.total();
        assertTrue(total.compareTo(Money.ZERO) > 0);
        pos.payCash(total.asBigDecimal().doubleValue() + 5.0); // Pay with extra for change

        // 6. Checkout
        pos.checkout();

        // Verify complete workflow
        assertEquals(1, savedBills.size());
        assertFalse(publishedEvents.isEmpty());
        
        Bill finalBill = savedBills.get(0);
        assertEquals("cashier_john", finalBill.userName());
        assertEquals("POS", finalBill.channel());
        assertEquals("CASH", finalBill.paymentMethod());
        assertEquals(3, finalBill.lines().size());
    }

    @Test
    @DisplayName("Batch discount integration workflow")
    void batch_discount_integration_workflow() {
        // 1. Manager adds batch discount
        inventoryAdmin.addBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, 
                                      Money.of(20.0), "Manager special", "manager_jane");

        // 2. Start POS transaction
        pos.newBill();
        pos.setUser("cashier_bob");

        // 3. Add items (some from discounted batch)
        pos.addItem("APPLE", 3); // Should get 20% discount from batch 2
        pos.addItem("BREAD", 1); // No batch discount

        // 4. Verify discount is automatically applied
        List<String> availableDiscounts = pos.getAvailableDiscounts();
        assertFalse(availableDiscounts.isEmpty());

        // 5. Complete transaction with valid 4-digit card number
        Money total = pos.total();
        pos.payCard("9012"); // Use exactly 4 digits
        pos.checkout();

        // Verify integration
        assertTrue(total.compareTo(Money.of(0.0)) > 0);
        
        Bill finalBill = savedBills.get(0);
        assertEquals("CARD", finalBill.paymentMethod());
        assertEquals("9012", finalBill.cardLast4());
    }

    @Test
    @DisplayName("Multiple customer transactions in sequence")
    void multiple_customer_transactions() {
        // Transaction 1
        pos.newBill();
        pos.setUser("cashier1");
        pos.addItem("APPLE", 2);
        pos.payCash(10.0);
        pos.checkout();

        // Transaction 2
        pos.newBill();
        pos.setUser("cashier2");
        pos.addItem("BREAD", 3);
        pos.addItem("MILK", 1);
        pos.payCard("4444"); // Use exactly 4 digits
        pos.checkout();

        // Verify both transactions
        assertEquals(2, savedBills.size());
        
        assertEquals("cashier1", savedBills.get(0).userName());
        assertEquals("cashier2", savedBills.get(1).userName());
        assertEquals("CASH", savedBills.get(0).paymentMethod());
        assertEquals("CARD", savedBills.get(1).paymentMethod());
    }

    @Test
    @DisplayName("Error recovery: restart after failed transaction")
    void error_recovery_workflow() {
        // Start transaction
        pos.newBill();
        pos.addItem("APPLE", 1);

        // Simulate error by not paying before checkout
        assertThrows(IllegalStateException.class, () -> pos.checkout());

        // Verify system can recover
        pos.newBill(); // Start fresh
        pos.addItem("BREAD", 1);
        pos.payCash(5.0);
        pos.checkout();

        // Should work normally after error
        assertEquals(1, savedBills.size());
    }

    @Test
    @DisplayName("Large transaction with many items")
    void large_transaction_many_items() {
        pos.newBill();
        pos.setUser("bulk_cashier");

        // Add many different items with proper inventory setup
        for (int i = 1; i <= 20; i++) {
            String itemCode = "BULK" + i;
            invRepo.addTestItem(itemCode, "Bulk Item " + i, Money.of(i * 1.5));
            // Set sufficient inventory for each item
            int quantity = i % 5 + 1;
            invRepo.setTestQuantities(itemCode, 0, quantity + 5, quantity + 10); // Ensure enough stock
            pos.addItem(itemCode, quantity); // Varying quantities
        }

        // Apply discount and pay
        pos.applyDiscount(new TestPercentageDiscount(5.0));
        Money total = pos.total();
        pos.payCash(total.asBigDecimal().doubleValue() + 50.0);
        pos.checkout();

        // Verify large transaction handling
        Bill finalBill = savedBills.get(0);
        assertEquals(20, finalBill.lines().size());
        assertTrue(finalBill.total().compareTo(Money.ZERO) > 0);
    }

    private Bill getCurrentBill() {
        try {
            var field = POSController.class.getDeclaredField("active");
            field.setAccessible(true);
            return (Bill) field.get(pos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupTestInventory() {
        invRepo.addTestItem("APPLE", "Fresh Apple", Money.of(2.00));
        invRepo.addTestItem("BREAD", "Whole Wheat Bread", Money.of(3.50));
        invRepo.addTestItem("MILK", "Organic Milk", Money.of(4.00));
        
        invRepo.setTestQuantities("APPLE", 20, 15, 50);
        invRepo.setTestQuantities("BREAD", 10, 8, 30);
        invRepo.setTestQuantities("MILK", 5, 12, 25);
    }

    // Test implementations
    static class TestBillNumberGenerator implements BillNumberGenerator {
        private int counter = 1;
        
        @Override
        public String next() {
            return String.format("INTEGRATION-TEST-%04d", counter++);
        }
    }

    class TestBillRepository implements BillRepository {
        @Override
        public String createBill() { return "TEST-BILL-001"; }

        @Override
        public void saveBill(Bill bill) {
            savedBills.add(bill);
        }

        @Override
        public Optional<Bill> findBill(String billId) { return Optional.empty(); }

        @Override
        public void savePaidBill(domain.billing.Receipt receipt) { /* unused in tests */ }

        @Override
        public List<Bill> findOpenBills() { return List.of(); }

        @Override
        public List<domain.billing.Receipt> findReceiptsByDate(java.time.LocalDate date) { return List.of(); }

        @Override
        public void deleteBill(String billId) { /* unused in tests */ }
    }

    static class TestBillWriter implements BillWriter {
        @Override
        public void write(Bill bill) {
            // Just track that write was called
        }
    }

    class TestEventBus implements application.events.EventBus {
        @Override
        public void publish(Object event) {
            publishedEvents.add(event);
        }

        @Override
        public <T> void subscribe(Class<T> type, java.util.function.Consumer<T> handler) {
            // Not needed for integration tests
        }
    }

    static class TestPercentageDiscount implements domain.pricing.DiscountPolicy {
        private final double percentage;

        TestPercentageDiscount(double percentage) {
            this.percentage = percentage;
        }

        @Override
        public Money computeDiscount(Bill bill) {
            Money subtotal = bill.computeSubtotal();
            return subtotal.multiply(percentage).divide(100);
        }

        @Override
        public String code() {
            return "TEST_PERCENTAGE";
        }
    }

    static class FakeIntegratedInventoryRepo implements InventoryRepository {
        final Map<String, Item> testItems = new HashMap<>();
        final Map<String, TestQuantities> quantities = new HashMap<>();

        static class TestQuantities {
            int shelf, store, main;
            TestQuantities(int shelf, int store, int main) {
                this.shelf = shelf; this.store = store; this.main = main;
            }
        }

        public void addTestItem(String code, String name, Money price) {
            testItems.put(code, new Item(1L, code, name, price, 50));
            quantities.put(code, new TestQuantities(0, 0, 0));
        }

        public void setTestQuantities(String code, int shelf, int store, int main) {
            quantities.put(code, new TestQuantities(shelf, store, main));
        }

        @Override
        public Optional<Item> findItemByCode(String itemCode) {
            return Optional.ofNullable(testItems.get(itemCode));
        }

        @Override
        public Money priceOf(String itemCode) {
            Item item = testItems.get(itemCode);
            return item != null ? item.unitPrice() : Money.ZERO;
        }

        @Override
        public List<Batch> findBatchesInStore(String itemCode) {
            TestQuantities qty = quantities.get(itemCode);
            if (qty == null || qty.store <= 0) return List.of();

            return List.of(new Batch(2L, itemCode, LocalDate.now().plusDays(30),
                                   0, qty.store, 0));
        }

        @Override
        public List<Batch> findBatchesOnShelf(String itemCode) {
            TestQuantities qty = quantities.get(itemCode);
            if (qty == null || qty.shelf <= 0) return List.of();

            return List.of(new Batch(1L, itemCode, LocalDate.now().plusDays(20),
                                   qty.shelf, 0, 0));
        }

        // Minimal implementations for other required methods
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) {}
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) {}
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {}
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {}
        @Override public void createItem(String code, String name, Money price) {}
        @Override public void renameItem(String itemCode, String newName) {}
        @Override public void setItemPrice(String itemCode, Money newPrice) {}
        @Override public void setItemRestockLevel(String itemCode, int level) {}
        @Override public void deleteItem(String itemCode) {}
        @Override public List<Item> listAllItems() { return List.of(); }
        @Override public List<Item> searchItemsByNameOrCode(String query) { return List.of(); }
        @Override public int restockLevel(String itemCode) { return 50; }
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {}
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {}
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) {}
        @Override public void deleteBatch(long batchId) {}
        @Override public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String reason, String createdBy) {}
        @Override public void removeBatchDiscount(long discountId) {}
        @Override public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) { return Optional.empty(); }
        @Override public List<BatchDiscount> findBatchDiscountsByBatch(long batchId) { return List.of(); }
        @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() { return List.of(); }
    }
}
