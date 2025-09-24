package domain.billing.composite;

import domain.common.Money;
import java.io.PrintWriter;

/**
 * Component interface for the Composite pattern in billing
 */
public interface BillComponent {
    Money getTotal();
    void print(PrintWriter writer);
    String getDescription();
    int getItemCount();
}
