package application.pos.patterns.command;

/**
 * Command interface for implementing the Command pattern
 */
public interface Command {
    void execute();
    void undo();
    String getDescription();
}
