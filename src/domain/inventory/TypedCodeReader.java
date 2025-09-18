package domain.inventory;

import javax.swing.JTextField;

/** Adapter: reads the code from a Swing JTextField. */
public final class TypedCodeReader implements ItemCodeReader {
    private final JTextField field;

    public TypedCodeReader(JTextField field) {
        this.field = field;
    }

    @Override
    public String readCode() {
        return field.getText().trim();
    }
}
