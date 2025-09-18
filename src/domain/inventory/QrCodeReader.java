package domain.inventory;

/**
 * Stub adapter for a QR code scanner.
 * In a real system this would integrate with a camera/decoder library.
 */
public final class QrCodeReader implements ItemCodeReader {

    @Override
    public String readCode() {
        // For now, just return a simulated code
        return "SIMULATED_QR_CODE";
    }
}
