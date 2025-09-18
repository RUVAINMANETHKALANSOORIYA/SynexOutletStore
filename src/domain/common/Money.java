package domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money implements Comparable<Money> {
    private static final int SCALE = 2;
    private final BigDecimal amount;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money(BigDecimal amount) { this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP); }
    public static Money of(double v){ return new Money(BigDecimal.valueOf(v)); }
    public BigDecimal asBigDecimal(){ return amount; }

    public Money plus(Money o){ return new Money(amount.add(o.amount)); }
    public Money minus(Money o){ return new Money(amount.subtract(o.amount)); }
    public Money multiply(int f){ return new Money(amount.multiply(BigDecimal.valueOf(f))); }
    public Money multiply(double f){ return new Money(amount.multiply(BigDecimal.valueOf(f))); }
    public Money divide(int d){ return new Money(amount.divide(BigDecimal.valueOf(d), SCALE, RoundingMode.HALF_UP)); }

    @Override public String toString(){ return amount.toPlainString(); }
    @Override public int compareTo(Money o){ return amount.compareTo(o.amount); }
    @Override public boolean equals(Object o){ return (o instanceof Money m) && amount.compareTo(m.amount)==0; }
    @Override public int hashCode(){ return amount.hashCode(); }
}
