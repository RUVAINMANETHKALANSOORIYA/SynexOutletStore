package domain.spec.inventory;

import domain.spec.Specification;

public final class AtOrBelowRestock implements Specification<StockView> {
    @Override
    public boolean isSatisfiedBy(StockView v) {
        int total = v.shelfQty() + v.storeQty();
        return total <= v.restockLevel();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AtOrBelowRestock;
    }

    @Override
    public int hashCode() {
        return AtOrBelowRestock.class.hashCode();
    }

    @Override
    public String toString() {
        return "AtOrBelowRestock: items at or below restock level";
    }
}
