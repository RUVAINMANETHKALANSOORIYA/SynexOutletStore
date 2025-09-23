package domain.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeReaderTest {

    private BarcodeReader reader;

    @BeforeEach
    void setUp() {
        reader = new BarcodeReader();
    }

    @Test
    @DisplayName("BarcodeReader can read code")
    void read_code_basic() {
        String code = reader.readCode();
        assertNotNull(code);
        assertEquals("SIMULATED_BARCODE_CODE", code);
    }

    @Test
    @DisplayName("BarcodeReader returns consistent results")
    void read_code_consistent() {
        String code1 = reader.readCode();
        String code2 = reader.readCode();
        assertEquals(code1, code2);
    }

    @Test
    @DisplayName("BarcodeReader returns non-empty string")
    void read_code_non_empty() {
        String code = reader.readCode();
        assertNotNull(code);
        assertFalse(code.isEmpty());
    }

    @Test
    @DisplayName("BarcodeReader multiple reads")
    void read_code_multiple_times() {
        for (int i = 0; i < 5; i++) {
            String code = reader.readCode();
            assertNotNull(code);
            assertEquals("SIMULATED_BARCODE_CODE", code);
        }
    }

    @Test
    @DisplayName("BarcodeReader implements ItemCodeReader interface")
    void implements_item_code_reader() {
        assertTrue(reader instanceof ItemCodeReader);
    }

    @Test
    @DisplayName("BarcodeReader code format is string")
    void code_format_is_string() {
        String code = reader.readCode();
        assertTrue(code instanceof String);
    }

    @Test
    @DisplayName("BarcodeReader simulation works")
    void simulation_works() {
        String code = reader.readCode();
        assertTrue(code.contains("SIMULATED"));
        assertTrue(code.contains("BARCODE"));
        assertTrue(code.contains("CODE"));
    }

    @Test
    @DisplayName("BarcodeReader creates valid ItemCodeReader")
    void creates_valid_item_code_reader() {
        ItemCodeReader codeReader = new BarcodeReader();
        assertNotNull(codeReader);
        String code = codeReader.readCode();
        assertNotNull(code);
    }
}
