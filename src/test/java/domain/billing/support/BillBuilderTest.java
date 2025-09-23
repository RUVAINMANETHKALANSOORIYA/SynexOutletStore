package domain.billing.support;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillBuilderTest {

    private BillBuilder builder;

    @BeforeEach
    void setup() {
        builder = new BillBuilder("TEST-001");
    }

    @Test
    @DisplayName("BillBuilder creates basic bill")
    void builder_creates_basic_bill() {
        Bill bill = builder.build();

        assertEquals("TEST-001", bill.number());
        assertNotNull(bill.lines());
        assertTrue(bill.lines().isEmpty());
    }

    @Test
    @DisplayName("BillBuilder with user and channel")
    void builder_with_user_channel() {
        Bill bill = new BillBuilder("TEST-002")
            .user("test_user")
            .channel("POS")
            .build();

        assertEquals("TEST-002", bill.number());
        assertEquals("test_user", bill.userName());
        assertEquals("POS", bill.channel());
    }

    @Test
    @DisplayName("BillBuilder with line items")
    void builder_with_line_items() {
        Bill bill = new BillBuilder("TEST-003")
            .line("ITEM001", "Test Item 1", Money.of(10.0), 2)
            .line("ITEM002", "Test Item 2", Money.of(15.0), 1)
            .build();

        assertEquals("TEST-003", bill.number());
        assertEquals(2, bill.lines().size());
        assertEquals("ITEM001", bill.lines().get(0).itemCode());
        assertEquals("Test Item 1", bill.lines().get(0).itemName());
    }

    @Test
    @DisplayName("BillBuilder with complete bill")
    void builder_complete_bill() {
        Bill bill = new BillBuilder("TEST-004")
            .user("cashier1")
            .channel("POS")
            .line("PROD001", "Product 1", Money.of(25.0), 3)
            .line("PROD002", "Product 2", Money.of(12.50), 2)
            .build();

        assertEquals("TEST-004", bill.number());
        assertEquals("cashier1", bill.userName());
        assertEquals("POS", bill.channel());
        assertEquals(2, bill.lines().size());
    }

    @Test
    @DisplayName("BillBuilder with empty user")
    void builder_empty_user() {
        Bill bill = new BillBuilder("TEST-005")
            .user("")
            .build();

        assertEquals("", bill.userName());
    }

    @Test
    @DisplayName("BillBuilder with null user")
    void builder_null_user() {
        Bill bill = new BillBuilder("TEST-006")
            .user(null)
            .build();

        assertNull(bill.userName());
    }

    @Test
    @DisplayName("BillBuilder with null channel")
    void builder_null_channel() {
        Bill bill = new BillBuilder("TEST-007")
            .channel(null)
            .build();

        assertNull(bill.channel());
    }

    @Test
    @DisplayName("BillBuilder method chaining")
    void builder_method_chaining() {
        BillBuilder chainedBuilder = new BillBuilder("TEST-008")
            .user("user1")
            .channel("ONLINE")
            .line("CHAIN001", "Chain Item", Money.of(5.0), 1);

        Bill bill = chainedBuilder.build();

        assertEquals("TEST-008", bill.number());
        assertEquals("user1", bill.userName());
        assertEquals("ONLINE", bill.channel());
        assertEquals(1, bill.lines().size());
    }

    @Test
    @DisplayName("BillBuilder multiple instances")
    void builder_multiple_instances() {
        BillBuilder builder1 = new BillBuilder("MULTI-001");
        BillBuilder builder2 = new BillBuilder("MULTI-002");

        Bill bill1 = builder1.user("user1").build();
        Bill bill2 = builder2.user("user2").build();

        assertEquals("MULTI-001", bill1.number());
        assertEquals("MULTI-002", bill2.number());
        assertEquals("user1", bill1.userName());
        assertEquals("user2", bill2.userName());
    }

    @Test
    @DisplayName("BillBuilder with zero quantity line")
    void builder_zero_quantity_line() {
        Bill bill = new BillBuilder("TEST-009")
            .line("ZERO001", "Zero Qty Item", Money.of(10.0), 0)
            .build();

        assertEquals(1, bill.lines().size());
        assertEquals(0, bill.lines().get(0).quantity());
    }

    @Test
    @DisplayName("BillBuilder with negative price")
    void builder_negative_price() {
        assertDoesNotThrow(() -> {
            Bill bill = new BillBuilder("TEST-010")
                .line("NEG001", "Negative Price", Money.of(-5.0), 1)
                .build();

            assertEquals(1, bill.lines().size());
        });
    }

    @Test
    @DisplayName("BillBuilder line order preservation")
    void builder_line_order() {
        Bill bill = new BillBuilder("TEST-011")
            .line("FIRST", "First Item", Money.of(10.0), 1)
            .line("SECOND", "Second Item", Money.of(20.0), 1)
            .line("THIRD", "Third Item", Money.of(30.0), 1)
            .build();

        assertEquals(3, bill.lines().size());
        assertEquals("FIRST", bill.lines().get(0).itemCode());
        assertEquals("SECOND", bill.lines().get(1).itemCode());
        assertEquals("THIRD", bill.lines().get(2).itemCode());
    }

    @Test
    @DisplayName("BillBuilder toString representation")
    void builder_string_representation() {
        String toString = builder.toString();
        assertNotNull(toString);
        assertFalse(toString.isEmpty());
    }
}
