package domain.inventory;

/** Port interface for reading an item code (typed, barcode, QR, etc.) */
public interface ItemCodeReader {
    String readCode();
}
