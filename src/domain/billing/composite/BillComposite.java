package domain.billing.composite;

import domain.common.Money;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite component representing a collection of bill components
 */
public class BillComposite implements BillComponent {
    private final List<BillComponent> components = new ArrayList<>();
    private final String description;

    public BillComposite(String description) {
        this.description = description;
    }

    public void addComponent(BillComponent component) {
        components.add(component);
    }

    public void removeComponent(BillComponent component) {
        components.remove(component);
    }

    @Override
    public Money getTotal() {
        return components.stream()
            .map(BillComponent::getTotal)
            .reduce(Money.ZERO, Money::plus);
    }

    @Override
    public void print(PrintWriter writer) {
        writer.println(description + ":");
        components.forEach(component -> component.print(writer));
        writer.println("Total: " + getTotal());
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getItemCount() {
        return components.stream()
            .mapToInt(BillComponent::getItemCount)
            .sum();
    }

    public List<BillComponent> getComponents() {
        return new ArrayList<>(components);
    }
}
