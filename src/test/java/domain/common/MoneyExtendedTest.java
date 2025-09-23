package domain.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyExtendedTest {

    @Test
    @DisplayName("Money multiplication operations")
    void money_multiplication() {
        Money base = Money.of(25.50);

        assertEquals(Money.of(51.0), base.multiply(2));
        assertEquals(Money.of(127.5), base.multiply(5));
        assertEquals(Money.of(0.0), base.multiply(0));
    }

    @Test
    @DisplayName("Money division operations")
    void money_division() {
        Money base = Money.of(100.0);

        assertEquals(Money.of(50.0), base.divide(2));
        assertEquals(Money.of(25.0), base.divide(4));
        assertEquals(Money.of(33.33), base.divide(3));
    }

    @Test
    @DisplayName("Money division by zero throws exception")
    void money_division_by_zero() {
        Money base = Money.of(100.0);

        assertThrows(ArithmeticException.class, () -> base.divide(0));
    }

    @Test
    @DisplayName("Money percentage calculations")
    void money_percentage_calculations() {
        Money base = Money.of(200.0);

        assertEquals(Money.of(20.0), base.multiply(0.10)); // 10% of 200
        assertEquals(Money.of(50.0), base.multiply(0.25)); // 25% of 200
    }

    @Test
    @DisplayName("Money comparison operations")
    void money_comparison() {
        Money small = Money.of(10.0);
        Money medium = Money.of(50.0);
        Money large = Money.of(100.0);
        Money equalToMedium = Money.of(50.0);

        assertTrue(small.compareTo(medium) < 0);
        assertTrue(large.compareTo(medium) > 0);
        assertEquals(0, medium.compareTo(equalToMedium));
    }

    @Test
    @DisplayName("Money isNegative check")
    void money_negative_check() {
        Money positive = Money.of(50.0);
        Money negative = Money.of(-25.0);
        Money zero = Money.ZERO;

        assertFalse(positive.isNegative());
        assertTrue(negative.isNegative());
        assertFalse(zero.isNegative());
    }

    @Test
    @DisplayName("Money rounding behavior")
    void money_rounding() {
        Money precise = Money.of(12.3456789);

        // Money should round to 2 decimal places
        assertEquals(2, precise.asBigDecimal().scale());
        assertEquals(new BigDecimal("12.35"), precise.asBigDecimal());
    }

    @Test
    @DisplayName("Money with very large amounts")
    void money_large_amounts() {
        Money large = Money.of(999999999.99);

        assertFalse(large.isNegative());
        assertNotEquals(Money.ZERO, large);
        assertTrue(large.compareTo(Money.ZERO) > 0);
    }

    @Test
    @DisplayName("Money with very small amounts")
    void money_small_amounts() {
        Money tiny = Money.of(0.01);
        Money smaller = Money.of(0.001);

        assertFalse(tiny.isNegative());
        assertEquals(Money.of(0.00), smaller); // Should round to 0.00
    }

    @Test
    @DisplayName("Money arithmetic chain operations")
    void money_arithmetic_chains() {
        Money base = Money.of(100.0);

        Money result = base.plus(Money.of(50.0))
                          .minus(Money.of(25.0))
                          .multiply(2)
                          .divide(5);

        assertEquals(Money.of(50.0), result); // (100+50-25)*2/5 = 125*2/5 = 50
    }

    @Test
    @DisplayName("Money format operations")
    void money_format_operations() {
        Money amount = Money.of(1234.56);

        String formatted = amount.toString();
        assertNotNull(formatted);
        assertTrue(formatted.contains("1,234.56") || formatted.contains("1234.56"));

        String plain = amount.toPlainString();
        assertEquals("1234.56", plain);

        String lkrFormatted = amount.toFormattedString();
        assertTrue(lkrFormatted.contains("LKR") && lkrFormatted.contains("1,234.56"));
    }

    @Test
    @DisplayName("Money equals and hashcode")
    void money_equals_hashcode() {
        Money money1 = Money.of(99.99);
        Money money2 = Money.of(99.99);
        Money money3 = Money.of(88.88);

        assertEquals(money1, money2);
        assertNotEquals(money1, money3);
        assertEquals(money1.hashCode(), money2.hashCode());
    }

    @Test
    @DisplayName("Money ZERO constant")
    void money_zero_constant() {
        assertEquals(Money.of(0.0), Money.ZERO);
        assertFalse(Money.ZERO.isNegative());
        assertEquals(0, Money.ZERO.compareTo(Money.of(0.0)));
    }

    @Test
    @DisplayName("Money BigDecimal constructor")
    void money_bigdecimal_constructor() {
        BigDecimal bd = new BigDecimal("99.999");
        Money money = new Money(bd);

        assertEquals(Money.of(100.0), money); // Should round to 2 decimal places
    }

    @Test
    @DisplayName("Money precision handling")
    void money_precision_handling() {
        Money precise1 = Money.of(10.126);
        Money precise2 = Money.of(10.124);

        assertEquals(Money.of(10.13), precise1); // Rounds up
        assertEquals(Money.of(10.12), precise2); // Rounds down
    }
}
