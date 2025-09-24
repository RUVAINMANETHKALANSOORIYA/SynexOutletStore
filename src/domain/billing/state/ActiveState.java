package domain.billing.state;

import application.pos.POSController;

/**
 * Active state - bill is being built with items
 */
public class ActiveState implements BillState {

    @Override
    public void addItem(POSController context, String code, int qty) {
        // Delegate to controller's internal addItem method
        context.addItemInternal(code, qty);
    }

    @Override
    public void removeItem(POSController context, String code, int qty) {
        // Delegate to controller's internal removeItem method
        context.removeItemInternal(code, qty);

        // Check if bill is now empty and transition to empty state
        if (context.getActiveBill() != null && context.getActiveBill().lines().isEmpty()) {
            context.changeState(new EmptyState());
        }
    }

    @Override
    public void processPayment(POSController context, String method, Object... params) {
        // Process payment and transition to paid state
        context.processPaymentInternal(method, params);
        context.changeState(new PaidState());
    }

    @Override
    public void finalizeBill(POSController context) {
        throw new IllegalStateException("Cannot finalize unpaid bill. Process payment first.");
    }

    @Override
    public String getStateName() {
        return "ACTIVE";
    }
}
