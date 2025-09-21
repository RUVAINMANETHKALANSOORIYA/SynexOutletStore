package domain.inventory;

import java.time.LocalDate;

public final class Batch {
    private final long id;
    private final String itemCode;
    private final LocalDate expiry;
    private int qtyOnShelf, qtyInStore, qtyInMain;

    public Batch(long id, String itemCode, LocalDate expiry, int qtyOnShelf, int qtyInStore){
        this(id, itemCode, expiry, qtyOnShelf, qtyInStore, 0);
    }

    public Batch(long id, String itemCode, LocalDate expiry, int qtyOnShelf, int qtyInStore, int qtyInMain){
        this.id=id; this.itemCode=itemCode; this.expiry=expiry;
        this.qtyOnShelf=qtyOnShelf; this.qtyInStore=qtyInStore; this.qtyInMain = qtyInMain;
    }

    public long id(){ return id; }
    public String itemCode(){ return itemCode; }
    public LocalDate expiryDate(){ return expiry; }
    public int qtyOnShelf(){ return qtyOnShelf; }
    public int qtyInStore(){ return qtyInStore; }
    public int qtyInMain(){ return qtyInMain; }

    public int takeFromShelf(int qty){
        int t = Math.min(qtyOnShelf, qty);
        qtyOnShelf -= t;
        return t;
    }

    public int takeFromStore(int qty){
        int t = Math.min(qtyInStore, qty);
        qtyInStore -= t;
        return t;
    }
}
