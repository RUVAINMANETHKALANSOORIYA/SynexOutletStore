package domain.inventory;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatchDiscountTest {

    @Test
    @DisplayName("record components are accessible and correct")
    void record_components_access() {
        BatchDiscount d = new BatchDiscount(
                1L, 100L,
                BatchDiscount.DiscountType.PERCENTAGE,
                Money.of(15.0),
                "Test discount",
                java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(30),
                "admin",
                java.time.LocalDateTime.now().minusDays(1),
                true
        );
        assertEquals(1L, d.id());
        assertEquals(100L, d.batchId());
        assertEquals(BatchDiscount.DiscountType.PERCENTAGE, d.discountType());
        assertEquals(Money.of(15.0), d.discountValue());
        assertEquals("Test discount", d.reason());
        assertTrue(d.isActive());
        assertTrue(d.isValidNow());
    }

    @Test
    @DisplayName("calculateDiscountedPrice applies percentage correctly")
    void percentage_discounted_price() {
        BatchDiscount d = new BatchDiscount(
                2L, 101L,
                BatchDiscount.DiscountType.PERCENTAGE,
                Money.of(10.0),
                "10% off",
                java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(1),
                "mgr", java.time.LocalDateTime.now().minusDays(1), true
        );
        Money original = Money.of(200.0);
        Money discounted = d.calculateDiscountedPrice(original);
        assertEquals(Money.of(180.0), discounted);
    }

    @Test
    @DisplayName("calculateDiscountedPrice applies fixed amount correctly and clamps to zero")
    void fixed_amount_discounted_price_and_clamp() {
        BatchDiscount d = new BatchDiscount(
                3L, 102L,
                BatchDiscount.DiscountType.FIXED_AMOUNT,
                Money.of(50.0),
                "LKR 50 off",
                java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(1),
                "mgr", java.time.LocalDateTime.now().minusDays(1), true
        );
        Money original = Money.of(40.0);
        Money discounted = d.calculateDiscountedPrice(original);
        assertEquals(Money.ZERO, discounted);
    }

    @Test
    @DisplayName("inactive or out-of-window discounts do not apply")
    void inactive_or_invalid_not_applied() {
        BatchDiscount inactive = new BatchDiscount(
                4L, 103L,
                BatchDiscount.DiscountType.FIXED_AMOUNT,
                Money.of(5.0),
                "inactive",
                java.time.LocalDateTime.now().minusDays(2),
                java.time.LocalDateTime.now().plusDays(2),
                "mgr", java.time.LocalDateTime.now().minusDays(2), false
        );
        assertEquals(Money.of(100.0), inactive.calculateDiscountedPrice(Money.of(100.0)));

        BatchDiscount notYet = new BatchDiscount(
                5L, 104L,
                BatchDiscount.DiscountType.PERCENTAGE,
                Money.of(10.0),
                "future",
                java.time.LocalDateTime.now().plusDays(1),
                java.time.LocalDateTime.now().plusDays(10),
                "mgr", java.time.LocalDateTime.now(), true
        );
        assertEquals(Money.of(100.0), notYet.calculateDiscountedPrice(Money.of(100.0)));
    }
}
