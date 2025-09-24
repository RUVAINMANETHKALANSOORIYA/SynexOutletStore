package domain.billing.composite;

import domain.common.Money;
import domain.billing.BillLine;
import java.io.PrintWriter;

/**
 * Leaf component representing a single bill line
 */
public class BillLineComponent implements BillComponent {
    private final BillLine billLine;

    public BillLineComponent(BillLine billLine) {
        this.billLine = billLine;
    }

    @Override
    public Money getTotal() {
        return billLine.lineTotal();
    }

    @Override
    public void print(PrintWriter writer) {
        writer.printf("%-20s %3d x %8s = %10s%n",
            billLine.itemName(),
            billLine.quantity(),
            billLine.unitPrice(),
            billLine.lineTotal());
    }

    @Override
    public String getDescription() {
        return billLine.itemName();
    }

    @Override
    public int getItemCount() {
        return billLine.quantity();
    }

    public BillLine getBillLine() {
        return billLine;
    }
}
