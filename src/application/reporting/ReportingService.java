package application.reporting;

import java.time.LocalDate;
import java.util.List;

public final class ReportingService {
    private final ReportRepository repo;
    private final ReportPrinter printer;

    public ReportingService(ReportRepository repo, ReportPrinter printer) {
        this.repo = repo;
        this.printer = printer;
    }

    public void printDailySales(LocalDate day) {
        var row = repo.dailySales(day);
        var view = new ReportPrinter.DailySales(
                row.day(),
                row.bills(),
                row.revenue(),
                row.discounts(),
                row.items()
        );
        printer.printDailySales(view);
    }

    public void printReshelving(int shelfTarget) {
        var rows = repo.reshelvingSuggestions(shelfTarget);
        List<ReportPrinter.ReshelvingRow> view = rows.stream()
                .map(r -> new ReportPrinter.ReshelvingRow(
                        r.itemCode(),
                        r.name(),
                        r.shelfQty(),
                        r.storeQty(),
                        r.suggestedMove()
                ))
                .toList();
        printer.printReshelving(view, shelfTarget);
    }

    public void printReorder(int threshold) {
        var rows = repo.reorderBelow(threshold);
        List<ReportPrinter.ReorderRow> view = rows.stream()
                .map(r -> new ReportPrinter.ReorderRow(
                        r.itemCode(),
                        r.name(),
                        r.totalQty()
                ))
                .toList();
        printer.printReorder(view, threshold);
    }


    public void printRestock() {
        var rows = repo.restockAtOrBelowLevel();
        var view = rows.stream()
                .map(r -> new ReportPrinter.RestockRow(
                        r.itemCode(),
                        r.itemName(),
                        r.shelfQty(),
                        r.storeQty(),
                        r.mainQty(),
                        r.restockLevel()
                ))
                .toList();
        printer.printRestock(view);
    }


    public List<ReportRepository.RestockRow> restockAtOrBelowLevel() {
        return repo.restockAtOrBelowLevel();
    }

    public void printStockByBatch(String itemCodeOrNull) {
        var rows = repo.stockByBatch(itemCodeOrNull);
        List<ReportPrinter.StockBatchRow> view = rows.stream()
                .map(r -> new ReportPrinter.StockBatchRow(
                        r.batchId(),
                        r.itemCode(),
                        r.name(),
                        r.expiry(),
                        r.qtyOnShelf(),
                        r.qtyInStore()
                ))
                .toList();
        printer.printStock(view, itemCodeOrNull);
    }

    public void printBills(LocalDate from, LocalDate to) {
        var rows = repo.billsBetween(from, to);
        List<ReportPrinter.BillRow> view = rows.stream()
                .map(r -> new ReportPrinter.BillRow(
                        r.billNo(),
                        r.createdAt(),
                        r.userName(),
                        r.channel(),
                        r.paymentMethod(),
                        r.subtotal(),
                        r.discount(),
                        r.tax(),
                        r.total()
                ))
                .toList();
        printer.printBills(view, from, to);
    }

    public void printBestSellers(LocalDate from, LocalDate to, int limit) {
        var rows = repo.bestSellers(from, to, limit);
        var list = rows.stream()
                .map(r -> new ReportPrinter.BestSeller(
                        r.itemCode(),
                        r.name(),
                        r.qtySold(),
                        r.revenue()
                ))
                .toList();
        printer.printBestSellers(from, to, list);
    }

    public void printRevenueSeries(LocalDate from, LocalDate to) {
        var rows = repo.revenueByDay(from, to);
        List<ReportPrinter.RevenuePoint> series = rows.stream()
                .map(r -> new ReportPrinter.RevenuePoint(
                        r.day(),
                        r.revenue()
                ))
                .toList();
        printer.printRevenueSeries(from, to, series);
    }
}
