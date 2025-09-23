package domain.inventory;

import javax.swing.JTextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypedCodeReaderTest {

    @Test
    @DisplayName("Read code from text field")
    void read_code_from_text_field() {
        JTextField textField = new JTextField("ITEM001");
        TypedCodeReader reader = new TypedCodeReader(textField);

        String result = reader.readCode();

        assertEquals("ITEM001", result);
    }

    @Test
    @DisplayName("Read empty code from text field")
    void read_empty_code() {
        JTextField textField = new JTextField("");
        TypedCodeReader reader = new TypedCodeReader(textField);

        String result = reader.readCode();

        assertEquals("", result);
    }

    @Test
    @DisplayName("Read code with whitespace trimming")
    void read_code_with_whitespace() {
        JTextField textField = new JTextField("  ITEM001  ");
        TypedCodeReader reader = new TypedCodeReader(textField);

        String result = reader.readCode();

        assertEquals("ITEM001", result);
    }

    @Test
    @DisplayName("Read code with special characters")
    void read_code_special_characters() {
        JTextField textField = new JTextField("SPËCIAL-001");
        TypedCodeReader reader = new TypedCodeReader(textField);

        String result = reader.readCode();

        assertEquals("SPËCIAL-001", result);
    }

    @Test
    @DisplayName("Read numeric code")
    void read_numeric_code() {
        JTextField textField = new JTextField("1234567890");
        TypedCodeReader reader = new TypedCodeReader(textField);

        String result = reader.readCode();

        assertEquals("1234567890", result);
    }

    @Test
    @DisplayName("Does not clear text field after reading (no API to clear)")
    void does_not_clear_field_after_reading() {
        JTextField textField = new JTextField("ITEM001");
        TypedCodeReader reader = new TypedCodeReader(textField);

        reader.readCode();

        assertEquals("ITEM001", textField.getText());
    }

    @Test
    @DisplayName("Constructing with a text field works")
    void constructing_with_field_works() {
        JTextField textField = new JTextField();
        assertDoesNotThrow(() -> new TypedCodeReader(textField));
    }

    @Test
    @DisplayName("Read returns trimmed content (no validation API)")
    void read_returns_trimmed_content() {
        JTextField textField = new JTextField("  ABC123  ");
        TypedCodeReader reader = new TypedCodeReader(textField);

        assertEquals("ABC123", reader.readCode());
    }

    @Test
    @DisplayName("Handle text field updates")
    void handle_text_field_updates() {
        JTextField textField = new JTextField("INITIAL");
        TypedCodeReader reader = new TypedCodeReader(textField);

        textField.setText("UPDATED");
        String result = reader.readCode();

        assertEquals("UPDATED", result);
    }

    @Test
    @DisplayName("Null text field will cause NPE when reading")
    void null_field_npe_on_read() {
        TypedCodeReader reader = new TypedCodeReader(null);
        assertThrows(NullPointerException.class, reader::readCode);
    }
}
