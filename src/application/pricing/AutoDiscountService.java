package application.pricing;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import domain.pricing.DiscountPolicy;
import domain.inventory.InventoryReservation;
import domain.inventory.BatchDiscount;
import application.inventory.InventoryAdminService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service that automatically detects and applies batch-specific discounts
 * when items are added to a bill from discounted batches.
 */
public final class AutoDiscountService {

    private static String formatPercentage(double value) {
        // Format without decimal if it's a whole number (e.g., 10 -> "10%"),
        // otherwise keep up to one decimal (e.g., 12.5 -> "12.5%").
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return ((int) Math.rint(value)) + "%";
        }
        return String.format("%.1f%%", value);
    }

    private final InventoryAdminService inventoryAdmin;

    public AutoDiscountService(InventoryAdminService inventoryAdmin) {
        this.inventoryAdmin = inventoryAdmin;
    }

    /**
     * Creates a discount policy based on batch discounts present in the bill
     */
    public DiscountPolicy detectBatchDiscounts(Bill bill) {
        if (bill == null || bill.lines().isEmpty() || inventoryAdmin == null) {
            return null;
        }

        // Only apply batch discounts for in-store POS channel
        if (bill.channel() == null || !"POS".equalsIgnoreCase(bill.channel())) {
            return null;
        }

        return new BatchDiscountPolicy(bill, inventoryAdmin);
    }

    /**
     * Discount policy that applies batch-specific discounts to eligible items
     */
    private static class BatchDiscountPolicy implements DiscountPolicy {
        private final Bill bill;
        private final InventoryAdminService inventoryAdmin;

        public BatchDiscountPolicy(Bill bill, InventoryAdminService inventoryAdmin) {
            this.bill = bill;
            this.inventoryAdmin = inventoryAdmin;
        }

        @Override
        public String code() {
            return "BATCH_DISCOUNT";
        }

        @Override
        public String description() {
            return "Batch discounts applied to eligible items";
        }

        @Override
        public Money computeDiscount(Bill bill) {
            Money totalDiscount = Money.ZERO;

            for (BillLine line : bill.lines()) {
                for (InventoryReservation reservation : line.reservations()) {
                    Optional<BatchDiscount> discount = inventoryAdmin.findActiveBatchDiscount(reservation.batchId);
                    if (discount.isPresent() && discount.get().isValidNow()) {
                        Money originalPrice = inventoryAdmin.priceOf(line.itemCode());
                        Money discountedPrice = discount.get().calculateDiscountedPrice(originalPrice);
                        Money itemDiscount = originalPrice.minus(discountedPrice);
                        totalDiscount = totalDiscount.plus(itemDiscount.times(reservation.quantity));
                    }
                }
            }

            return totalDiscount;
        }

        @Override
        public void apply(Bill bill) {
            // The discount is already applied at the line item level in POSController
            // This method is called by PricingService to finalize the discount
        }
    }
}
