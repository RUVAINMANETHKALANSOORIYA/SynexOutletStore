package domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;


public final class Money implements Comparable<Money> {
    private static final int SCALE = 2;
    private final BigDecimal amount;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final DecimalFormat LKR_FORMAT = new DecimalFormat("#,##0.00");

    public Money(BigDecimal amount) { this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP); }
    public static Money of(double v){ return new Money(BigDecimal.valueOf(v)); }
    public BigDecimal asBigDecimal(){ return amount; }

    public Money plus(Money o){ return new Money(amount.add(o.amount)); }
    public Money minus(Money o){ return new Money(amount.subtract(o.amount)); }
    public Money multiply(int f){ return new Money(amount.multiply(BigDecimal.valueOf(f))); }
    public Money multiply(double f){ return new Money(amount.multiply(BigDecimal.valueOf(f))); }
    public Money divide(int d){ return new Money(amount.divide(BigDecimal.valueOf(d), SCALE, RoundingMode.HALF_UP)); }

    public boolean isNegative() { return amount.compareTo(BigDecimal.ZERO) < 0; }

    public String toFormattedString() {
        return "LKR " + LKR_FORMAT.format(amount);
    }

    public String toPlainString() {
        return amount.toPlainString();
    }

    @Override public String toString(){ return toFormattedString(); } // Default to formatted
    @Override public int compareTo(Money o){ return amount.compareTo(o.amount); }
    @Override public boolean equals(Object o){ return (o instanceof Money m) && amount.compareTo(m.amount)==0; }
    @Override public int hashCode(){ return amount.hashCode(); }
}
