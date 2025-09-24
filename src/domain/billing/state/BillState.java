package domain.billing.state;

import application.pos.POSController;

/**
 * State interface for the Bill state machine
 */
public interface BillState {
    void addItem(POSController context, String code, int qty);
    void removeItem(POSController context, String code, int qty);
    void processPayment(POSController context, String method, Object... params);
    void finalizeBill(POSController context);
    String getStateName();
}
