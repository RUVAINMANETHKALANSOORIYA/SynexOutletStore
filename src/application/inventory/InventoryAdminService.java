package application.inventory;

import domain.common.Money;
import domain.inventory.Item;
import domain.inventory.BatchDiscount;
import ports.out.InventoryRepository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class InventoryAdminService {
    private final InventoryRepository repo;

    public InventoryAdminService(InventoryRepository repo) {
        this.repo = repo;
    }

    // ===== Item CRUD =====
    public void addNewItem(String code, String name, Money price) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (price == null || price.asBigDecimal().signum() < 0) throw new IllegalArgumentException("price must be >= 0");
        repo.createItem(code.trim(), name.trim(), price);
    }

    public Optional<Item> getItem(String code) {
        return repo.findItemByCode(code);
    }

    public List<Item> listItems() {
        return repo.listAllItems();
    }

    public List<Item> searchItems(String query) {
        return repo.searchItemsByNameOrCode(query);
    }

    public void renameItem(String code, String newName) {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("name required");
        repo.renameItem(code, newName.trim());
    }

    public void setItemPrice(String code, Money newPrice) {
        if (newPrice == null || newPrice.asBigDecimal().signum() < 0) throw new IllegalArgumentException("price must be >= 0");
        repo.setItemPrice(code, newPrice);
    }

    public void setRestockLevel(String code, int level) {
        if (level < 0) throw new IllegalArgumentException("level must be >= 0");
        repo.setItemRestockLevel(code, level);
    }

    public void deleteItem(String code) {
        repo.deleteItem(code);
    }

    // ===== Existing batch/admin passthroughs kept as-is =====
    public void addBatch(String itemCode, LocalDate expiry, int qtyShelf, int qtyStore) {
        repo.addBatch(itemCode, expiry, qtyShelf, qtyStore);
    }
    public void editBatchQuantities(long batchId, int qtyShelf, int qtyStore) {
        repo.editBatchQuantities(batchId, qtyShelf, qtyStore);
    }
    public void updateBatchExpiry(long batchId, LocalDate newExpiry) {
        repo.updateBatchExpiry(batchId, newExpiry);
    }
    public void deleteBatch(long batchId) {
        repo.deleteBatch(batchId);
    }
    public void moveStoreToShelfFEFO(String code, int qty) { repo.moveStoreToShelfFEFO(code, qty); }
    public void moveMainToShelfFEFO(String code, int qty) { repo.moveMainToShelfFEFO(code, qty); }
    public void moveMainToStoreFEFO(String code, int qty) { repo.moveMainToStoreFEFO(code, qty); }

    // ===== NEW: Batch discount management =====
    /**
     * Add discount to a specific batch (for close expiry, overstock, etc.)
     */
    public void addBatchDiscount(long batchId, BatchDiscount.DiscountType type, Money value,
                                String reason, String createdBy) {
        // Validate discount parameters
        if (value.asBigDecimal().doubleValue() <= 0) {
            throw new IllegalArgumentException("Discount value must be positive");
        }
        if (type == BatchDiscount.DiscountType.PERCENTAGE && value.asBigDecimal().doubleValue() > 100) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100%");
        }

        repo.addBatchDiscount(batchId, type, value, reason, createdBy);
    }

    /**
     * Remove (deactivate) a batch discount
     */
    public void removeBatchDiscount(long discountId) {
        repo.removeBatchDiscount(discountId);
    }

    /**
     * Get all active batch discounts with product details for management view
     */
    public List<InventoryRepository.BatchDiscountView> getAllBatchDiscountsWithDetails() {
        return repo.getAllBatchDiscountsWithDetails();
    }

    /**
     * Find active discount for a specific batch
     */
    public Optional<BatchDiscount> findActiveBatchDiscount(long batchId) {
        return repo.findActiveBatchDiscount(batchId);
    }

    /**
     * Expose base price for use in discount computations
     */
    public Money priceOf(String itemCode) {
        return repo.priceOf(itemCode);
    }

    /**
     * Calculate discounted price for an item from a specific batch
     */
    public Money calculateDiscountedPrice(String itemCode, long batchId) {
        Money basePrice = repo.priceOf(itemCode);
        Optional<BatchDiscount> discount = repo.findActiveBatchDiscount(batchId);

        if (discount.isPresent() && discount.get().isValidNow()) {
            return discount.get().calculateDiscountedPrice(basePrice);
        }

        return basePrice; // No discount or expired discount
    }
}
