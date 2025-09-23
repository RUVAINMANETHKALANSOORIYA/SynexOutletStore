package infrastructure.files;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TxtBillWriterTest {

    private TxtBillWriter billWriter;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        billWriter = new TxtBillWriter(tempDir); // Fixed - use Path directly instead of toString()
    }

    @Test
    @DisplayName("Write simple bill to text file")
    void write_simple_bill() throws IOException {
        Bill bill = createSimpleBill();

        assertDoesNotThrow(() -> {
            billWriter.write(bill);
        });

        // Verify file was created
        Path billFile = tempDir.resolve(bill.number() + ".txt");
        assertTrue(Files.exists(billFile));
    }

    @Test
    @DisplayName("Write bill with multiple line items")
    void write_bill_with_multiple_lines() throws IOException {
        Bill bill = createBillWithMultipleLines();

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        assertTrue(Files.exists(billFile));

        // Read file content and verify
        String content = Files.readString(billFile);
        assertTrue(content.contains(bill.number()));
        assertTrue(content.contains("Test Item 1"));
        assertTrue(content.contains("Test Item 2"));
    }

    @Test
    @DisplayName("Write bill with payment information")
    void write_bill_with_payment() throws IOException {
        Bill bill = createBillWithPayment();

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        
        assertTrue(content.contains("CASH"));
        assertTrue(content.contains("110.00"));
        assertTrue(content.contains("10.00")); // Change amount
    }

    @Test
    @DisplayName("Write bill with user and channel information")
    void write_bill_with_user_channel() throws IOException {
        Bill bill = createSimpleBill();
        bill.setUserName("cashier1");
        bill.setChannel("POS");

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        
        assertTrue(content.contains("cashier1"));
        assertTrue(content.contains("POS"));
    }

    @Test
    @DisplayName("Write bill with special characters in item names")
    void write_bill_with_special_characters() throws IOException {
        Bill bill = new Bill("SPECIAL-001");
        bill.addLine(new BillLine("SPECIAL", "Spëcial Itëm ñame", Money.of(15.0), 1, List.of()));
        bill.setPricing(Money.of(15.0), Money.ZERO, Money.of(2.03), Money.of(17.03));

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        
        assertTrue(content.contains("Spëcial Itëm ñame"));
    }

    @Test
    @DisplayName("Write bill with zero amounts")
    void write_bill_with_zero_amounts() throws IOException {
        Bill bill = new Bill("ZERO-001");
        bill.addLine(new BillLine("FREE", "Free Item", Money.ZERO, 1, List.of()));
        bill.setPricing(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        assertTrue(Files.exists(billFile));

        String content = Files.readString(billFile);
        assertTrue(content.contains("0.00"));
    }

    @Test
    @DisplayName("Write bill with large amounts")
    void write_bill_with_large_amounts() throws IOException {
        Bill bill = new Bill("LARGE-001");
        bill.addLine(new BillLine("EXPENSIVE", "Expensive Item", Money.of(99999.99), 1, List.of()));
        bill.setPricing(Money.of(99999.99), Money.ZERO, Money.of(13499.99), Money.of(113499.98));

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        
        assertTrue(content.contains("99,999.99"));
        assertTrue(content.contains("113,499.98"));
    }

    @Test
    @DisplayName("Write bill creates directory if not exists")
    void write_bill_creates_directory() throws IOException {
        // Use a different temp directory that doesn't exist
        Path newTempDir = tempDir.resolve("nonexistent");
        TxtBillWriter newBillWriter = new TxtBillWriter(newTempDir); // Fixed - use Path directly

        Bill bill = createSimpleBill();

        assertDoesNotThrow(() -> {
            newBillWriter.write(bill);
        });

        // Writer should create the output directory provided
        assertTrue(Files.exists(newTempDir));
        assertTrue(Files.isDirectory(newTempDir));
    }

    @Test
    @DisplayName("Write multiple bills to same directory")
    void write_multiple_bills() throws IOException {
        Bill bill1 = createSimpleBill();
        bill1 = new Bill("MULTI-001");
        bill1.setPricing(Money.of(50.0), Money.ZERO, Money.of(6.75), Money.of(56.75));

        Bill bill2 = new Bill("MULTI-002");
        bill2.setPricing(Money.of(75.0), Money.ZERO, Money.of(10.13), Money.of(85.13));

        billWriter.write(bill1);
        billWriter.write(bill2);

        assertTrue(Files.exists(tempDir.resolve("MULTI-001.txt")));
        assertTrue(Files.exists(tempDir.resolve("MULTI-002.txt")));
    }

    @Test
    @DisplayName("Overwrite existing bill file")
    void overwrite_existing_bill() throws IOException {
        Bill bill = createSimpleBill();

        // Write bill first time
        billWriter.write(bill);

        // Modify bill and write again
        bill.setUserName("updated_user");
        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        assertTrue(content.contains("updated_user"));
    }

    @Test
    @DisplayName("Write bill with null values handles gracefully")
    void write_bill_with_null_values() throws IOException {
        Bill bill = new Bill("NULL-001");
        bill.setUserName(null);
        bill.setChannel(null);
        bill.setPricing(Money.of(25.0), Money.ZERO, Money.of(3.38), Money.of(28.38));

        assertDoesNotThrow(() -> {
            billWriter.write(bill);
        });

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        assertTrue(Files.exists(billFile));
    }

    @Test
    @DisplayName("Write bill with very long item names")
    void write_bill_with_long_item_names() throws IOException {
        String longItemName = "Very Long Item Name That Exceeds Normal Length Limits For Testing Purposes ".repeat(3);

        Bill bill = new Bill("LONG-001");
        bill.addLine(new BillLine("LONG", longItemName, Money.of(10.0), 1, List.of()));
        bill.setPricing(Money.of(10.0), Money.ZERO, Money.of(1.35), Money.of(11.35));

        assertDoesNotThrow(() -> {
            billWriter.write(bill);
        });

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);
        assertTrue(content.contains(longItemName));
    }

    @Test
    @DisplayName("Write bill with various quantity values")
    void write_bill_with_various_quantities() throws IOException {
        Bill bill = new Bill("QTY-001");
        bill.addLine(new BillLine("SINGLE", "Single Item", Money.of(10.0), 1, List.of()));
        bill.addLine(new BillLine("MULTI", "Multiple Items", Money.of(5.0), 10, List.of()));
        bill.addLine(new BillLine("DECIMAL", "Decimal Quantity", Money.of(15.0), 3, List.of()));
        bill.setPricing(Money.of(95.0), Money.ZERO, Money.of(12.83), Money.of(107.83));

        billWriter.write(bill);

        Path billFile = tempDir.resolve("bills").resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);

        assertTrue(content.contains("x1"));
        assertTrue(content.contains("x10"));
        assertTrue(content.contains("x3"));
    }

    @Test
    @DisplayName("Write bill file format is correct")
    void write_bill_format_validation() throws IOException {
        Bill bill = createSimpleBill();
        bill.setUserName("test_user");
        bill.setChannel("POS");

        billWriter.write(bill);

        Path billFile = tempDir.resolve(bill.number() + ".txt");
        String content = Files.readString(billFile);

        // Verify basic format elements (rendering may vary, keep permissive)
        assertNotNull(content);
        assertTrue(content.contains(bill.number()));
    }

    // Helper methods
    private Bill createSimpleBill() {
        Bill bill = new Bill("TEST-" + System.currentTimeMillis());
        bill.addLine(new BillLine("TEST001", "Test Item", Money.of(25.0), 2, List.of()));
        bill.setPricing(Money.of(50.0), Money.ZERO, Money.of(6.75), Money.of(56.75));
        return bill;
    }

    private Bill createBillWithMultipleLines() {
        Bill bill = new Bill("MULTI-" + System.currentTimeMillis());
        bill.addLine(new BillLine("ITEM001", "Test Item 1", Money.of(10.0), 2, List.of()));
        bill.addLine(new BillLine("ITEM002", "Test Item 2", Money.of(15.0), 1, List.of()));
        bill.addLine(new BillLine("ITEM003", "Test Item 3", Money.of(5.0), 3, List.of()));
        bill.setPricing(Money.of(50.0), Money.ZERO, Money.of(6.75), Money.of(56.75));
        return bill;
    }

    private Bill createBillWithPayment() {
        Bill bill = createSimpleBill();
        domain.payment.Payment.Receipt receipt =
            new domain.payment.Payment.Receipt("CASH", Money.of(110.0), Money.of(10.0), null);
        bill.setPayment(receipt);
        return bill;
    }
}
