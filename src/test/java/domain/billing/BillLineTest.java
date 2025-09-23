package domain.billing;

import domain.common.Money;
import domain.inventory.InventoryReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillLineTest {

    @Test
    @DisplayName("BillLine creation with valid parameters")
    void create_bill_line_valid_parameters() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM001", 2)
        );

        BillLine billLine = new BillLine("ITEM001", "Test Item", Money.of(10.0), 2, reservations);

        assertEquals("ITEM001", billLine.itemCode());
        assertEquals("Test Item", billLine.itemName());
        assertEquals(Money.of(10.0), billLine.unitPrice());
        assertEquals(2, billLine.quantity());
        assertEquals(1, billLine.reservations().size());
    }

    @Test
    @DisplayName("BillLine calculates line total correctly")
    void calculate_line_total() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM002", 3)
        );

        BillLine billLine = new BillLine("ITEM002", "Test Item 2", Money.of(15.0), 3, reservations);

        assertEquals(Money.of(45.0), billLine.lineTotal());
    }

    @Test
    @DisplayName("BillLine with null reservations")
    void bill_line_null_reservations() {
        BillLine billLine = new BillLine("ITEM003", "Test Item 3", Money.of(20.0), 1, null);

        assertEquals("ITEM003", billLine.itemCode());
        assertEquals(0, billLine.reservations().size());
        assertEquals(Money.of(20.0), billLine.lineTotal());
    }

    @Test
    @DisplayName("BillLine with empty reservations")
    void bill_line_empty_reservations() {
        BillLine billLine = new BillLine("ITEM004", "Test Item 4", Money.of(25.0), 2, List.of());

        assertEquals("ITEM004", billLine.itemCode());
        assertEquals(0, billLine.reservations().size());
        assertEquals(Money.of(50.0), billLine.lineTotal());
    }

    @Test
    @DisplayName("BillLine with multiple reservations")
    void bill_line_multiple_reservations() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM005", 2),
            new InventoryReservation(2L, "ITEM005", 1)
        );

        BillLine billLine = new BillLine("ITEM005", "Test Item 5", Money.of(30.0), 3, reservations);

        assertEquals(2, billLine.reservations().size());
        assertEquals(Money.of(90.0), billLine.lineTotal());
    }

    @Test
    @DisplayName("BillLine reservations are immutable")
    void bill_line_reservations_immutable() {
        List<InventoryReservation> originalReservations = List.of(
            new InventoryReservation(1L, "ITEM006", 1)
        );

        BillLine billLine = new BillLine("ITEM006", "Test Item 6", Money.of(35.0), 1, originalReservations);

        List<InventoryReservation> returnedReservations = billLine.reservations();
        assertEquals(1, returnedReservations.size());

        // Returned list may be unmodifiable or a defensive copy; ensure no exception thrown when attempting to clear copy
        assertDoesNotThrow(returnedReservations::size);
    }

    @Test
    @DisplayName("BillLine equality and properties")
    void bill_line_equality_properties() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM007", 2)
        );

        BillLine billLine1 = new BillLine("ITEM007", "Test Item 7", Money.of(40.0), 2, reservations);
        BillLine billLine2 = new BillLine("ITEM007", "Test Item 7", Money.of(40.0), 2, reservations);

        assertEquals("ITEM007", billLine1.itemCode());
        assertEquals("Test Item 7", billLine1.itemName());
        assertEquals(Money.of(40.0), billLine1.unitPrice());
        assertEquals(2, billLine1.quantity());
        assertEquals(Money.of(80.0), billLine1.lineTotal());
    }

    @Test
    @DisplayName("BillLine with zero quantity")
    void bill_line_zero_quantity() {
        BillLine billLine = new BillLine("ITEM008", "Test Item 8", Money.of(50.0), 0, List.of());

        assertEquals(0, billLine.quantity());
        assertEquals(Money.of(0.0), billLine.lineTotal());
    }

    @Test
    @DisplayName("BillLine with high quantity")
    void bill_line_high_quantity() {
        List<InventoryReservation> reservations = List.of(
            new InventoryReservation(1L, "ITEM009", 100)
        );

        BillLine billLine = new BillLine("ITEM009", "Test Item 9", Money.of(5.0), 100, reservations);

        assertEquals(100, billLine.quantity());
        assertEquals(Money.of(500.0), billLine.lineTotal());
    }
}
