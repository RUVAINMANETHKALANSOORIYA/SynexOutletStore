package application.pos.patterns.command;

import application.pos.controllers.POSController;

/**
 * Command to add an item to the current bill
 */
public class AddItemCommand implements Command {
    private final POSController controller;
    private final String itemCode;
    private final int quantity;
    private boolean executed = false;

    public AddItemCommand(POSController controller, String itemCode, int quantity) {
        this.controller = controller;
        this.itemCode = itemCode;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        if (!executed) {
            controller.addItem(itemCode, quantity);
            executed = true;
        }
    }

    @Override
    public void undo() {
        if (executed) {
            controller.removeItem(itemCode, quantity);
            executed = false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Add %d x %s", quantity, itemCode);
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getQuantity() {
        return quantity;
    }
}
