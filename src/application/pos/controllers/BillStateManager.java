package application.pos.controllers;

import domain.billing.state.BillState;
import domain.billing.state.EmptyState;

/**
 * Manages bill state transitions using State pattern
 */
public class BillStateManager {
    private BillState currentState = new EmptyState();

    public void changeState(BillState newState) {
        this.currentState = newState;
    }

    public String getCurrentStateName() {
        return currentState.getStateName();
    }

    public BillState getCurrentState() {
        return currentState;
    }
}
