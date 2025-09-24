package domain.billing.state;

import application.pos.controllers.POSController;

/**
 * Completed state - bill has been finalized and saved
 */
public class CompletedState implements BillState {

    @Override
    public void addItem(POSController context, String code, int qty) {
        throw new IllegalStateException("Cannot modify completed bill");
    }

    @Override
    public void removeItem(POSController context, String code, int qty) {
        throw new IllegalStateException("Cannot modify completed bill");
    }

    @Override
    public void processPayment(POSController context, String method, Object... params) {
        throw new IllegalStateException("Bill is already completed");
    }

    @Override
    public void finalizeBill(POSController context) {
        throw new IllegalStateException("Bill is already completed");
    }

    @Override
    public String getStateName() {
        return "COMPLETED";
    }
}
