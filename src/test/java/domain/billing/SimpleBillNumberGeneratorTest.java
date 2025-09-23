package domain.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleBillNumberGeneratorTest {

    private SimpleBillNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SimpleBillNumberGenerator();
    }

    @Test
    @DisplayName("SimpleBillNumberGenerator generates valid bill number")
    void generate_valid_bill_number() {
        String billNumber = generator.next(); // Fixed method name

        assertNotNull(billNumber);
        assertTrue(billNumber.startsWith("POS-"));
        assertTrue(billNumber.contains("-"));
        assertTrue(billNumber.length() > 10);
    }

    @Test
    @DisplayName("SimpleBillNumberGenerator generates unique bill numbers")
    void generate_unique_bill_numbers() {
        String billNumber1 = generator.next(); // Fixed method name
        String billNumber2 = generator.next(); // Fixed method name

        assertNotNull(billNumber1);
        assertNotNull(billNumber2);
        assertNotEquals(billNumber1, billNumber2);
    }

    @Test
    @DisplayName("SimpleBillNumberGenerator follows expected format")
    void follow_expected_format() {
        String billNumber = generator.next(); // Fixed method name

        // Format should be POS-YYYYMMDD-NNNN
        assertTrue(billNumber.matches("POS-\\d{8}-\\d{4}"));
    }

    @Test
    @DisplayName("SimpleBillNumberGenerator increments sequence")
    void increment_sequence() {
        String billNumber1 = generator.next(); // Fixed method name
        String billNumber2 = generator.next(); // Fixed method name
        String billNumber3 = generator.next(); // Fixed method name

        // All should be different
        assertNotEquals(billNumber1, billNumber2);
        assertNotEquals(billNumber2, billNumber3);
        assertNotEquals(billNumber1, billNumber3);
    }

    @Test
    @DisplayName("SimpleBillNumberGenerator creates valid instance")
    void create_valid_instance() {
        assertNotNull(generator);
    }

    @Test
    @DisplayName("Generate many bill numbers without collision")
    void generate_many_bill_numbers() {
        java.util.Set<String> generatedNumbers = new java.util.HashSet<>();

        // Generate 100 bill numbers
        for (int i = 0; i < 100; i++) {
            String billNumber = generator.next();
            assertFalse(generatedNumbers.contains(billNumber),
                "Duplicate bill number generated: " + billNumber);
            generatedNumbers.add(billNumber);
        }

        assertEquals(100, generatedNumbers.size());
    }

    @Test
    @DisplayName("Bill number contains current date")
    void bill_number_contains_current_date() {
        String billNumber = generator.next();

        // Check if bill number contains current date in YYYYMMDD format
        String currentYear = String.valueOf(java.time.LocalDate.now().getYear());
        assertTrue(billNumber.contains(currentYear));
    }

    @Test
    @DisplayName("Bill number format validation")
    void bill_number_format_validation() {
        String billNumber = generator.next();

        // Validate basic format requirements
        assertNotNull(billNumber);
        assertFalse(billNumber.trim().isEmpty());
        assertFalse(billNumber.contains(" ")); // No spaces
        assertTrue(billNumber.matches("[A-Za-z0-9\\-]+"), "Bill number should contain only alphanumeric and dash characters");
    }
}
