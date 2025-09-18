package domain.inventory;

/**
 * Stub adapter for a barcode scanner.
 * In a real system this would integrate with a device SDK.
 */
public final class BarcodeReader implements ItemCodeReader {

    @Override
    public String readCode() {
        // For now, just return a simulated code
        return "SIMULATED_BARCODE_CODE";
    }
}
