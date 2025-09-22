package domain.inventory;

import domain.common.Money;
import java.time.LocalDateTime;

public record BatchDiscount(
        long id,
        long batchId,
        DiscountType discountType,
        Money discountValue,
        String reason,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String createdBy,
        LocalDateTime createdAt,
        boolean isActive
) {
    public enum DiscountType {
        PERCENTAGE,     // discountValue is percentage (0-100)
        FIXED_AMOUNT    // discountValue is fixed money amount
    }

    /**
     * Calculate the discounted price for a given original price
     */
    public Money calculateDiscountedPrice(Money originalPrice) {
        if (!isActive || !isValidNow()) {
            return originalPrice;
        }

        return switch (discountType) {
            case PERCENTAGE -> {
                double percentage = discountValue.asBigDecimal().doubleValue();
                double discountAmount = originalPrice.asBigDecimal().doubleValue() * (percentage / 100.0);
                yield originalPrice.minus(Money.of(discountAmount));
            }
            case FIXED_AMOUNT -> originalPrice.minus(discountValue);
        };
    }

    /**
     * Check if discount is currently valid based on time
     */
    public boolean isValidNow() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = now.isAfter(validFrom) || now.isEqual(validFrom);
        boolean beforeEnd = validUntil == null || now.isBefore(validUntil);
        return afterStart && beforeEnd;
    }

    /**
     * Get discount description for display
     */
    public String getDescription() {
        return switch (discountType) {
            case PERCENTAGE -> String.format("%.1f%% off", discountValue.asBigDecimal().doubleValue());
            case FIXED_AMOUNT -> String.format("LKR %.2f off", discountValue.asBigDecimal().doubleValue());
        };
    }
}
