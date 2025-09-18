package application.inventory;

/** Simple policy: if shelf < threshold, recommend restocking fixedQty. */
public interface ThresholdReorderPolicy {
    boolean needsRestock(int shelfQty, int threshold);
    default int quantityToMove(int storeQty, int fixedQty) {
        return Math.min(storeQty, fixedQty);
    }
}
