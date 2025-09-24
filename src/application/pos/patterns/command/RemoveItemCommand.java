package application.pos.patterns.command;

import application.pos.controllers.POSController;

/**
 * Command to remove an item from the current bill
 */
public class RemoveItemCommand implements Command {
    private final POSController controller;
    private final String itemCode;
    private final int quantity;
    private boolean executed = false;

    public RemoveItemCommand(POSController controller, String itemCode, int quantity) {
        this.controller = controller;
        this.itemCode = itemCode;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        if (!executed) {
            controller.removeItem(itemCode, quantity);
            executed = true;
        }
    }

    @Override
    public void undo() {
        if (executed) {
            controller.addItem(itemCode, quantity);
            executed = false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Remove %d x %s", quantity, itemCode);
    }
}
