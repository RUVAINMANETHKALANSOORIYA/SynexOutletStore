package application.inventory;

import domain.common.Money;
import ports.out.InventoryRepository;

import java.time.LocalDate;

public final class InventoryAdminService {
    private final InventoryRepository repo;

    public InventoryAdminService(InventoryRepository repo) {
        this.repo = repo;
    }

    // ---------- ITEM (catalog) management ----------
    public void addNewItem(String itemCode, String name, Money unitPrice) {
        requireItemCode(itemCode);
        requireName(name);
        requirePrice(unitPrice);
        // guard: avoid duplicates
        repo.findItemByCode(itemCode).ifPresent(i -> {
            throw new IllegalStateException("Item already exists: " + itemCode);
        });
        repo.createItem(itemCode, name, unitPrice);
    }

    public void renameItem(String itemCode, String newName) {
        ensureItemExists(itemCode);
        requireName(newName);
        repo.renameItem(itemCode, newName);
    }

    public void changeItemPrice(String itemCode, Money newPrice) {
        ensureItemExists(itemCode);
        requirePrice(newPrice);
        repo.setItemPrice(itemCode, newPrice);
    }

    public void deleteItem(String itemCode) {
        ensureItemExists(itemCode);
        repo.deleteItem(itemCode);
    }

    // ---------- BATCH management ----------
    public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {
        ensureItemExists(itemCode);
        if (qtyShelf < 0 || qtyStore < 0) throw new IllegalArgumentException("Quantities must be >= 0");
        repo.addBatch(itemCode, expiry, qtyShelf, qtyStore);
    }

    public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {
        if (qtyShelf < 0 || qtyStore < 0) throw new IllegalArgumentException("Quantities must be >= 0");
        repo.editBatchQuantities(batchId, qtyShelf, qtyStore);
    }

    public void updateBatchExpiry(long batchId, LocalDate newExpiry) {
        repo.updateBatchExpiry(batchId, newExpiry); // may be null to clear
    }

    public void deleteBatch(long batchId) {
        repo.deleteBatch(batchId);
    }

    public void moveStoreToShelfFEFO(String itemCode, int qty) {
        ensureItemExists(itemCode);
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        repo.moveStoreToShelfFEFO(itemCode, qty);
    }

    // ---------- helpers ----------
    private void ensureItemExists(String itemCode) {
        requireItemCode(itemCode);
        repo.findItemByCode(itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemCode));
    }

    private static void requireItemCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("itemCode is required");
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    }

    private static void requirePrice(Money price) {
        if (price == null || price.asBigDecimal().signum() < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
    }
}
