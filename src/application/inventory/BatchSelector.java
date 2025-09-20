package application.inventory;

import domain.inventory.InventoryReservation;
import ports.out.InventoryRepository;   // ✅ Correct import

import java.util.List;

public interface BatchSelector {

    List<InventoryReservation> selectFor(String itemCode, int requestedQty, InventoryRepository repo);
}
