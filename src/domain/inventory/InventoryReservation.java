package domain.inventory;

public final class InventoryReservation {
    public final long batchId;
    public final String itemCode;
    public final int quantity;
    public InventoryReservation(long batchId, String itemCode, int quantity){
        this.batchId=batchId; this.itemCode=itemCode; this.quantity=quantity;
    }
}
