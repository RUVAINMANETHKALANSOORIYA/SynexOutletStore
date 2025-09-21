package application.inventory;

public interface ThresholdReorderPolicy {
    boolean needsRestock(int shelfQty, int threshold);
    default int quantityToMove(int storeQty, int fixedQty) {
        return Math.min(storeQty, fixedQty);
    }
}
