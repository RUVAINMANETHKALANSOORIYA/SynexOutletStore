package application.reporting;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ReportPrinter {
    // ===== Existing DTOs =====
    record DailySales(LocalDate day, long bills, String revenue, String discounts, long itemsSold) {}
    record BestSeller(String itemCode, String itemName, long qtySold, String revenue) {}
    record RevenuePoint(LocalDate day, String revenue) {}

    // ===== Existing print methods =====
    void printDailySales(DailySales s);
    void printBestSellers(LocalDate from, LocalDate to, List<BestSeller> list);
    void printRevenueSeries(LocalDate from, LocalDate to, List<RevenuePoint> series);

    // ===== New DTOs =====
    /** For moving stock from STORE -> SHELF toward a target level. */
    record ReshelvingRow(String itemCode, String itemName, int shelfQty, int storeQty, int suggestedMove) {}

    /** Items that need reordering because total (shelf+store) is below a threshold. */
    record ReorderRow(String itemCode, String itemName, int totalQty) {}

    /** Batch-level stock snapshot. */
    record StockBatchRow(long batchId, String itemCode, String itemName,
                         LocalDate expiry, int qtyOnShelf, int qtyInStore) {}

    /** Bill headers for a date range. */
    record BillRow(String billNo, LocalDateTime createdAt, String userName, String channel,
                   String paymentMethod, String subtotal, String discount, String tax, String total) {}

    record RestockRow(String itemCode, String itemName, int shelfQty, int storeQty, int mainQty, int restockLevel) {}
    void printRestock(List<RestockRow> rows);


    // ===== New print methods (signatures match ReportingService calls) =====
    void printReshelving(List<ReshelvingRow> rows, int shelfTarget);
    void printReorder(List<ReorderRow> rows, int threshold);
    void printStock(List<StockBatchRow> rows, String itemCodeOrNull);
    void printBills(List<BillRow> rows, LocalDate from, LocalDate to);
}
