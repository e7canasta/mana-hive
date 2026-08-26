package com.manahive.kernel

/**
 * Specification Pattern (Fowler, Vernon).
 *
 * A Specification is a predicate that tests whether a candidate object
 * satisfies some criterion. Specifications are composable:
 * - and(Spec) → both must match
 * - or(Spec)  → either must match
 * - not()     → inverts the match
 *
 * In the Recorder engine, Specifications determine whether a trigger
 * should activate a recording rule or evidence rule.
 *
 * Fowler: "Specification" in Domain-Driven Design.
 * Vernon: "Specification" as a first-class domain concept.
 *
 * @param T The type of candidate object being tested
 */
public interface Specification<T> {
    /**
     * Test whether the candidate satisfies this specification.
     */
    public fun isSatisfiedBy(candidate: T): Boolean

    /**
     * Combine with another specification: both must match.
     */
    public fun and(other: Specification<T>): Specification<T> =
        AndSpecification(this, other)

    /**
     * Combine with another specification: either must match.
     */
    public fun or(other: Specification<T>): Specification<T> =
        OrSpecification(this, other)

    /**
     * Invert this specification: must NOT match.
     */
    public fun not(): Specification<T> =
        NotSpecification(this)
}

/**
 * Composite: both specifications must match.
 */
public class AndSpecification<T>(
    private val left: Specification<T>,
    private val right: Specification<T>,
) : Specification<T> {
    override fun isSatisfiedBy(candidate: T): Boolean =
        left.isSatisfiedBy(candidate) && right.isSatisfiedBy(candidate)
}

/**
 * Composite: either specification must match.
 */
public class OrSpecification<T>(
    private val left: Specification<T>,
    private val right: Specification<T>,
) : Specification<T> {
    override fun isSatisfiedBy(candidate: T): Boolean =
        left.isSatisfiedBy(candidate) || right.isSatisfiedBy(candidate)
}

/**
 * Composite: inverts the wrapped specification.
 */
public class NotSpecification<T>(
    private val wrapped: Specification<T>,
) : Specification<T> {
    override fun isSatisfiedBy(candidate: T): Boolean =
        !wrapped.isSatisfiedBy(candidate)
}

/**
 * Always-true specification (catch-all).
 */
public class AlwaysTrue<T> : Specification<T> {
    override fun isSatisfiedBy(candidate: T): Boolean = true
}

/**
 * Always-false specification (reject-all).
 */
public class AlwaysFalse<T> : Specification<T> {
    override fun isSatisfiedBy(candidate: T): Boolean = false
}
