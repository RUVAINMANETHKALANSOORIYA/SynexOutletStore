package domain.inventory;

import domain.common.Money;

public final class Item {
    private final long id;
    private final String code, name;
    private final Money unitPrice;
    private final int restockLevel;

    /** Old constructor stays valid (defaults restock level to 50). */
    public Item(long id, String code, String name, Money unitPrice) {
        this(id, code, name, unitPrice, 50);
    }

    public Item(long id, String code, String name, Money unitPrice, int restockLevel) {
        if (restockLevel < 0) throw new IllegalArgumentException("restockLevel must be >= 0");
        this.id = id;
        this.code = code;
        this.name = name;
        this.unitPrice = unitPrice;
        this.restockLevel = restockLevel;
    }

    public long id(){ return id; }
    public String code(){ return code; }
    public String name(){ return name; }
    public Money unitPrice(){ return unitPrice; }
    public int restockLevel(){ return restockLevel; }
}
