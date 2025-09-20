package domain.pricing;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PercentageDiscountTest {

    @Test
    @DisplayName("0% yields zero discount and code is stable")
    void zero_percent() {
        Bill b = new Bill("D-0");
        b.addLine(new BillLine("A", "Apple", Money.of(10.0), 3, List.of())); // 30
        PercentageDiscount d = new PercentageDiscount(0);
        assertEquals(Money.ZERO, d.computeDiscount(b));
        assertEquals("PERCENTAGE(0%)", d.code());
    }

    @Test
    @DisplayName("10% over multiple lines and rounding")
    void ten_percent_multiple_lines() {
        Bill b = new Bill("D-10");
        b.addLine(new BillLine("A", "A", Money.of(10.0), 1, List.of())); // 10.00
        b.addLine(new BillLine("B", "B", Money.of(9.99), 1, List.of())); // 9.99 -> subtotal 19.99
        PercentageDiscount d = new PercentageDiscount(10);
        assertEquals(Money.of(2.00), d.computeDiscount(b)); // 19.99 * 10% = 1.999 -> 2.00
    }

    @Test
    @DisplayName("100% equals subtotal")
    void hundred_percent_caps_to_subtotal() {
        Bill b = new Bill("D-100");
        b.addLine(new BillLine("A", "A", Money.of(12.34), 1, List.of()));
        PercentageDiscount d = new PercentageDiscount(100);
        assertEquals(b.computeSubtotal(), d.computeDiscount(b));
        assertEquals("PERCENTAGE(100%)", d.code());
    }

    @Test
    @DisplayName("percent guard: <0 or >100 rejected")
    void guard_invalid_percent() {
        assertThrows(IllegalArgumentException.class, () -> new PercentageDiscount(-1));
        assertThrows(IllegalArgumentException.class, () -> new PercentageDiscount(101));
    }
}
