package domain.billing;

import domain.common.Money;
import domain.inventory.InventoryReservation;
import java.util.*;

public final class BillLine {
    private final String itemCode, itemName;
    private final Money unitPrice;
    private final int quantity;
    private final List<InventoryReservation> reservations;

    public BillLine(String code, String name, Money price, int qty, List<InventoryReservation> res){
        this.itemCode=code; this.itemName=name; this.unitPrice=price; this.quantity=qty;
        this.reservations = res==null? List.of() : new ArrayList<>(res);
    }
    public String itemCode(){ return itemCode; }
    public String itemName(){ return itemName; }
    public Money unitPrice(){ return unitPrice; }
    public int quantity(){ return quantity; }
    public List<InventoryReservation> reservations(){ return new ArrayList<>(reservations); }
    public Money lineTotal(){ return unitPrice.multiply(quantity); }
}
