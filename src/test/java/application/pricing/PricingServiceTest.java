package application.pricing;

import application.inventory.InventoryService;
import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import domain.pricing.DiscountPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.out.InventoryRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private static class FixedDiscount implements DiscountPolicy {
        private final Money amount; private final String code;
        FixedDiscount(Money amount, String code){ this.amount = amount; this.code = code; }
        @Override public Money computeDiscount(Bill bill) { return amount; }
        @Override public String code() { return code; }
    }

    // Create a mock InventoryService using composition instead of inheritance
    private static InventoryService createMockInventoryService() {
        MockInventoryRepo mockRepo = new MockInventoryRepo();
        return new InventoryService(mockRepo, new application.inventory.FefoBatchSelector());
    }

    private static class MockInventoryRepo implements InventoryRepository {
        // Return consistent test prices based on item code
        @Override
        public Money priceOf(String itemCode) {
            return switch (itemCode) {
                case "A" -> Money.of(10.0);
                case "B" -> Money.of(50.0);
                default -> Money.of(1.0);
            };
        }

        @Override
        public java.util.Optional<domain.inventory.Item> findItemByCode(String itemCode) {
            // Return a mock item for test purposes
            long id = itemCode.hashCode();
            String name = switch (itemCode) {
                case "A" -> "Apple";
                case "B" -> "Banana";
                default -> "Unknown";
            };
            return java.util.Optional.of(new domain.inventory.Item(id, itemCode, name, priceOf(itemCode)));
        }

        // Minimal implementation for testing - most methods unused
        @Override public List<domain.inventory.Batch> findBatchesOnShelf(String itemCode) { return List.of(); }
        @Override public List<domain.inventory.Batch> findBatchesInStore(String itemCode) { return List.of(); }
        @Override public void commitReservations(Iterable<domain.inventory.InventoryReservation> reservations) { }
        @Override public void commitStoreReservations(Iterable<domain.inventory.InventoryReservation> reservations) { }
        @Override public int shelfQty(String itemCode) { return 0; }
        @Override public int storeQty(String itemCode) { return 0; }
        @Override public int mainStoreQty(String itemCode) { return 0; }
        @Override public void moveStoreToShelfFEFO(String itemCode, int qty) { }
        @Override public void moveMainToShelfFEFO(String itemCode, int qty) { }
        @Override public void moveMainToStoreFEFO(String itemCode, int qty) { }
        @Override public void createItem(String code, String name, Money price) { }
        @Override public void renameItem(String itemCode, String newName) { }
        @Override public void setItemPrice(String itemCode, Money newPrice) { }
        @Override public void setItemRestockLevel(String itemCode, int level) { }
        @Override public void deleteItem(String itemCode) { }
        @Override public List<domain.inventory.Item> listAllItems() { return List.of(); }
        @Override public List<domain.inventory.Item> searchItemsByNameOrCode(String query) { return List.of(); }
        @Override public int restockLevel(String itemCode) { return 50; }
        @Override public void addBatch(String itemCode, java.time.LocalDate expiry, int qtyShelf, int qtyStore) { }
        @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) { }
        @Override public void updateBatchExpiry(long batchId, java.time.LocalDate newExpiry) { }
        @Override public void deleteBatch(long batchId) { }
        @Override public void addBatchDiscount(long batchId, domain.inventory.BatchDiscount.DiscountType type, Money value, String reason, String createdBy) { }
        @Override public void removeBatchDiscount(long discountId) { }
        @Override public java.util.Optional<domain.inventory.BatchDiscount> findActiveBatchDiscount(long batchId) { return java.util.Optional.empty(); }
        @Override public List<domain.inventory.BatchDiscount> findBatchDiscountsByBatch(long batchId) { return List.of(); }
        @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() { return List.of(); }
    }

    @Test
    @DisplayName("no discount and zero tax")
    void no_discount_zero_tax() {
        Bill b = new Bill("P-1");
        b.addLine(new BillLine("A", "Apple", Money.of(10.0), 3, List.of())); // 30
        PricingService svc = new PricingService(0.0, createMockInventoryService());
        svc.finalizePricing(b, null);
        assertEquals(Money.of(30.0), b.subtotal());
        assertEquals(Money.ZERO, b.discount());
        assertEquals(Money.ZERO, b.tax());
        assertEquals(Money.of(30.0), b.total());
    }

    @Test
    @DisplayName("discount capped at subtotal and tax applied on post-discount")
    void discount_cap_and_tax() {
        Bill b = new Bill("P-2");
        b.addLine(new BillLine("B", "Banana", Money.of(50.0), 2, List.of())); // 100
        PricingService svc = new PricingService(10.0, createMockInventoryService()); // 10%
        // discount 120 should cap at 100
        svc.finalizePricing(b, new FixedDiscount(Money.of(120.0), "FIX120"));
        assertEquals(Money.of(100.0), b.subtotal());
        assertEquals(Money.of(100.0), b.discount()); // capped
        assertEquals(Money.ZERO, b.tax()); // baseAfterDiscount = 0
        assertEquals(Money.ZERO, b.total());

        // smaller discount 20: tax 10% of 80 = 8
        svc.finalizePricing(b, new FixedDiscount(Money.of(20.0), "FIX20"));
        assertEquals(Money.of(100.0), b.subtotal());
        assertEquals(Money.of(20.0), b.discount());
        assertEquals(Money.of(8.0), b.tax()); // 10% of (100-20)=80
        assertEquals(Money.of(88.0), b.total()); // 80 + 8
    }
}
