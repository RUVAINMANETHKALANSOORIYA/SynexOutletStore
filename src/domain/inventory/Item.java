package domain.inventory;

import domain.common.Money;

public final class Item {
    private final long id;
    private final String code, name;
    private final Money unitPrice;

    public Item(long id, String code, String name, Money unitPrice) {
        this.id=id; this.code=code; this.name=name; this.unitPrice=unitPrice;
    }
    public long id(){ return id; }
    public String code(){ return code; }
    public String name(){ return name; }
    public Money unitPrice(){ return unitPrice; }
}
