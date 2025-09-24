package application.pos.controllers;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import domain.pricing.DiscountPolicy;
import application.pricing.PricingService;
import application.pricing.AutoDiscountService;

import java.util.List;

/**
 * Manages discount application and calculations
 */
public class DiscountManager {
    private final PricingService pricingService;
    private final AutoDiscountService autoDiscountService;
    private final InventoryManager inventoryManager;
    private DiscountPolicy activeDiscount = null;

    public DiscountManager(PricingService pricingService,
                          AutoDiscountService autoDiscountService,
                          InventoryManager inventoryManager) {
        this.pricingService = pricingService;
        this.autoDiscountService = autoDiscountService;
        this.inventoryManager = inventoryManager;
    }

    public void applyDiscount(Bill bill, DiscountPolicy policy) {
        this.activeDiscount = policy;
        pricingService.finalizePricing(bill, activeDiscount);
    }

    public DiscountPolicy getActiveDiscount() {
        return activeDiscount;
    }

    public void autoApplyBestDiscount(Bill bill) {
        if (bill == null || bill.lines().isEmpty()) {
            return;
        }

        DiscountPolicy batchDiscount = autoDiscountService.detectBatchDiscounts(bill);
        if (batchDiscount != null) {
            activeDiscount = batchDiscount;

            // Print discount notification
            BillLine lastLine = bill.lines().get(bill.lines().size() - 1);
            Money originalLineTotal = inventoryManager.getItemPrice(lastLine.itemCode()).multiply(lastLine.quantity());
            Money discountedLineTotal = lastLine.lineTotal();
            Money lineSavings = originalLineTotal.minus(discountedLineTotal);

            if (lineSavings.compareTo(Money.ZERO) > 0) {
                System.out.println("🎉 Batch Discount Applied - Total savings for " + lastLine.quantity() +
                    " items: LKR " + String.format("%.2f", lineSavings.asBigDecimal().doubleValue()));
            }
        }
    }

    public void resetDiscount() {
        activeDiscount = null;
    }

    public String getCurrentDiscountInfo(Bill bill) {
        if (bill == null || activeDiscount == null) {
            return "No discount applied";
        }

        if ("BATCH_DISCOUNT".equals(activeDiscount.code())) {
            Money actualDiscount = calculateActualBatchDiscountAmount(bill);
            return "Current Discount: BATCH_DISCOUNTS (Saves: " + actualDiscount + ")";
        }

        Money discountAmount = activeDiscount.computeDiscount(bill);
        return "Current Discount: " + activeDiscount.code() + " (Saves: " + discountAmount + ")";
    }

    public List<String> getAvailableDiscounts(Bill bill) {
        if (bill == null) {
            return List.of("No active bill");
        }
        return inventoryManager.getAvailableDiscounts(bill.lines());
    }

    private Money calculateActualBatchDiscountAmount(Bill bill) {
        Money totalOriginalPrice = Money.ZERO;
        Money totalDiscountedPrice = Money.ZERO;

        for (BillLine line : bill.lines()) {
            Money originalPrice = inventoryManager.getItemPrice(line.itemCode());
            totalOriginalPrice = totalOriginalPrice.plus(originalPrice.multiply(line.quantity()));
            totalDiscountedPrice = totalDiscountedPrice.plus(line.lineTotal());
        }

        return totalOriginalPrice.minus(totalDiscountedPrice);
    }
}
