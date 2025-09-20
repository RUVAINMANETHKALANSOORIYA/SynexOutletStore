package domain.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    @DisplayName("basic arithmetic and equality")
    void arithmetic_and_equality() {
        Money a = Money.of(10.25);
        Money b = Money.of(5.75);
        Money c = a.plus(b); // 16.00
        assertEquals(Money.of(16.00), c);
        assertEquals(0, c.compareTo(Money.of(16.00)));

        Money d = c.minus(Money.of(1.50));
        assertEquals(Money.of(14.50), d);

        assertEquals(Money.of(21.50), Money.of(10.75).multiply(2));
        assertEquals(Money.of(5.38), Money.of(10.75).divide(2));
        assertFalse(Money.of(0.00).isNegative());
    }

    @Test
    @DisplayName("formatting and plain string")
    void formatting_and_plain() {
        Money m = new Money(new BigDecimal("1234.5"));
        assertTrue(m.toString().startsWith("LKR "));
        assertEquals("1234.50", m.toPlainString());
    }

    @Test
    @DisplayName("hashCode consistent with equals")
    void hashcode_contract() {
        Money x1 = Money.of(1.20);
        Money x2 = Money.of(1.2);
        assertEquals(x1, x2);
        assertEquals(x1.hashCode(), x2.hashCode());
    }
}
