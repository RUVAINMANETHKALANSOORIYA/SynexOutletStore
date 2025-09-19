package domain.spec;

public interface Specification<T> {
    boolean isSatisfiedBy(T t);
    default Specification<T> and(Specification<T> other) { return x -> this.isSatisfiedBy(x) && other.isSatisfiedBy(x); }
    default Specification<T> or (Specification<T> other) { return x -> this.isSatisfiedBy(x) || other.isSatisfiedBy(x); }
    default Specification<T> not()                     { return x -> !this.isSatisfiedBy(x); }
}
