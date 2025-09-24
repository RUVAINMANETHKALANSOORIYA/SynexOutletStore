package application.pos.controllers;

import domain.billing.Bill;
import domain.billing.BillLine;
import domain.billing.composite.BillComponent;
import domain.billing.composite.BillComposite;
import domain.billing.composite.BillLineComponent;

/**
 * Builds composite representations of bills using Composite pattern
 */
public class BillCompositeBuilder {
    
    public BillComponent buildComposite(Bill bill) {
        if (bill == null) {
            return new BillComposite("Empty Bill");
        }

        BillComposite composite = new BillComposite("Bill " + bill.number());
        for (BillLine line : bill.lines()) {
            composite.addComponent(new BillLineComponent(line));
        }
        return composite;
    }
}
