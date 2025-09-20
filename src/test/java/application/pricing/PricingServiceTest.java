package application.pricing;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import domain.pricing.DiscountPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private static class FixedDiscount implements DiscountPolicy {
        private final Money amount; private final String code;
        FixedDiscount(Money amount, String code){ this.amount = amount; this.code = code; }
        @Override public Money computeDiscount(Bill bill) { return amount; }
        @Override public String code() { return code; }
    }

    @Test
    @DisplayName("no discount and zero tax")
    void no_discount_zero_tax() {
        Bill b = new Bill("P-1");
        b.addLine(new BillLine("A", "Apple", Money.of(10.0), 3, List.of())); // 30
        PricingService svc = new PricingService(0.0);
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
        b.addLine(new BillLine("A", "Apple", Money.of(50.0), 2, List.of())); // 100
        PricingService svc = new PricingService(10.0); // 10%
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
        assertEquals(Money.of(8.0), b.tax());
        assertEquals(Money.of(88.0), b.total());
    }
}
