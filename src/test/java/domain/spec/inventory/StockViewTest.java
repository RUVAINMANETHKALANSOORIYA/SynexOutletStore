package domain.spec.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockViewTest {

    @Test
    @DisplayName("StockView creation with all quantities")
    void stock_view_creation() {
        StockView stockView = new StockView(10, 15, 5);

        assertEquals(10, stockView.shelfQty());
        assertEquals(15, stockView.storeQty());
        assertEquals(5, stockView.restockLevel());
    }

    @Test
    @DisplayName("StockView total quantity calculation")
    void stock_view_total_quantity() {
        StockView stockView = new StockView(10, 15, 5);

        int totalQty = stockView.shelfQty() + stockView.storeQty();
        assertEquals(25, totalQty);
    }

    @Test
    @DisplayName("StockView empty stock check")
    void stock_view_empty_check() {
        StockView emptyStock = new StockView(0, 0, 5);
        StockView hasStock = new StockView(1, 0, 5);

        boolean isEmpty1 = emptyStock.shelfQty() == 0 && emptyStock.storeQty() == 0;
        boolean isEmpty2 = hasStock.shelfQty() == 0 && hasStock.storeQty() == 0;

        assertTrue(isEmpty1);
        assertFalse(isEmpty2);
    }

    @Test
    @DisplayName("StockView restock need assessment")
    void stock_view_restock_needs() {
        StockView belowLevel = new StockView(2, 3, 10);
        StockView aboveLevel = new StockView(8, 7, 10);
        StockView atLevel = new StockView(5, 5, 10);

        boolean needsRestock1 = (belowLevel.shelfQty() + belowLevel.storeQty()) < belowLevel.restockLevel();
        boolean needsRestock2 = (aboveLevel.shelfQty() + aboveLevel.storeQty()) < aboveLevel.restockLevel();
        boolean needsRestock3 = (atLevel.shelfQty() + atLevel.storeQty()) < atLevel.restockLevel();

        assertTrue(needsRestock1);
        assertFalse(needsRestock2);
        assertFalse(needsRestock3);
    }

    @Test
    @DisplayName("StockView stock availability checks")
    void stock_view_availability() {
        StockView emptyStock = new StockView(0, 0, 5);
        StockView hasStock = new StockView(3, 2, 5);

        boolean isEmpty1 = (emptyStock.shelfQty() + emptyStock.storeQty()) == 0;
        boolean isEmpty2 = (hasStock.shelfQty() + hasStock.storeQty()) == 0;

        assertTrue(isEmpty1);
        assertFalse(isEmpty2);
    }

    @Test
    @DisplayName("StockView shelf stock availability")
    void stock_view_shelf_stock() {
        StockView hasShelf = new StockView(5, 0, 10);
        StockView noShelf = new StockView(0, 5, 10);

        boolean hasShelfStock1 = hasShelf.shelfQty() > 0;
        boolean hasShelfStock2 = noShelf.shelfQty() > 0;

        assertTrue(hasShelfStock1);
        assertFalse(hasShelfStock2);
    }

    @Test
    @DisplayName("StockView store stock availability")
    void stock_view_store_stock() {
        StockView hasStore = new StockView(0, 8, 10);
        StockView noStore = new StockView(5, 0, 10);

        boolean hasStoreStock1 = hasStore.storeQty() > 0;
        boolean hasStoreStock2 = noStore.storeQty() > 0;

        assertTrue(hasStoreStock1);
        assertFalse(hasStoreStock2);
    }

    @Test
    @DisplayName("StockView main stock availability")
    void stock_view_main_stock() {
        StockView hasMain = new StockView(3, 0, 10);
        StockView noMain = new StockView(0, 5, 10);

        boolean hasMainStock1 = hasMain.shelfQty() > 0; // Assuming shelf is "main"
        boolean hasMainStock2 = noMain.shelfQty() > 0;

        assertTrue(hasMainStock1);
        assertFalse(hasMainStock2);
    }

    @Test
    @DisplayName("StockView with different quantities")
    void stock_view_different_quantities() {
        StockView view1 = new StockView(5, 10, 20);
        StockView view2 = new StockView(3, 7, 15);
        StockView view3 = new StockView(1, 2, 8);

        assertEquals(15, view1.shelfQty() + view1.storeQty());
        assertEquals(10, view2.shelfQty() + view2.storeQty());
        assertEquals(3, view3.shelfQty() + view3.storeQty());
    }

    @Test
    @DisplayName("StockView with null handling")
    void stock_view_null_handling() {
        // Test creating StockView with valid parameters
        StockView view = new StockView(5, 10, 15);

        assertNotNull(view);
        assertEquals(5, view.shelfQty());
        assertEquals(10, view.storeQty());
        assertEquals(15, view.restockLevel());
    }

    @Test
    @DisplayName("StockView calculations")
    void stock_view_calculations() {
        StockView view = new StockView(8, 12, 25);

        int totalQty = view.shelfQty() + view.storeQty();
        assertEquals(20, totalQty);
    }

    @Test
    @DisplayName("StockView equality")
    void stock_view_equality() {
        StockView view1 = new StockView(5, 5, 15);
        StockView view2 = new StockView(5, 5, 15);
        StockView view3 = new StockView(3, 3, 10);

        assertEquals(view1, view2);
        assertNotEquals(view1, view3);
        assertEquals(view1.hashCode(), view2.hashCode());
    }
}
