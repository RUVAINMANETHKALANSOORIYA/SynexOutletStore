package application.inventory;

import ports.out.InventoryRepository;


public final class RestockService {
    private final InventoryRepository repo;

    public RestockService(InventoryRepository repo) {
        this.repo = repo;
    }

    public void restockFixed(String itemCode, int fixedQty) {
        repo.findItemByCode(itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemCode));
        if (fixedQty <= 0) throw new IllegalArgumentException("fixedQty must be > 0");

        int store = repo.storeQty(itemCode);
        if (store <= 0) throw new IllegalStateException("No stock in store for " + itemCode);

        int move = Math.min(store, fixedQty);
        if (move > 0) repo.moveStoreToShelfFEFO(itemCode, move);
    }




//    public boolean restockIfBelow(String itemCode, int shelfThreshold, int fixedQty, ThresholdReorderPolicy policy) {
//        repo.findItemByCode(itemCode)
//                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemCode));
//        if (policy == null) throw new IllegalArgumentException("policy is required");
//        if (fixedQty <= 0) throw new IllegalArgumentException("fixedQty must be > 0");
//
//        int shelf = repo.shelfQty(itemCode);
//        if (!policy.needsRestock(shelf, shelfThreshold)) return false;
//
//        int store = repo.storeQty(itemCode);
//        if (store <= 0) throw new IllegalStateException("No stock in store for " + itemCode);
//
//        int move = policy.quantityToMove(store, fixedQty);
//        if (move <= 0) return false;
//
//        repo.moveStoreToShelfFEFO(itemCode, move);
//        return true;
//    }
//
//
//

    public void restockToTarget(String itemCode, int targetShelfQty) {
        repo.findItemByCode(itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemCode));
        if (targetShelfQty <= 0) throw new IllegalArgumentException("targetShelfQty must be > 0");

        int shelf = repo.shelfQty(itemCode);
        if (shelf >= targetShelfQty) return;

        int need  = targetShelfQty - shelf;
        int store = repo.storeQty(itemCode);
        if (store <= 0) throw new IllegalStateException("No stock in store for " + itemCode);

        int move = Math.min(store, need);
        if (move > 0) repo.moveStoreToShelfFEFO(itemCode, move);
    }
}
