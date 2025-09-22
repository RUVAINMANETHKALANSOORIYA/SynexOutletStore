package application.pos;

import application.events.EventBus;
import application.inventory.InventoryService;
import application.inventory.InventoryAdminService;
import application.pricing.PricingService;
import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.BillNumberGenerator;
import domain.billing.BillWriter;
import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.BatchDiscount;
import domain.inventory.InventoryReservation;
import domain.inventory.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.BillRepository;
import ports.out.InventoryRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BatchDiscountPOSTest {

    private InventoryService inv;
    private InventoryAdminService invAdmin;
    private FakeInventoryRepo invRepo;
    private PricingService pricing;
    private FakeBillNoGen billNos;
    private FakeBillRepo repo;
    private FakeBillWriter writer;
    private CapturingEvents events;
    private POSController pos;

    @BeforeEach
    void setup() {
        invRepo = new FakeInventoryRepo();
        inv = new InventoryService(invRepo, new application.inventory.FefoBatchSelector());
        invAdmin = new InventoryAdminService(invRepo);
        pricing = new PricingService(0.0, inv); // 0% tax for simplicity
        billNos = new FakeBillNoGen();
        repo = new FakeBillRepo();
        writer = new FakeBillWriter();
        events = new CapturingEvents();
        pos = new POSController(inv, invAdmin, pricing, billNos, repo, writer, events);
        pos.newBill();
    }

    @Test
    @DisplayName("Batch discount automatically applied when adding items from discounted batch")
    void batch_discount_auto_applied() {
        // Setup: Item with a discounted batch
        invRepo.setItem("APPLE", "Apple", 10.0);
        invRepo.setQuantities("APPLE", 0, 5, 0, 50);

        // Inventory manager adds 20% discount to batch 2 (store batch)
        invRepo.addTestBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, Money.of(20.0), "manager");

        // Act: Add 2 apples from store (POS channel uses store first)
        pos.addItem("APPLE", 2);

        // Verify: Batch discount should be automatically applied
        Money total = pos.total();

        // Expected calculation:
        // Original: 2 × $10 = $20
        // 20% discount: $20 × 0.20 = $4
        // Final total: $20 - $4 = $16
        assertEquals(Money.of(16.0), total);

        // Verify discount info shows batch discount
        String discountInfo = pos.getCurrentDiscountInfo();
        assertTrue(discountInfo.contains("BATCH_DISCOUNTS"));
        assertTrue(discountInfo.contains("4.00"));
    }

    @Test
    @DisplayName("Fixed amount batch discount works correctly")
    void fixed_amount_batch_discount() {
        // Setup: Item with fixed discount
        invRepo.setItem("BREAD", "Bread", 5.0);
        invRepo.setQuantities("BREAD", 0, 3, 0, 50);

        // Inventory manager adds $1.50 fixed discount to batch 2
        invRepo.addTestBatchDiscount(2L, BatchDiscount.DiscountType.FIXED_AMOUNT, Money.of(1.5), "manager");

        // Act: Add 3 bread from store
        pos.addItem("BREAD", 3);

        // Verify: Fixed discount applied to each item
        Money total = pos.total();

        // Expected calculation:
        // Original: 3 × $5 = $15
        // Fixed discount: 3 × $1.50 = $4.50
        // Final total: $15 - $4.50 = $10.50
        assertEquals(Money.of(10.5), total);
    }

    @Test
    @DisplayName("Only items from discounted batches get discount")
    void only_discounted_batches_get_discount() {
        // Setup: Two items, only one has batch discount
        invRepo.setItem("DISCOUNTED", "Discounted Item", 20.0);
        invRepo.setQuantities("DISCOUNTED", 0, 2, 0, 50);

        invRepo.setItem("REGULAR", "Regular Item", 15.0);
        invRepo.setQuantities("REGULAR", 0, 2, 0, 50);

        // Only add discount to DISCOUNTED item's batch (batch 2)
        invRepo.addTestBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, Money.of(10.0), "manager");

        // Act: Add both items
        pos.addItem("DISCOUNTED", 1); // Should get 10% discount
        pos.addItem("REGULAR", 1);     // Should NOT get discount

        // Verify: Only discounted item gets discount
        Money total = pos.total();

        // Expected calculation:
        // DISCOUNTED: $20 - $2 (10%) = $18
        // REGULAR: $15 (no discount)
        // Total: $18 + $15 = $33
        assertEquals(Money.of(33.0), total);

        // Verify discount descriptions
        List<String> discounts = pos.getAvailableDiscounts();
        assertEquals(1, discounts.size());
        assertTrue(discounts.get(0).contains("Discounted Item"));
        assertTrue(discounts.get(0).contains("10%"));
    }

    @Test
    @DisplayName("Batch discounts only apply to in-store inventory (POS channel)")
    void batch_discounts_only_apply_to_store_inventory() {
        // Setup: Item available on both shelf and store
        invRepo.setItem("MIXED", "Mixed Item", 30.0);
        invRepo.setQuantities("MIXED", 3, 2, 0, 50);

        // Add discount only to store batch (batch 2), not shelf batch (batch 1)
        invRepo.addTestBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, Money.of(15.0), "manager");

        // Act 1: POS channel (uses store first) - should get discount
        pos.setChannel("POS");
        pos.addItem("MIXED", 2);
        Money posTotal = pos.total();

        // Expected for POS: $60 - $9 (15% discount) = $51
        assertEquals(Money.of(51.0), posTotal);

        // Reset for next test
        pos.newBill();

        // Act 2: ONLINE channel (uses shelf) - should NOT get discount
        pos.setChannel("ONLINE");
        pos.addItem("MIXED", 2);
        Money onlineTotal = pos.total();

        // Expected for ONLINE: $60 (no discount from shelf)
        assertEquals(Money.of(60.0), onlineTotal);
    }

    @Test
    @DisplayName("Multiple batch discounts are combined correctly")
    void multiple_batch_discounts_combined() {
        // Setup: Multiple items with different batch discounts
        invRepo.setItem("ITEM1", "Item 1", 25.0);
        invRepo.setQuantities("ITEM1", 0, 2, 0, 50);

        invRepo.setItem("ITEM2", "Item 2", 40.0);
        invRepo.setQuantities("ITEM2", 0, 1, 0, 50);

        // Add different discounts to different batches
        invRepo.addTestBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, Money.of(20.0), "manager"); // ITEM1
        invRepo.addTestBatchDiscount(4L, BatchDiscount.DiscountType.FIXED_AMOUNT, Money.of(5.0), "manager"); // ITEM2

        // Act: Add both items
        pos.addItem("ITEM1", 2); // $50 - $10 (20%) = $40
        pos.addItem("ITEM2", 1); // $40 - $5 (fixed) = $35

        // Verify: Combined discounts applied
        Money total = pos.total();
        assertEquals(Money.of(75.0), total); // $40 + $35 = $75

        // Verify multiple discounts are listed
        List<String> discounts = pos.getAvailableDiscounts();
        assertEquals(2, discounts.size());
    }

    @Test
    @DisplayName("Expired batch discounts are not applied")
    void expired_batch_discounts_not_applied() {
        // Setup: Item with expired discount
        invRepo.setItem("EXPIRED", "Expired Discount Item", 50.0);
        invRepo.setQuantities("EXPIRED", 0, 1, 0, 50);

        // Add expired discount
        invRepo.addExpiredBatchDiscount(2L, BatchDiscount.DiscountType.PERCENTAGE, Money.of(30.0), "manager");

        // Act: Add item
        pos.addItem("EXPIRED", 1);

        // Verify: No discount applied (original price)
        Money total = pos.total();
        assertEquals(Money.of(50.0), total);

        String discountInfo = pos.getCurrentDiscountInfo();
        assertEquals("No discount applied", discountInfo);
    }

    // ===== Test Helper Classes =====

    static final class FakeInventoryRepo implements InventoryRepository {
        static final class ItemState {
            Item item; Money price; int shelf; int store; int main; int restock;
            long shelfBatchId; long storeBatchId; long mainBatchId;
            List<BatchDiscount> batchDiscounts = new ArrayList<>();
            ItemState(Item item, Money price, int shelf, int store, int main, int restock){
                this.item=item; this.price=price; this.shelf=shelf; this.store=store; this.main=main; this.restock=restock; }
        }
        final Map<String, ItemState> items = new HashMap<>();
        boolean committedShelf = false; boolean committedStore = false;
        private long nextBatchId = 1;
        private long nextBatchSeq = 1;

        void setItem(String code, String name, double unitPrice) {
            items.putIfAbsent(code, new ItemState(new Item(items.size()+1, code, name, Money.of(unitPrice)), Money.of(unitPrice), 0,0,0,50));
        }

        void setQuantities(String code, int shelf, int store, int main, int restock) {
            ItemState st = items.get(code);
            if (st == null) throw new IllegalStateException("unknown item "+code);
            st.shelf = shelf; st.store = store; st.main = main; st.restock = restock;
            // Assign deterministic unique batch IDs per item for shelf/store (even if qty is 0)
            if (st.shelfBatchId == 0) st.shelfBatchId = nextBatchSeq++;
            if (st.storeBatchId == 0) st.storeBatchId = nextBatchSeq++;
            // Do not auto-assign MAIN batch IDs in tests to keep store IDs even (2,4,6,...)
        }

        void addTestBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String createdBy) {
            LocalDateTime now = LocalDateTime.now();
            BatchDiscount discount = new BatchDiscount(
                nextBatchId++, batchId, type, value, "Test discount",
                now.minusHours(1), now.plusDays(30), createdBy, now, true
            );

            // Find which item this batch belongs to and add the discount to that item only
            for (ItemState state : items.values()) {
                if (state.shelfBatchId == batchId || state.storeBatchId == batchId || state.mainBatchId == batchId) {
                    state.batchDiscounts.add(discount);
                    break;
                }
            }
        }

        void addExpiredBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String createdBy) {
            LocalDateTime now = LocalDateTime.now();
            BatchDiscount discount = new BatchDiscount(
                nextBatchId++, batchId, type, value, "Expired discount",
                now.minusDays(30), now.minusDays(1), createdBy, now.minusDays(30), true
            );

            for (ItemState state : items.values()) {
                state.batchDiscounts.add(discount);
            }
        }

        @Override public Optional<Item> findItemByCode(String itemCode) {
            ItemState st = items.get(itemCode); return Optional.ofNullable(st == null ? null : st.item);
        }
        @Override public Money priceOf(String itemCode) { return items.get(itemCode).price; }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) {
            ItemState st = items.get(itemCode);
            long id = st.shelfBatchId == 0 ? 0 : st.shelfBatchId;
            return List.of(new Batch(id, itemCode, LocalDate.now().plusDays(10), st.shelf, 0, 0));
        }
        @Override public List<Batch> findBatchesInStore(String itemCode) {
            ItemState st = items.get(itemCode);
            long id = st.storeBatchId == 0 ? 0 : st.storeBatchId;
            return List.of(new Batch(id, itemCode, LocalDate.now().plusDays(10), 0, st.store, 0));
        }
        @Override public void commitReservations(Iterable<InventoryReservation> reservations) {
            committedShelf = true;
            for (InventoryReservation r : reservations) {
                ItemState st = items.get(r.itemCode);
                st.shelf = Math.max(0, st.shelf - r.quantity);
            }
        }
        @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) {
            committedStore = true;
            for (InventoryReservation r : reservations) {
                ItemState st = items.get(r.itemCode);
                st.store = Math.max(0, st.store - r.quantity);
            }
        }
        @Override public int shelfQty(String itemCode) { return items.get(itemCode).shelf; }
        @Override public int storeQty(String itemCode) { return items.get(itemCode).store; }
        @Override public int mainStoreQty(String itemCode) { return items.get(itemCode).main; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) {
            ItemState st = items.get(itemCode); int mv = Math.min(qty, st.store); st.store -= mv; st.shelf += mv;
        }
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) {
            ItemState st = items.get(itemCode); int mv = Math.min(qty, st.main); st.main -= mv; st.shelf += mv;
        }
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) {
            ItemState st = items.get(itemCode); int mv = Math.min(qty, st.main); st.main -= mv; st.store += mv;
        }
        @Override public void createItem(String code, String name, Money price) { setItem(code, name, price.asBigDecimal().doubleValue()); }
        @Override public void renameItem(String code, String newName) { /* unused */ }
        @Override public void setItemPrice(String code, Money newPrice) { /* unused */ }
        @Override public void setItemRestockLevel(String code, int level) { items.get(code).restock = level; }
        @Override public void deleteItem(String code) { items.remove(code); }
        @Override public List<Item> listAllItems() { return items.values().stream().map(s -> s.item).toList(); }
        @Override public List<Item> searchItemsByNameOrCode(String query) { return listAllItems(); }
        @Override public int restockLevel(String itemCode) { return items.get(itemCode).restock; }
        @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) { /* unused */ }
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) { /* unused */ }
        @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) { /* unused */ }
        @Override public void deleteBatch(long batchId) { /* unused */ }

        // Batch discount methods
        @Override public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String reason, String createdBy) {
            addTestBatchDiscount(batchId, type, value, createdBy);
        }
        @Override public void removeBatchDiscount(long discountId) { /* unused */ }
        @Override public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) {
            return items.values().stream()
                .flatMap(state -> state.batchDiscounts.stream())
                .filter(discount -> discount.batchId() == batchId && discount.isActive() && discount.isValidNow())
                .findFirst();
        }
        @Override public List<BatchDiscount> findBatchDiscountsByBatch(long batchId) { return List.of(); }
        @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() { return List.of(); }
    }

    static final class FakeBillNoGen implements BillNumberGenerator {
        int n = 1; @Override public String next() { return String.format("BATCH-TEST-%04d", n++); }
    }

    static final class FakeBillRepo implements BillRepository {
        Bill saved; @Override public void save(Bill bill) { this.saved = bill; }
    }

    static final class FakeBillWriter implements BillWriter {
        final List<Bill> written = new ArrayList<>();
        @Override public void write(Bill bill) { written.add(bill); }
    }

    static final class CapturingEvents implements EventBus {
        final List<Object> published = new ArrayList<>();
        @Override public void publish(Object event) { published.add(event); }
        @Override public <T> void subscribe(Class<T> type, java.util.function.Consumer<T> handler) { /* not needed */ }
    }
}
