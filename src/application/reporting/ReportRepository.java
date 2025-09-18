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

    // ---- Records (DTOs) ----
    public record DailySalesRow(LocalDate day, long bills, String revenue, String discounts, long items) {}
    public record BestSellerRow(String itemCode, String name, long qtySold, String revenue) {}
    public record RevenueRow(LocalDate day, String revenue) {}
    record RestockRow(String itemCode, String itemName, int shelfQty, int storeQty, int mainQty, int restockLevel) {}
    List<RestockRow> restockAtOrBelowLevel();

    // New DTOs
    public record ReshelvingRow(String itemCode, String name, int shelfQty, int storeQty, int suggestedMove) {}
    public record ReorderRow(String itemCode, String name, int totalQty) {}
    public record StockBatchRow(long batchId, String itemCode, String name, java.time.LocalDate expiry, int qtyOnShelf, int qtyInStore) {}
    public record BillRow(
            String billNo, java.time.LocalDateTime createdAt, String userName, String channel,
            String paymentMethod, String subtotal, String discount, String tax, String total
    ) {}
}
