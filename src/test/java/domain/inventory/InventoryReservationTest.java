package domain.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryReservationTest {

    @Test
    @DisplayName("Create inventory reservation with valid parameters")
    void create_reservation_valid_parameters() {
        long batchId = 1L;
        String itemCode = "ITEM001";
        int quantity = 5;

        InventoryReservation reservation = new InventoryReservation(batchId, itemCode, quantity);

        assertEquals(batchId, reservation.batchId);
        assertEquals(itemCode, reservation.itemCode);
        assertEquals(quantity, reservation.quantity);
    }

    @Test
    @DisplayName("Create reservation with different batch")
    void create_reservation_different_batch() {
        InventoryReservation reservation = new InventoryReservation(2L, "ITEM002", 10);

        assertEquals(2L, reservation.batchId);
        assertEquals("ITEM002", reservation.itemCode);
        assertEquals(10, reservation.quantity);
    }

    @Test
    @DisplayName("Create reservation with zero quantity")
    void create_reservation_zero_quantity() {
        InventoryReservation reservation = new InventoryReservation(3L, "ITEM003", 0);

        assertEquals(3L, reservation.batchId);
        assertEquals("ITEM003", reservation.itemCode);
        assertEquals(0, reservation.quantity);
    }

    @Test
    @DisplayName("Reservation equality")
    void reservation_equality() {
        InventoryReservation reservation1 = new InventoryReservation(1L, "ITEM001", 5);
        InventoryReservation reservation2 = new InventoryReservation(1L, "ITEM001", 5);
        InventoryReservation reservation3 = new InventoryReservation(2L, "ITEM001", 5);

        assertEquals(reservation1.batchId, reservation2.batchId);
        assertEquals(reservation1.itemCode, reservation2.itemCode);
        assertEquals(reservation1.quantity, reservation2.quantity);

        assertNotEquals(reservation1.batchId, reservation3.batchId);
    }

    @Test
    @DisplayName("Reservation with large quantity")
    void reservation_large_quantity() {
        InventoryReservation reservation = new InventoryReservation(4L, "ITEM004", 1000);

        assertEquals(4L, reservation.batchId);
        assertEquals("ITEM004", reservation.itemCode);
        assertEquals(1000, reservation.quantity);
    }
}
