package infrastructure.security;

import application.auth.AuthService;
import domain.common.Money;
import domain.inventory.*;
import ports.out.InventoryRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public final class PermissionCheckedInventoryRepository implements InventoryRepository {
    private final InventoryRepository inner;
    private final AuthService auth;

    public PermissionCheckedInventoryRepository(InventoryRepository inner, AuthService auth) {
        this.inner = inner; this.auth = auth;
    }

    private void requireManagerOrAdmin() {
        var u = auth.currentUser();
        String role = (u == null) ? "" : u.role();
        boolean ok = "INVENTORY_MANAGER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        if (!ok) throw new SecurityException("Manager/Admin required for MAIN transfers.");
    }

    @Override public void moveMainToShelfFEFO(String itemCode, int qty) {
        requireManagerOrAdmin();
        inner.moveMainToShelfFEFO(itemCode, qty);
    }
    @Override public void moveMainToStoreFEFO(String itemCode, int qty) {
        requireManagerOrAdmin();
        inner.moveMainToStoreFEFO(itemCode, qty);
    }

    @Override public Optional<Item> findItemByCode(String itemCode) { return inner.findItemByCode(itemCode); }
    @Override public Money priceOf(String itemCode) { return inner.priceOf(itemCode); }
    @Override public List<Batch> findBatchesOnShelf(String itemCode) { return inner.findBatchesOnShelf(itemCode); }
    @Override public List<Batch> findBatchesInStore(String itemCode) { return inner.findBatchesInStore(itemCode); }
    @Override public void commitReservations(Iterable<InventoryReservation> reservations) { inner.commitReservations(reservations); }
    @Override public void commitStoreReservations(Iterable<InventoryReservation> reservations) { inner.commitStoreReservations(reservations); }
    @Override public int shelfQty(String itemCode) { return inner.shelfQty(itemCode); }
    @Override public int storeQty(String itemCode) { return inner.storeQty(itemCode); }
    @Override public int mainStoreQty(String itemCode) { return inner.mainStoreQty(itemCode); }
    @Override public void moveStoreToShelfFEFO(String itemCode, int qty) { inner.moveStoreToShelfFEFO(itemCode, qty); }
    @Override public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) { inner.addBatch(itemCode, expiry, qtyShelf, qtyStore); }
    @Override public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) { inner.editBatchQuantities(batchId, qtyShelf, qtyStore); }
    @Override public void updateBatchExpiry(long batchId, LocalDate newExpiry) { inner.updateBatchExpiry(batchId, newExpiry); }
    @Override public void deleteBatch(long batchId) { inner.deleteBatch(batchId); }
    @Override public void createItem(String code, String name, Money price) { inner.createItem(code, name, price); }
    @Override public void renameItem(String code, String newName) { inner.renameItem(code, newName); }
    @Override public void setItemPrice(String code, Money newPrice) { inner.setItemPrice(code, newPrice); }
    @Override public void deleteItem(String code) { inner.deleteItem(code); }
    @Override public int restockLevel(String itemCode) { return inner.restockLevel(itemCode); }
    @Override public void setItemRestockLevel(String itemCode, int level) { inner.setItemRestockLevel(itemCode, level); }
    @Override public List<Item> listAllItems() { return inner.listAllItems(); }
    @Override public List<Item> searchItemsByNameOrCode(String query) { return inner.searchItemsByNameOrCode(query); }

    // ===== Batch discount management (with permission checks) =====
    @Override public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value,
                                          String reason, String createdBy) {
        requireManagerOrAdmin();
        inner.addBatchDiscount(batchId, type, value, reason, createdBy);
    }

    @Override public void removeBatchDiscount(long discountId) {
        requireManagerOrAdmin();
        inner.removeBatchDiscount(discountId);
    }

    @Override public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) {
        return inner.findActiveBatchDiscount(batchId);
    }

    @Override public List<BatchDiscount> findBatchDiscountsByBatch(long batchId) {
        return inner.findBatchDiscountsByBatch(batchId);
    }

    @Override public List<BatchDiscountView> getAllBatchDiscountsWithDetails() {
        return inner.getAllBatchDiscountsWithDetails();
    }
}
