package application.inventory;

import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import ports.out.InventoryRepository;   // ✅ Correct import

import java.util.ArrayList;
import java.util.List;

/** FEFO: earliest expiry first (null expiry goes last). */
public final class FefoBatchSelector implements BatchSelector {
    @Override
    public List<InventoryReservation> selectFor(String itemCode, int requestedQty, InventoryRepository repo) {
        if (requestedQty <= 0) throw new IllegalArgumentException("qty must be > 0");
        // ensure item exists
        repo.findItemByCode(itemCode)
                .orElseThrow(() -> new java.util.NoSuchElementException("Unknown item: " + itemCode));

        var batches = repo.findBatchesOnShelf(itemCode); // sorted by FEFO in JDBC impl
        int remaining = requestedQty;
        List<InventoryReservation> plan = new ArrayList<>();

        for (Batch b : batches) {
            if (remaining == 0) break;
            int take = Math.min(b.qtyOnShelf(), remaining);
            if (take > 0) {
                // plan reservation (don’t mutate batch qty here)
                plan.add(new InventoryReservation(b.id(), itemCode, take));
                remaining -= take;
            }
        }

        if (remaining > 0) {
            throw new IllegalStateException("Not enough quantity on shelf for " + itemCode);
        }
        return plan;
    }
}
