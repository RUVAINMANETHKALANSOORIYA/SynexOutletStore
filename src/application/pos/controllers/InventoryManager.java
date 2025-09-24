package application.pos.controllers;

import domain.billing.BillLine;
import domain.common.Money;
import domain.inventory.InventoryReservation;
import domain.inventory.BatchDiscount;
import ports.in.InventoryService;
import application.inventory.InventoryAdminService;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Handles all inventory-related operations for the POS system
 */
public final class InventoryManager {
    private final InventoryService inventory;
    private final InventoryAdminService inventoryAdmin;

    public InventoryManager(InventoryService inventory, InventoryAdminService inventoryAdmin) {
        this.inventory = inventory;
        this.inventoryAdmin = inventoryAdmin;
    }

    /**
     * Reserve items by channel
     */
    public List<InventoryReservation> reserveItems(String itemCode, int quantity, String channel) {
        validateItemCode(itemCode);
        validateQuantity(quantity);
        validateItemExists(itemCode);
        validateStockAvailability(itemCode, quantity, channel);

        try {
            return inventory.reserveByChannel(itemCode, quantity, channel);
        } catch (IllegalStateException e) {
            throw new POSOperationException("Insufficient stock for item " + itemCode + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new POSOperationException("Inventory operation failed for item " + itemCode + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Failed to reserve item " + itemCode + ": " + e.getMessage(), e);
        }
    }

    /**
     * Smart reserve items with cross-channel logic
     */
    public InventoryService.SmartPick reserveItemsSmart(String itemCode, int quantity, String channel,
                                                       boolean approveUseOtherSide, boolean managerApprovedBackfill) {
        validateItemCode(itemCode);
        validateQuantity(quantity);
        validateBooleanParameters(approveUseOtherSide, managerApprovedBackfill);
        validateItemExists(itemCode);

        try {
            return inventory.reserveSmart(itemCode, quantity, channel, approveUseOtherSide, managerApprovedBackfill);
        } catch (IllegalStateException e) {
            throw new POSOperationException("Smart pick failed for item " + itemCode + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new POSOperationException("Inventory operation failed for item " + itemCode + " using smart pick: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new POSOperationException("Failed to add item " + itemCode + " using smart pick: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate the best price from reservations considering batch discounts
     */
    public Money calculateBestPrice(String itemCode, List<InventoryReservation> reservations, String channel) {
        // Validate input parameters
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new POSOperationException("Item code cannot be null or empty for price calculation");
        }

        if (reservations == null) {
            throw new POSOperationException("Reservations list cannot be null for price calculation");
        }

        if (reservations.isEmpty()) {
            System.out.println("⚠️ No reservations available for item " + itemCode + ", using base price");
        }

        Money basePrice;
        try {
            basePrice = inventory.priceOf(itemCode);
            if (basePrice == null) {
                throw new POSOperationException("Base price for item " + itemCode + " is null");
            }
        } catch (Exception e) {
            throw new POSOperationException("Failed to get base price for item " + itemCode + ": " + e.getMessage(), e);
        }

        Money bestPrice = basePrice;

        // Apply batch discounts only for in-store POS channel
        if (!"POS".equalsIgnoreCase(channel)) {
            System.out.println("⚠️ Batch discounts only apply to POS channel. Current channel: " + channel);
            return basePrice;
        }

        // If no inventory admin service available (backward compatibility), use base price
        if (inventoryAdmin == null) {
            System.out.println("⚠️ InventoryAdminService not available - no batch discounts");
            return basePrice;
        }

        // If no reservations, return base price
        if (reservations.isEmpty()) {
            return basePrice;
        }

        System.out.println("🔍 Checking batch discounts for item: " + itemCode + " (Base price: LKR " +
            String.format("%.2f", basePrice.asBigDecimal().doubleValue()) + ")");

        // Check each batch for discounts and find the best price
        boolean foundDiscount = false;
        Money maxSavingsPerItem = Money.ZERO;

        for (InventoryReservation reservation : reservations) {
            if (reservation == null) {
                System.err.println("Warning: Null reservation found for item " + itemCode + ", skipping");
                continue;
            }

            try {
                System.out.println("   Checking batch ID: " + reservation.batchId);
                Money batchPrice = inventoryAdmin.calculateDiscountedPrice(itemCode, reservation.batchId);

                if (batchPrice == null) {
                    System.err.println("Warning: Null price returned for batch " + reservation.batchId + ", using base price");
                    continue;
                }

                if (batchPrice.compareTo(bestPrice) < 0) {
                    Money savings = basePrice.minus(batchPrice);
                    if (savings.compareTo(maxSavingsPerItem) > 0) {
                        maxSavingsPerItem = savings;
                    }
                    bestPrice = batchPrice;
                    foundDiscount = true;
                    System.out.println("   ✅ Discounted price found: LKR " +
                        String.format("%.2f", batchPrice.asBigDecimal().doubleValue()) +
                        " (Save: LKR " + String.format("%.2f", savings.asBigDecimal().doubleValue()) + " per item)");
                } else {
                    System.out.println("   ❌ No discount for batch " + reservation.batchId +
                        " (Price: LKR " + String.format("%.2f", batchPrice.asBigDecimal().doubleValue()) + ")");
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to calculate discount for batch " + reservation.batchId +
                    " for item " + itemCode + ": " + e.getMessage());
                continue;
            }
        }

        if (!foundDiscount) {
            System.out.println("ℹ️ No batch discounts available for " + itemCode);
        }

        return bestPrice;
    }

    /**
     * Get item name
     */
    public String getItemName(String itemCode) {
        return inventory.itemName(itemCode);
    }

    /**
     * Get item price
     */
    public Money getItemPrice(String itemCode) {
        return inventory.priceOf(itemCode);
    }

    /**
     * Get available discounts for reservations
     */
    public List<String> getAvailableDiscounts(List<BillLine> lines) {
        if (inventoryAdmin == null) {
            return List.of("No batch discounts available");
        }

        List<String> discountDescriptions = new ArrayList<>();

        for (BillLine line : lines) {
            for (InventoryReservation reservation : line.reservations()) {
                Optional<BatchDiscount> discount = inventoryAdmin.findActiveBatchDiscount(reservation.batchId);
                if (discount.isPresent() && discount.get().isValidNow()) {
                    String description = String.format("%s: %s",
                        line.itemName(),
                        discount.get().getDescription());
                    if (!discountDescriptions.contains(description)) {
                        discountDescriptions.add(description);
                    }
                }
            }
        }

        if (discountDescriptions.isEmpty()) {
            return List.of("No batch discounts available");
        }

        return discountDescriptions;
    }

    /**
     * Commit shelf reservations
     */
    public void commitShelfReservations(List<InventoryReservation> reservations) {
        try {
            if (!reservations.isEmpty()) inventory.commitReservation(reservations);
        } catch (Exception e) {
            throw new POSOperationException("Failed to commit shelf reservations: " + e.getMessage(), e);
        }
    }

    /**
     * Commit store reservations
     */
    public void commitStoreReservations(List<InventoryReservation> reservations) {
        try {
            if (!reservations.isEmpty()) inventory.commitStoreReservation(reservations);
        } catch (Exception e) {
            throw new POSOperationException("Failed to commit store reservations: " + e.getMessage(), e);
        }
    }

    /**
     * Get stock quantities for stock level checking
     */
    public StockInfo getStockInfo(String itemCode) {
        try {
            int shelf = inventory.shelfQty(itemCode);
            int store = inventory.storeQty(itemCode);
            int threshold = Math.max(50, inventory.restockLevel(itemCode));
            return new StockInfo(shelf, store, threshold);
        } catch (Exception e) {
            throw new POSOperationException("Failed to get stock info for item " + itemCode + ": " + e.getMessage(), e);
        }
    }

    // Validation methods
    private void validateItemCode(String code) {
        if (code == null) {
            throw new POSOperationException("Item code cannot be null");
        }
        if (code.trim().isEmpty()) {
            throw new POSOperationException("Item code cannot be empty");
        }
        if (!code.matches("^[A-Za-z0-9_-]+$")) {
            throw new POSOperationException("Item code contains invalid characters. Only letters, numbers, underscore and hyphen are allowed");
        }
    }

    private void validateQuantity(int qty) {
        if (qty <= 0) {
            throw new POSOperationException("Quantity must be greater than zero. Provided: " + qty);
        }
        if (qty > 10000) {
            throw new POSOperationException("Quantity too large. Maximum allowed: 10000. Provided: " + qty);
        }
    }

    private void validateBooleanParameters(boolean approveUseOtherSide, boolean managerApprovedBackfill) {
        if (managerApprovedBackfill && !approveUseOtherSide) {
            throw new POSOperationException("Manager approved backfill requires approval to use other side");
        }
    }

    private void validateItemExists(String code) {
        try {
            inventory.itemName(code);
        } catch (NoSuchElementException e) {
            throw new POSOperationException("Item " + code + " does not exist in inventory");
        }
    }

    private void validateStockAvailability(String code, int qty, String channel) {
        int availableStock;
        if ("POS".equalsIgnoreCase(channel)) {
            availableStock = inventory.storeQty(code);
        } else {
            availableStock = inventory.shelfQty(code);
        }

        if (availableStock < qty) {
            throw new POSOperationException("Not enough stock available for item " + code + ". Requested: " + qty + ", Available: " + availableStock);
        }
    }

    /**
     * Stock information record
     */
    public record StockInfo(int shelf, int store, int threshold) {}

    /**
     * Custom exception for POS operations
     */
    public static class POSOperationException extends RuntimeException {
        public POSOperationException(String message) {
            super(message);
        }

        public POSOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
