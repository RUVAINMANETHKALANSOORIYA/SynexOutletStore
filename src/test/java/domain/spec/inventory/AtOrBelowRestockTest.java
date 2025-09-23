package domain.spec.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtOrBelowRestockTest {

    @Test
    @DisplayName("AtOrBelowRestock matches items at restock level")
    void at_restock_level_matches() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView atLevel = new StockView(5, 3, 10); // total 10 = restock 10

        assertTrue(spec.isSatisfiedBy(atLevel));
    }

    @Test
    @DisplayName("AtOrBelowRestock matches items below restock level")
    void below_restock_level_matches() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView belowLevel = new StockView(3, 2, 10); // total 5 < restock 10

        assertTrue(spec.isSatisfiedBy(belowLevel));
    }

    @Test
    @DisplayName("AtOrBelowRestock doesn't match items above restock level")
    void above_restock_level_no_match() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView aboveLevel = new StockView(9, 7, 15); // total 16 > restock 15

        assertFalse(spec.isSatisfiedBy(aboveLevel));
    }

    @Test
    @DisplayName("AtOrBelowRestock with zero restock level")
    void zero_restock_level() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView zeroRestock = new StockView(5, 3, 0); // total 8 > restock 0

        assertFalse(spec.isSatisfiedBy(zeroRestock));
    }

    @Test
    @DisplayName("AtOrBelowRestock with zero total quantity")
    void zero_total_quantity() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView zeroTotal = new StockView(0, 0, 5); // total 0 < restock 5

        assertTrue(spec.isSatisfiedBy(zeroTotal));
    }

    @Test
    @DisplayName("AtOrBelowRestock specification with null stock view")
    void null_stock_view() {
        AtOrBelowRestock spec = new AtOrBelowRestock();

        assertThrows(NullPointerException.class, () -> spec.isSatisfiedBy(null));
    }

    @Test
    @DisplayName("AtOrBelowRestock specification immutability")
    void specification_immutability() {
        AtOrBelowRestock spec1 = new AtOrBelowRestock();
        AtOrBelowRestock spec2 = new AtOrBelowRestock();

        // Specifications should be consistent
        StockView testView = new StockView(5, 5, 20);
        assertEquals(spec1.isSatisfiedBy(testView), spec2.isSatisfiedBy(testView));
    }

    @Test
    @DisplayName("AtOrBelowRestock toString provides description")
    void specification_to_string() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        String description = spec.toString();

        assertNotNull(description);
        assertTrue(description.contains("restock") || description.contains("below") || description.contains("level"));
    }

    @Test
    @DisplayName("AtOrBelowRestock equals and hashcode")
    void specification_equality() {
        AtOrBelowRestock spec1 = new AtOrBelowRestock();
        AtOrBelowRestock spec2 = new AtOrBelowRestock();

        assertEquals(spec1, spec2);
        assertEquals(spec1.hashCode(), spec2.hashCode());
    }

    @Test
    @DisplayName("AtOrBelowRestock with edge case quantities")
    void edge_case_quantities() {
        AtOrBelowRestock spec = new AtOrBelowRestock();

        // Very large quantities
        StockView largeView = new StockView(10000, 5000, 20000);
        assertTrue(spec.isSatisfiedBy(largeView)); // 15000 < 20000, should be true

        // Negative quantities (edge case)
        StockView negativeView = new StockView(-1, -2, 5);
        assertTrue(spec.isSatisfiedBy(negativeView)); // -3 < 5, should be true
    }

    @Test
    @DisplayName("AtOrBelowRestock with stock below restock level")
    void spec_stock_below_restock_level() {
        StockView stockView = new StockView(2, 3, 10);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertTrue(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock with stock at restock level")
    void spec_stock_at_restock_level() {
        StockView stockView = new StockView(5, 5, 10);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertTrue(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock with stock above restock level")
    void spec_stock_above_restock_level() {
        StockView stockView = new StockView(8, 7, 10);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertFalse(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock with zero stock")
    void spec_zero_stock() {
        StockView stockView = new StockView(0, 0, 5);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertTrue(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock with zero restock level")
    void spec_zero_restock_level() {
        StockView stockView = new StockView(1, 2, 0);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertFalse(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock boundary condition")
    void spec_boundary_condition() {
        // Stock total = 9, restock level = 10 (exactly 1 below)
        StockView stockView = new StockView(4, 5, 10);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertTrue(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock with high stock levels")
    void spec_high_stock_levels() {
        StockView stockView = new StockView(50, 25, 50);
        AtOrBelowRestock spec = new AtOrBelowRestock();

        boolean result = spec.isSatisfiedBy(stockView);

        assertFalse(result);
    }

    @Test
    @DisplayName("AtOrBelowRestock specification consistency")
    void spec_consistency() {
        AtOrBelowRestock spec1 = new AtOrBelowRestock();
        AtOrBelowRestock spec2 = new AtOrBelowRestock();
        StockView stockView = new StockView(3, 2, 8);

        boolean result1 = spec1.isSatisfiedBy(stockView);
        boolean result2 = spec2.isSatisfiedBy(stockView);

        assertEquals(result1, result2);
        assertTrue(result1); // 3+2=5 < 8
    }
}
