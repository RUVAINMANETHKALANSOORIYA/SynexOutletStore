package domain.inventory;

import java.time.LocalDate;

public final class Batch {
    private final long id;
    private final String itemCode;
    private final LocalDate expiry;
    private int qtyOnShelf, qtyInStore;

    public Batch(long id, String itemCode, LocalDate expiry, int qtyOnShelf, int qtyInStore){
        this.id=id; this.itemCode=itemCode; this.expiry=expiry; this.qtyOnShelf=qtyOnShelf; this.qtyInStore=qtyInStore;
    }
    public long id(){ return id; }
    public String itemCode(){ return itemCode; }
    public LocalDate expiryDate(){ return expiry; }
    public int qtyOnShelf(){ return qtyOnShelf; }
    public int qtyInStore(){ return qtyInStore; }

    public int takeFromShelf(int qty){
        int t = Math.min(qtyOnShelf, qty);
        qtyOnShelf -= t;
        return t;
    }
}
