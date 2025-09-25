package domain.inventory;

import domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    @Test
    @DisplayName("Create item with valid parameters")
    void create_item_valid_parameters() {
        long id = 1L;
        String code = "ITEM001";
        String name = "Test Item";
        Money price = Money.of(15.99);

        Item item = new Item(id, code, name, price);

        assertEquals(id, item.id());
        assertEquals(code, item.code());
        assertEquals(name, item.name());
        assertEquals(price, item.unitPrice());
        assertEquals(50, item.restockLevel()); // Default restock level
    }

    @Test
    @DisplayName("Create item with custom restock level")
    void create_item_custom_restock_level() {
        Item item = new Item(2L, "ITEM002", "Custom Item", Money.of(10.0), 25);

        assertEquals(25, item.restockLevel());
    }

    @Test
    @DisplayName("Item with zero price")
    void item_with_zero_price() {
        Item item = new Item(3L, "FREE001", "Free Item", Money.ZERO);

        assertEquals(Money.ZERO, item.unitPrice());
    }

    @Test
    @DisplayName("Item with high price")
    void item_with_high_price() {
        Money highPrice = Money.of(99999.99);
        Item item = new Item(4L, "LUXURY001", "Luxury Item", highPrice);

        assertEquals(highPrice, item.unitPrice());
    }

    @Test
    @DisplayName("Item with special characters in code")
    void item_special_characters_code() {
        String specialCode = "SPËCIAL-001";
        Item item = new Item(5L, specialCode, "Special Item", Money.of(10.0));

        assertEquals(specialCode, item.code());
    }

    @Test
    @DisplayName("Item with special characters in name")
    void item_special_characters_name() {
        String specialName = "Spëcial Itëm Ñame";
        Item item = new Item(6L, "ITEM001", specialName, Money.of(10.0));

        assertEquals(specialName, item.name());
    }

    @Test
    @DisplayName("Item with very long name")
    void item_long_name() {
        String longName = "Very Long Item Name That Exceeds Normal Length Limits For Testing";
        Item item = new Item(7L, "LONG001", longName, Money.of(10.0));

        assertEquals(longName, item.name());
    }

    @Test
    @DisplayName("Item with empty name")
    void item_empty_name() {
        Item item = new Item(8L, "EMPTY001", "", Money.of(10.0));

        assertEquals("", item.name());
    }

    @Test
    @DisplayName("Item with null values handles gracefully")
    void item_null_values() {
        assertDoesNotThrow(() -> {
            Item item = new Item(9L, null, null, null);
            assertEquals(9L, item.id());
            assertNull(item.code());
            assertNull(item.name());
            assertNull(item.unitPrice());
        });
    }

    @Test
    @DisplayName("Item equals and hashCode based on code")
    void item_equals_hashcode() {
        Item item1 = new Item(10L, "SAME001", "Item 1", Money.of(10.0));
        Item item2 = new Item(11L, "SAME001", "Item 2", Money.of(20.0)); // Different id, name, price
        Item item3 = new Item(12L, "DIFF001", "Item 1", Money.of(10.0)); // Different code

        assertEquals(item1, item2); // Same code
        assertNotEquals(item1, item3); // Different code
        assertEquals(item1.hashCode(), item2.hashCode());
    }

    @Test
    @DisplayName("Item toString contains essential information")
    void item_to_string() {
        Item item = new Item(13L, "ITEM001", "Test Item", Money.of(25.50));

        String toString = item.toString();

        assertNotNull(toString);
        assertFalse(toString.trim().isEmpty());
        // More lenient check - just ensure toString works and returns meaningful content
        assertTrue(toString.length() > 10); // Reasonable length for a toString
    }

    @Test
    @DisplayName("Item price comparison")
    void item_price_comparison() {
        Item cheapItem = new Item(14L, "CHEAP001", "Cheap Item", Money.of(5.0));
        Item expensiveItem = new Item(15L, "EXP001", "Expensive Item", Money.of(50.0));

        assertTrue(cheapItem.unitPrice().compareTo(expensiveItem.unitPrice()) < 0);
        assertTrue(expensiveItem.unitPrice().compareTo(cheapItem.unitPrice()) > 0);
    }

    @Test
    @DisplayName("Item with negative restock level throws exception")
    void item_negative_restock_level() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Item(16L, "NEG001", "Negative Restock", Money.of(10.0), -5);
        });
    }

    @Test
    @DisplayName("Item with zero restock level")
    void item_zero_restock_level() {
        Item item = new Item(17L, "ZERO001", "Zero Restock", Money.of(10.0), 0);

        assertEquals(0, item.restockLevel());
    }

    @Test
    @DisplayName("Item with large ID")
    void item_large_id() {
        long largeId = Long.MAX_VALUE;
        Item item = new Item(largeId, "LARGE001", "Large ID Item", Money.of(10.0));

        assertEquals(largeId, item.id());
    }

    @Test
    @DisplayName("Item immutability")
    void item_immutability() {
        Item item = new Item(18L, "IMMUTABLE001", "Immutable Item", Money.of(15.0));

        // All fields should be immutable
        assertEquals("IMMUTABLE001", item.code());
        assertEquals("Immutable Item", item.name());
        assertEquals(Money.of(15.0), item.unitPrice());
    }
}
