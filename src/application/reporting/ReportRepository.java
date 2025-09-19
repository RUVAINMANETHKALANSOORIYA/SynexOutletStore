package application.reporting;

import java.time.LocalDate;
import java.util.List;

public interface ReportRepository {

    // ==== already existing ====
    DailySalesRow dailySales(LocalDate day);
    List<BestSellerRow> bestSellers(LocalDate from, LocalDate to, int limit);
    List<RevenueRow> revenueByDay(LocalDate from, LocalDate to);

    // ==== NEW: Reshelving / Reorder / Stock / Bills ====
    /** Items whose shelf is below target and store has stock; suggest move qty. */
    List<ReshelvingRow> reshelvingSuggestions(int shelfTarget);

    /** Items whose (shelf + store) is below threshold. */
    List<ReorderRow> reorderBelow(int threshold);

    /** Batch-wise stock; pass null or empty to get all items. */
    List<StockBatchRow> stockByBatch(String itemCodeOrNull);

    /** Bill headers between [from, to]. */
    List<BillRow> billsBetween(LocalDate from, LocalDate to);

    /** Items at or below restock level. */
    List<RestockRow> restockAtOrBelowLevel();

    // ---- Records (DTOs) ----
    record DailySalesRow(LocalDate day, long bills, String revenue, String discounts, long items) {}
    record BestSellerRow(String itemCode, String name, long qtySold, String revenue) {}
    record RevenueRow(LocalDate day, String revenue) {}
    record RestockRow(String itemCode, String itemName, int shelfQty, int storeQty, int mainQty, int restockLevel) {}

    // New DTOs
    record ReshelvingRow(String itemCode, String name, int shelfQty, int storeQty, int suggestedMove) {}
    record ReorderRow(String itemCode, String name, int totalQty) {}
    record StockBatchRow(long batchId, String itemCode, String name, java.time.LocalDate expiry, int qtyOnShelf, int qtyInStore) {}
    record BillRow(
            String billNo, java.time.LocalDateTime createdAt, String userName, String channel,
            String paymentMethod, String subtotal, String discount, String tax, String total
    ) {}
}
