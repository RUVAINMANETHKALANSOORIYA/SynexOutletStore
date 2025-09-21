package domain.spec.inventory;

import domain.spec.Specification;

public final class AtOrBelowRestock implements Specification<StockView> {
    @Override
    public boolean isSatisfiedBy(StockView v) {
        int total = v.shelfQty() + v.storeQty();
        int threshold = Math.max(50, v.restockLevel());
        return total <= threshold;
    }
}
