package domain.spec.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpiryWithinDaysTest {

    @Test
    @DisplayName("AtOrBelowRestock matches when total <= restock level")
    void at_or_below_restock_matches() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView v = new StockView(3, 2, 5); // total = 5, level = 5
        assertTrue(spec.isSatisfiedBy(v));
    }

    @Test
    @DisplayName("AtOrBelowRestock does not match when total > restock level")
    void at_or_below_restock_not_match() {
        AtOrBelowRestock spec = new AtOrBelowRestock();
        StockView v = new StockView(4, 3, 5); // total = 7, level = 5
        assertFalse(spec.isSatisfiedBy(v));
    }

    @Test
    @DisplayName("AtOrBelowRestock equals/hashCode and toString")
    void equals_hash_toString() {
        AtOrBelowRestock a = new AtOrBelowRestock();
        AtOrBelowRestock b = new AtOrBelowRestock();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.toString().toLowerCase().contains("restock"));
    }
}
