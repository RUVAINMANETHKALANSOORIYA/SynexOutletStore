package domain.spec.inventory;

import domain.spec.Specification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtOrBelowRestockTest {

    @Test
    @DisplayName("AtOrBelowRestock uses max(50, restockLevel) for threshold and checks (shelf+store) <= threshold")
    void threshold_logic_max_50_or_restock() {
        AtOrBelowRestock spec = new AtOrBelowRestock();

        // restockLevel below 50 -> threshold becomes 50
        assertTrue(spec.isSatisfiedBy(new StockView(20, 20, 10))); // 40 <= 50
        assertTrue(spec.isSatisfiedBy(new StockView(25, 25, 10))); // 50 == 50
        assertFalse(spec.isSatisfiedBy(new StockView(40, 11, 10))); // 51 > 50

        // restockLevel above 50 -> threshold becomes restockLevel
        assertTrue(spec.isSatisfiedBy(new StockView(30, 30, 80))); // 60 <= 80
        assertTrue(spec.isSatisfiedBy(new StockView(40, 40, 80))); // 80 == 80
        assertFalse(spec.isSatisfiedBy(new StockView(60, 30, 80))); // 90 > 80
    }

    @Test
    @DisplayName("Specification default combinators and/or/not work with StockView")
    void specification_combinators() {
        Specification<StockView> lowShelf = v -> v.shelfQty() < 10;
        Specification<StockView> lowStore = v -> v.storeQty() < 10;
        Specification<StockView> eitherLow = lowShelf.or(lowStore);
        Specification<StockView> bothLow = lowShelf.and(lowStore);
        Specification<StockView> notLowShelf = lowShelf.not();

        StockView a = new StockView(5,  20, 50);
        StockView b = new StockView(12, 8,  50);
        StockView c = new StockView(12, 20, 50);

        assertTrue(eitherLow.isSatisfiedBy(a)); // shelf low
        assertTrue(eitherLow.isSatisfiedBy(b)); // store low
        assertFalse(eitherLow.isSatisfiedBy(c));

        assertFalse(bothLow.isSatisfiedBy(a)); // store not low
        assertFalse(bothLow.isSatisfiedBy(b)); // shelf not low
        assertFalse(bothLow.isSatisfiedBy(c)); // none low

        assertFalse(notLowShelf.isSatisfiedBy(a)); // shelf low -> not false
        assertTrue(notLowShelf.isSatisfiedBy(b));  // shelf ok -> not true
        assertTrue(notLowShelf.isSatisfiedBy(c));
    }
}
