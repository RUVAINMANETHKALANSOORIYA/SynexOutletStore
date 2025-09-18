package application.inventory;

import domain.inventory.InventoryReservation;
import ports.out.InventoryRepository;   // ✅ Correct import

import java.util.List;

public interface BatchSelector {
    /**
     * Selects batches for this item code and returns a plan (batchId -> quantity) that
     * satisfies the requested quantity or throws if impossible.
     * Implementations should not mutate repository state; just compute the plan.
     */
    List<InventoryReservation> selectFor(String itemCode, int requestedQty, InventoryRepository repo);
}
