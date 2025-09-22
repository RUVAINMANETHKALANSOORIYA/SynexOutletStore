package application.online;

import application.events.EventBus;
import application.inventory.InventoryService;
import application.pricing.PricingService;
import application.pos.POSController;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ONLINE store scenarios: ensure controller behaves correctly when channel is ONLINE.
 */
class OnlineStoreControllerTest {

    private InventoryService inv;
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
        inv = new InventoryService(invRepo, new application.inventory.FefoBatchSelector()); // Use non-deprecated constructor
        pricing = new PricingService(0.0, inv); // Updated to include inventory service
        billNos = new FakeBillNoGen();
        repo = new FakeBillRepo();
        writer = new FakeBillWriter();
        events = new CapturingEvents();
        pos = new POSController(inv, null, pricing, billNos, repo, writer, events); // Fixed parameter order
        pos.setChannel("ONLINE");
        pos.newBill();
    }

    @Test
    @DisplayName("new ONLINE bill propagates channel metadata and uppercases channel")
    void online_newBill_metadata() {
        Bill b = current();
        assertEquals("ONLINE", b.channel());
        pos.setChannel("online"); // lower -> upper
        assertEquals("ONLINE", current().channel());
    }

    @Test
    @DisplayName("ONLINE legacy addItem reserves from shelf (non-POS path)")
    void online_addItem_legacy_uses_shelf() {
        invRepo.setItem("ON1", "OnlineItem", 20.0);
        invRepo.setQuantities("ON1", 5, 0, 0, 50);

        pos.addItem("ON1", 2); // should plan from shelf
        BillLine l = current().lines().get(0);
        assertEquals("ON1", l.itemCode());
        assertEquals(2, l.quantity());
    }

    @Test
    @DisplayName("ONLINE checkout by card persists, writes, commits shelf reservations, publishes events, and resets")
    void online_checkoutCard_commits_shelf_and_publishes_events() {
        invRepo.setItem("Z", "Zebra", 10.0);
        invRepo.setQuantities("Z", 1, 0, 0, 50); // total after sale becomes 0 => StockDepleted
        pos.addItem("Z", 1);

        pos.checkoutCard("4242");

        assertNotNull(repo.saved);
        assertTrue(writer.written.contains(repo.saved));
        assertTrue(invRepo.committedShelf);
        assertFalse(invRepo.committedStore);

        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.BillPaid));
        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.StockDepleted));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pos.total());
        assertTrue(ex.getMessage().toLowerCase().contains("no active"));
    }

    // ===== Helpers =====
    private Bill current() { return TestUtil.getActive(pos); }

    // Reflection helper to access private active bill for assertions on metadata
    private static final class TestUtil {
        static Bill getActive(POSController pos) {
            try {
                var f = POSController.class.getDeclaredField("active");
                f.setAccessible(true);
                return (Bill) f.get(pos);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ===== Test Doubles (minimal copies from POSControllerTest) =====
    static final class FakeInventoryRepo implements InventoryRepository {
        static final class ItemState {
            Item item; Money price; int shelf; int store; int main; int restock;
            ItemState(Item item, Money price, int shelf, int store, int main, int restock){
                this.item=item; this.price=price; this.shelf=shelf; this.store=store; this.main=main; this.restock=restock; }
        }
        final Map<String, ItemState> items = new HashMap<>();
        boolean committedShelf = false; boolean committedStore = false;

        void setItem(String code, String name, double unitPrice) {
            items.putIfAbsent(code, new ItemState(new Item(items.size()+1, code, name, Money.of(unitPrice)), Money.of(unitPrice), 0,0,0,50));
        }
        void setQuantities(String code, int shelf, int store, int main, int restock) {
            ItemState st = items.get(code);
            if (st == null) throw new IllegalStateException("unknown item "+code);
            st.shelf = shelf; st.store = store; st.main = main; st.restock = restock;
        }

        @Override public Optional<Item> findItemByCode(String itemCode) {
            ItemState st = items.get(itemCode); return Optional.ofNullable(st == null ? null : st.item);
        }
        @Override public Money priceOf(String itemCode) { return items.get(itemCode).price; }
        @Override public List<Batch> findBatchesOnShelf(String itemCode) {
            ItemState st = items.get(itemCode);
            return List.of(new Batch(1, itemCode, LocalDate.now().plusDays(10), st.shelf, 0, 0));
        }
        @Override public List<Batch> findBatchesInStore(String itemCode) {
            ItemState st = items.get(itemCode);
            return List.of(new Batch(2, itemCode, LocalDate.now().plusDays(10), 0, st.store, 0));
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

        // Added missing batch discount methods
        @Override public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value, String reason, String createdBy) { /* unused */ }
        @Override public void removeBatchDiscount(long discountId) { /* unused */ }
        @Override public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) { return Optional.empty(); }
        @Override public List<BatchDiscount> findBatchDiscountsByBatch(long batchId) { return List.of(); }
        @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() { return List.of(); }
    }

    static final class FakeBillNoGen implements BillNumberGenerator {
        int n = 1; @Override public String next() { return String.format("ONL-TEST-%04d", n++); }
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
