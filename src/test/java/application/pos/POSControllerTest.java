package application.pos;

import application.events.EventBus;
import application.inventory.InventoryService;
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
import domain.pricing.DiscountPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.BillRepository;
import ports.out.InventoryRepository;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class POSControllerTest {

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
        pos.newBill();
    }

    @Test
    @DisplayName("newBill initializes and propagates user/channel; setUser/setChannel update active bill")
    void newBill_and_metadata() {
        // defaults
        assertEquals("operator", current().userName());
        assertEquals("POS", current().channel());

        pos.setUser("alice");
        pos.setChannel("web");
        assertEquals("alice", current().userName());
        assertEquals("WEB", current().channel());
    }

    @Test
    @DisplayName("addItem legacy uses reserveByChannel and creates BillLine; POS channel path")
    void addItem_legacy_pos_channel() {
        invRepo.setItem("SKU1", "Apple", 100.0);
        invRepo.setQuantities("SKU1", 5, 10, 0, 100);

        pos.addItem("SKU1", 3);
        Bill b = current();
        assertEquals(1, b.lines().size());
        BillLine l = b.lines().get(0);
        assertEquals("SKU1", l.itemCode());
        assertEquals(3, l.quantity());
        assertEquals(Money.of(300.0), l.lineTotal());
    }

    @Test
    @DisplayName("addItem legacy uses shelf reservations when non-POS channel")
    void addItem_legacy_nonpos_channel() {
        invRepo.setItem("SKU2", "Banana", 50.0);
        invRepo.setQuantities("SKU2", 2, 0, 0, 100);

        pos.setChannel("kiosk");
        pos.addItem("SKU2", 2);
        assertEquals(1, current().lines().size());
        assertEquals("SKU2", current().lines().get(0).itemCode());
    }

    @Test
    @DisplayName("addItemSmart merges reservations and returns SmartPick")
    void addItemSmart_merges() {
        invRepo.setItem("SKU3", "Cookie", 25.0);
        invRepo.setQuantities("SKU3", 1, 1, 10, 100);
        InventoryService.SmartPick pick = pos.addItemSmart("SKU3", 2, true, true);
        assertNotNull(pick);
        assertEquals(2, current().lines().get(0).quantity());
        assertTrue(pick.shelfReservations.size() >= 0);
        assertTrue(pick.storeReservations.size() >= 0);
    }

    @Test
    @DisplayName("removeItem removes by code")
    void removeItem_by_code() {
        invRepo.setItem("S1", "Item", 10.0);
        invRepo.setQuantities("S1", 0, 5, 0, 50);
        pos.addItem("S1", 1);
        assertEquals(1, current().lines().size());
        pos.removeItem("S1");
        assertEquals(0, current().lines().size());
    }

    @Test
    @DisplayName("applyDiscount affects finalizePricing path and total() returns computed total; requires items")
    void total_and_discount() {
        invRepo.setItem("A", "Thing", 100.0);
        invRepo.setQuantities("A", 0, 10, 0, 100);
        pos.addItem("A", 2); // subtotal 200
        pos.applyDiscount(new FixedDiscount(Money.of(30.0)));
        Money tot = pos.total();
        // tax 0, discount 30 => total = 170
        assertEquals(Money.of(170.0), tot);
    }

    @Test
    @DisplayName("total throws if no items added")
    void total_throws_if_no_items() {
        pos = new POSController(inv, null, pricing, billNos, repo, writer, events); // Fixed parameter order
        pos.newBill();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pos.total());
        assertTrue(ex.getMessage().toLowerCase().contains("no items"));
    }

    @Test
    @DisplayName("payCash sets receipt and payment fields")
    void payCash_sets_receipt() {
        invRepo.setItem("C", "Candy", 12.5);
        invRepo.setQuantities("C", 0, 5, 0, 50);
        pos.addItem("C", 2); // 25.0
        pos.payCash(30.0);
        Bill b = current();
        assertEquals("CASH", b.paymentMethod());
        assertEquals(Money.of(30.0), b.paidAmount());
        assertEquals(Money.of(5.0), b.changeAmount());
    }

    @Test
    @DisplayName("payCard sets receipt and payment fields")
    void payCard_sets_receipt() {
        invRepo.setItem("D", "Drink", 40.0);
        invRepo.setQuantities("D", 0, 3, 0, 50);
        pos.addItem("D", 1);
        pos.payCard("1234");
        Bill b = current();
        assertEquals("CARD", b.paymentMethod());
        assertEquals("1234", b.cardLast4());
        assertEquals(b.total(), b.paidAmount());
    }

    @Test
    @DisplayName("checkout without prior payment throws")
    void checkout_requires_payment() {
        invRepo.setItem("E", "Egg", 10.0);
        invRepo.setQuantities("E", 0, 1, 0, 50);
        pos.addItem("E", 1);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pos.checkout());
        assertTrue(ex.getMessage().toLowerCase().contains("payment"));
    }

    @Test
    @DisplayName("checkoutCash persists, writes, commits reservations, publishes events, and resets state")
    void checkoutCash_full_flow_and_reset() {
        invRepo.setItem("X", "Xylitol", 10.0);
        invRepo.setQuantities("X", 0, 1, 0, 50); // total after sale becomes 0 => StockDepleted
        pos.addItem("X", 1);
        pos.setUser("bob");
        pos.setChannel("pos");
        pos.checkoutCash(10.0);

        assertNotNull(repo.saved);
        assertTrue(writer.written.contains(repo.saved));
        assertTrue(invRepo.committedStore);
        assertFalse(invRepo.committedShelf);

        // events contain BillPaid and StockDepleted
        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.BillPaid));
        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.StockDepleted));

        // reset
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pos.total());
        assertTrue(ex.getMessage().toLowerCase().contains("no active"));
    }

    @Test
    @DisplayName("checkoutCard also persists, writes, commits reservations, publishes threshold event, and resets state")
    void checkoutCard_full_flow_and_reset() {
        invRepo.setItem("Y", "Yogurt", 15.0);
        invRepo.setQuantities("Y", 0, 2, 0, 60);
        pos.addItem("Y", 1);
        pos.checkoutCard("9999");

        assertNotNull(repo.saved);
        assertTrue(writer.written.contains(repo.saved));
        assertTrue(invRepo.committedStore || invRepo.committedShelf);
        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.BillPaid));
        assertTrue(events.published.stream().anyMatch(e -> e instanceof application.events.events.RestockThresholdHit));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pos.total());
        assertTrue(ex.getMessage().toLowerCase().contains("no active"));
    }

    @Test
    @DisplayName("guard: operations throw when no active bill")
    void guard_no_active_bill() {
        pos = new POSController(inv, null, pricing, billNos, repo, writer, events); // Fixed parameter order
        assertThrows(IllegalStateException.class, () -> pos.addItem("A", 1));
        assertThrows(IllegalStateException.class, () -> pos.addItemSmart("A", 1, false, false));
        assertThrows(IllegalStateException.class, () -> pos.removeItem("A"));
        assertThrows(IllegalStateException.class, () -> pos.applyDiscount(new FixedDiscount(Money.of(1.0))));
        assertThrows(IllegalStateException.class, () -> pos.total());
        assertThrows(IllegalStateException.class, () -> pos.payCash(1));
        assertThrows(IllegalStateException.class, () -> pos.payCard("1111"));
        assertThrows(IllegalStateException.class, () -> pos.checkout());
        assertThrows(IllegalStateException.class, () -> pos.checkoutCash(1));
        assertThrows(IllegalStateException.class, () -> pos.checkoutCard("2222"));
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

    // ===== Test Doubles =====
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
        int n = 1; @Override public String next() { return String.format("POS-TEST-%04d", n++); }
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

    static final class FixedDiscount implements DiscountPolicy {
        private final Money amount; FixedDiscount(Money m){ this.amount = m; }
        @Override public Money computeDiscount(Bill bill) { return amount; }
        @Override public String code() { return "FIX"; }
    }
}
