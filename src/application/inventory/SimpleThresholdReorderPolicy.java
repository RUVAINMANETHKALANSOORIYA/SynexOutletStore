package application.inventory;


public final class SimpleThresholdReorderPolicy implements ThresholdReorderPolicy {

    @Override
    public boolean needsRestock(int shelfQty, int threshold) {
        // Handle negative values: negative stock should not trigger restock
        if (shelfQty < 0) return false;
        // Handle negative threshold: always needs restock if threshold is negative
        if (threshold < 0) return true;
        return shelfQty <= threshold;
    }

    /** Decide how many units to move in this operation. */
    @Override
    public int quantityToMove(int storeQty, int fixedQty) {
        if (storeQty <= 0 || fixedQty <= 0) return 0;
        return Math.min(storeQty, fixedQty);
    }
}
