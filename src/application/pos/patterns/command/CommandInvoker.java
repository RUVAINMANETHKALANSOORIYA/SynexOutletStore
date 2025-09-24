package application.pos.patterns.command;

import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

/**
 * Invoker class that manages command execution and undo/redo operations
 */
public class CommandInvoker {
    private final Stack<Command> history = new Stack<>();
    private final Stack<Command> undoHistory = new Stack<>();
    private final int maxHistorySize;

    public CommandInvoker() {
        this(100); // Default history size
    }

    public CommandInvoker(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public void execute(Command command) {
        command.execute();
        history.push(command);
        undoHistory.clear(); // Clear redo history when new command is executed

        // Maintain history size limit
        if (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }

    public boolean undo() {
        if (!history.isEmpty()) {
            Command command = history.pop();
            command.undo();
            undoHistory.push(command);
            return true;
        }
        return false;
    }

    public boolean redo() {
        if (!undoHistory.isEmpty()) {
            Command command = undoHistory.pop();
            command.execute();
            history.push(command);
            return true;
        }
        return false;
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public boolean canRedo() {
        return !undoHistory.isEmpty();
    }

    public List<String> getHistory() {
        List<String> historyList = new ArrayList<>();
        for (Command command : history) {
            historyList.add(command.getDescription());
        }
        return historyList;
    }

    public void clearHistory() {
        history.clear();
        undoHistory.clear();
    }
}
