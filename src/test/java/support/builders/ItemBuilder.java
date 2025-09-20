package support.builders;

import domain.common.Money;
import domain.inventory.Item;

public class ItemBuilder {
    private long id = 1L;
    private String code = "SKU-1";
    private String name = "Item";
    private Money price = Money.of(10.0);
    private int restockLevel = 50;

    public ItemBuilder id(long id){ this.id = id; return this; }
    public ItemBuilder code(String code){ this.code = code; return this; }
    public ItemBuilder name(String name){ this.name = name; return this; }
    public ItemBuilder price(double price){ this.price = Money.of(price); return this; }
    public ItemBuilder price(Money price){ this.price = price; return this; }
    public ItemBuilder restockLevel(int level){ this.restockLevel = level; return this; }

    public Item build(){ return new Item(id, code, name, price, restockLevel); }
}