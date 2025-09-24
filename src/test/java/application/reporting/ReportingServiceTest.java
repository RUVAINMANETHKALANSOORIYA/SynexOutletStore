package application.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ports.in.ReportingService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportingServiceTest {

    private ReportingService reportingService;
    private FakeReportRepository fakeRepository;
    private FakeReportPrinter fakePrinter;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeReportRepository();
        fakePrinter = new FakeReportPrinter();
        reportingService = new ReportingService(fakeRepository, fakePrinter); // Fixed constructor
    }

    @Test
    @DisplayName("ReportingService can print daily sales")
    void print_daily_sales() {
        LocalDate testDate = LocalDate.of(2023, 9, 15);

        assertDoesNotThrow(() -> {
            reportingService.printDailySales(testDate);
        });
    }

    @Test
    @DisplayName("ReportingService can print reshelving suggestions")
    void print_reshelving_suggestions() {
        int shelfTarget = 10;

        assertDoesNotThrow(() -> {
            reportingService.printReshelving(shelfTarget);
        });
    }

    @Test
    @DisplayName("ReportingService handles different dates")
    void handle_different_dates() {
        assertDoesNotThrow(() -> {
            reportingService.printDailySales(LocalDate.now());
            reportingService.printDailySales(LocalDate.now().minusDays(1));
            reportingService.printDailySales(LocalDate.now().minusWeeks(1));
        });
    }

    @Test
    @DisplayName("ReportingService handles different shelf targets")
    void handle_different_shelf_targets() {
        assertDoesNotThrow(() -> {
            reportingService.printReshelving(5);
            reportingService.printReshelving(15);
            reportingService.printReshelving(25);
        });
    }

    @Test
    @DisplayName("ReportingService creates valid service instance")
    void creates_valid_service_instance() {
        assertNotNull(reportingService);
    }

    @Test
    @DisplayName("ReportingService handles null date gracefully")
    void handle_null_date() {
        assertDoesNotThrow(() -> {
            reportingService.printDailySales(null);
        });
    }

    @Test
    @DisplayName("ReportingService handles negative shelf target")
    void handle_negative_shelf_target() {
        assertDoesNotThrow(() -> {
            reportingService.printReshelving(-1);
        });
    }

    @Test
    @DisplayName("ReportingService handles zero shelf target")
    void handle_zero_shelf_target() {
        assertDoesNotThrow(() -> {
            reportingService.printReshelving(0);
        });
    }

    // Fake implementations for testing
    static class FakeReportRepository implements ReportRepository {
        @Override
        public ReportRepository.DailySalesRow dailySales(LocalDate day) {
            return new ReportRepository.DailySalesRow(
                day != null ? day : LocalDate.now(),
                5,
                "100.00", // Fixed: using String instead of Money
                "10.00",  // Fixed: using String instead of Money
                25
            );
        }

        @Override
        public List<ReportRepository.ReshelvingRow> reshelvingSuggestions(int shelfTarget) {
            return List.of(
                new ReportRepository.ReshelvingRow("ITEM001", "Test Item 1", 5, 20, 10),
                new ReportRepository.ReshelvingRow("ITEM002", "Test Item 2", 3, 15, 7)
            );
        }

        @Override
        public List<ReportRepository.RestockRow> restockAtOrBelowLevel() {
            return List.of(
                new ReportRepository.RestockRow("ITEM003", "Test Item 3", 2, 5, 10, 20)
            );
        }

        // Minimal implementations for other required methods
        @Override public List<ReportRepository.BestSellerRow> bestSellers(LocalDate from, LocalDate to, int limit) { return List.of(); }
        @Override public List<ReportRepository.RevenueRow> revenueByDay(LocalDate from, LocalDate to) { return List.of(); }
        @Override public List<ReportRepository.ReorderRow> reorderBelow(int threshold) { return List.of(); }
        @Override public List<ReportRepository.StockBatchRow> stockByBatch(String itemCodeOrNull) { return List.of(); }
        @Override public List<ReportRepository.BillRow> billsBetween(LocalDate from, LocalDate to) { return List.of(); }
    }

    static class FakeReportPrinter implements ReportPrinter {
        @Override
        public void printDailySales(ReportPrinter.DailySales report) {
            // Mock implementation - just verify the method is called
            assertNotNull(report);
        }

        @Override
        public void printBestSellers(LocalDate from, LocalDate to, List<ReportPrinter.BestSeller> list) {
            // Mock implementation
            assertNotNull(list);
        }

        @Override
        public void printRevenueSeries(LocalDate from, LocalDate to, List<ReportPrinter.RevenuePoint> series) {
            // Mock implementation
            assertNotNull(series);
        }

        @Override
        public void printRestock(List<ReportPrinter.RestockRow> rows) {
            // Mock implementation
            assertNotNull(rows);
        }

        @Override
        public void printReshelving(List<ReportPrinter.ReshelvingRow> rows, int shelfTarget) {
            // Mock implementation - just verify the method is called
            assertNotNull(rows);
        }

        @Override
        public void printReorder(List<ReportPrinter.ReorderRow> reorderRows, int threshold) {
            // Mock implementation for missing method
            assertNotNull(reorderRows);
        }

        @Override
        public void printStock(List<ReportPrinter.StockBatchRow> stockRows, String itemCodeOrNull) {
            // Mock implementation for missing method
            assertNotNull(stockRows);
        }

        @Override
        public void printBills(List<ReportPrinter.BillRow> bills, LocalDate from, LocalDate to) {
            // Mock implementation for missing method
            assertNotNull(bills);
        }
    }
}
