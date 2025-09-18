package infrastructure.console;

import application.reporting.ReportPrinter;

import java.time.LocalDate;
import java.util.List;

public final class ConsoleReportPrinter implements ReportPrinter {
    @Override
    public void printDailySales(DailySales s) {
        System.out.println("=== Daily Sales: " + s.day() + " ===");
        System.out.printf("Bills: %d%nRevenue: %s%nDiscounts: %s%nItems sold: %d%n",
                s.bills(), s.revenue(), s.discounts(), s.itemsSold());
        System.out.println();
    }

    @Override
    public void printBestSellers(LocalDate from, LocalDate to, List<BestSeller> list) {
        System.out.println("=== Best Sellers from " + from + " to " + to + " ===");
        System.out.printf("%-10s %-24s %8s %12s%n", "Code", "Name", "Qty", "Revenue");
        for (BestSeller b : list) {
            System.out.printf("%-10s %-24s %8d %12s%n",
                    b.itemCode(), b.itemName(), b.qtySold(), b.revenue());
        }
        System.out.println();
    }

    @Override
    public void printRevenueSeries(LocalDate from, LocalDate to, List<RevenuePoint> series) {
        System.out.println("=== Revenue by Day from " + from + " to " + to + " ===");
        System.out.printf("%-12s %12s%n", "Date", "Revenue");
        for (RevenuePoint p : series) {
            System.out.printf("%-12s %12s%n", p.day(), p.revenue());
        }
        System.out.println();
    }

    // ===== New Reports =====

    @Override
    public void printReshelving(List<ReshelvingRow> rows, int shelfTarget) {
        System.out.println("=== Reshelving Report (Target: " + shelfTarget + ") ===");
        System.out.printf("%-10s %-20s %8s %8s %12s%n", "Code", "Name", "Shelf", "Store", "SuggestedMove");
        for (ReshelvingRow r : rows) {
            System.out.printf("%-10s %-20s %8d %8d %12d%n",
                    r.itemCode(), r.itemName(), r.shelfQty(), r.storeQty(), r.suggestedMove());
        }
        System.out.println();
    }

    @Override
    public void printReorder(List<ReorderRow> rows, int threshold) {
        System.out.println("=== Reorder Report (Threshold: " + threshold + ") ===");
        System.out.printf("%-10s %-20s %8s%n", "Code", "Name", "TotalQty");
        for (ReorderRow r : rows) {
            System.out.printf("%-10s %-20s %8d%n",
                    r.itemCode(), r.itemName(), r.totalQty());
        }
        System.out.println();
    }

    @Override
    public void printStock(List<StockBatchRow> rows, String itemCodeOrNull) {
        System.out.println("=== Stock Report " +
                (itemCodeOrNull == null ? "(All Items)" : "(Item: " + itemCodeOrNull + ")") + " ===");
        System.out.printf("%-6s %-10s %-20s %-12s %8s %8s%n", "Batch", "Code", "Name", "Expiry", "Shelf", "Store");
        for (StockBatchRow r : rows) {
            System.out.printf("%-6d %-10s %-20s %-12s %8d %8d%n",
                    r.batchId(), r.itemCode(), r.itemName(),
                    r.expiry() == null ? "N/A" : r.expiry(),
                    r.qtyOnShelf(), r.qtyInStore());
        }
        System.out.println();
    }

    @Override
    public void printBills(List<BillRow> rows, LocalDate from, LocalDate to) {
        System.out.println("=== Bill Report from " + from + " to " + to + " ===");
        System.out.printf("%-10s %-16s %-12s %-8s %-10s %-8s %-8s %-8s %-8s%n",
                "BillNo", "CreatedAt", "User", "Channel", "PayMethod",
                "Subtot", "Disc", "Tax", "Total");
        for (BillRow r : rows) {
            System.out.printf("%-10s %-16s %-12s %-8s %-10s %-8s %-8s %-8s %-8s%n",
                    r.billNo(), r.createdAt(), r.userName(), r.channel(),
                    r.paymentMethod(), r.subtotal(), r.discount(), r.tax(), r.total());
        }
        System.out.println();
    }

    @Override
    public void printRestock(List<RestockRow> rows) {
        System.out.println("=== Restock Report (<= level) ===");
        System.out.printf("%-8s %-24s %6s %6s %6s %8s%n", "Code","Name","Shelf","Store","Main","Level");
        for (var r : rows) {
            System.out.printf("%-8s %-24s %6d %6d %6d %8d%n",
                    r.itemCode(), r.itemName(), r.shelfQty(), r.storeQty(), r.mainQty(), r.restockLevel());
        }
        System.out.println();
    }

}
