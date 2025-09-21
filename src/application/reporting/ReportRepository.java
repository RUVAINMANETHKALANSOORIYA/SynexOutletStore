package application.reporting;

import java.time.LocalDate;
import java.util.List;

public interface ReportRepository {

    DailySalesRow dailySales(LocalDate day);
    List<BestSellerRow> bestSellers(LocalDate from, LocalDate to, int limit);
    List<RevenueRow> revenueByDay(LocalDate from, LocalDate to);

    List<ReshelvingRow> reshelvingSuggestions(int shelfTarget);

    List<ReorderRow> reorderBelow(int threshold);

    List<StockBatchRow> stockByBatch(String itemCodeOrNull);

    List<BillRow> billsBetween(LocalDate from, LocalDate to);

    List<RestockRow> restockAtOrBelowLevel();

    record DailySalesRow(LocalDate day, long bills, String revenue, String discounts, long items) {}
    record BestSellerRow(String itemCode, String name, long qtySold, String revenue) {}
    record RevenueRow(LocalDate day, String revenue) {}
    record RestockRow(String itemCode, String itemName, int shelfQty, int storeQty, int mainQty, int restockLevel) {}

    record ReshelvingRow(String itemCode, String name, int shelfQty, int storeQty, int suggestedMove) {}
    record ReorderRow(String itemCode, String name, int totalQty) {}
    record StockBatchRow(long batchId, String itemCode, String name, java.time.LocalDate expiry, int qtyOnShelf, int qtyInStore) {}
    record BillRow(
            String billNo, java.time.LocalDateTime createdAt, String userName, String channel,
            String paymentMethod, String subtotal, String discount, String tax, String total
    ) {}
}
