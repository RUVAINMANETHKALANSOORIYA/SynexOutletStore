package domain.billing.state;

import application.pos.POSController;

/**
 * Empty state - no active bill
 */
public class EmptyState implements BillState {

    @Override
    public void addItem(POSController context, String code, int qty) {
        // Create new bill and transition to active state
        context.newBill();
        context.changeState(new ActiveState());
        context.addItem(code, qty);
    }

    @Override
    public void removeItem(POSController context, String code, int qty) {
        throw new IllegalStateException("Cannot remove items from empty bill");
    }

    @Override
    public void processPayment(POSController context, String method, Object... params) {
        throw new IllegalStateException("Cannot process payment on empty bill");
    }

    @Override
    public void finalizeBill(POSController context) {
        throw new IllegalStateException("Cannot finalize empty bill");
    }

    @Override
    public String getStateName() {
        return "EMPTY";
    }
}
