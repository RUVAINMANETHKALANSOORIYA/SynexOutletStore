package domain.inventory;


public final class QrCodeReader implements ItemCodeReader {

    @Override
    public String readCode() {
        // For now, just return a simulated code
        return "SIMULATED_QR_CODE";
    }
}
