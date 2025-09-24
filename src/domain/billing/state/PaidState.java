package domain.billing.state;

import application.pos.controllers.POSController;

/**
 * Paid state - bill has been paid but not yet finalized
 */
public class PaidState implements BillState {

    @Override
    public void addItem(POSController context, String code, int qty) {
        throw new IllegalStateException("Cannot add items to paid bill");
    }

    @Override
    public void removeItem(POSController context, String code, int qty) {
        throw new IllegalStateException("Cannot remove items from paid bill");
    }

    @Override
    public void processPayment(POSController context, String method, Object... params) {
        throw new IllegalStateException("Bill is already paid");
    }

    @Override
    public void finalizeBill(POSController context) {
        // Finalize the bill and transition to completed state
        context.finalizeBillInternal();
        context.changeState(new CompletedState());
    }

    @Override
    public String getStateName() {
        return "PAID";
    }
}
