package domain.pricing;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BogoPolicyTest {

    @Test
    @DisplayName("odd/even quantities compute free items correctly and code is stable")
    void bogo_odd_even() {
        Bill b = new Bill("BOGO-1");
        // line1: qty 5 -> free 2
        b.addLine(new BillLine("A", "A", Money.of(10.0), 5, List.of()));
        // line2: qty 4 -> free 2
        b.addLine(new BillLine("B", "B", Money.of(2.5), 4, List.of()));
        // total discount: 2*10 + 2*2.5 = 25.0
        BogoPolicy p = new BogoPolicy();
        assertEquals(Money.of(25.0), p.computeDiscount(b));
        assertEquals("BOGO", p.code());
    }

    @Test
    @DisplayName("zero quantities or empty bill yields zero discount")
    void bogo_zero_case() {
        Bill b = new Bill("BOGO-0");
        BogoPolicy p = new BogoPolicy();
        assertEquals(Money.ZERO, p.computeDiscount(b));

        b.addLine(new BillLine("C", "C", Money.of(3.0), 1, List.of()));
        assertEquals(Money.ZERO, p.computeDiscount(b)); // qty 1 -> free 0
    }
}
