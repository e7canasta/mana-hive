package com.manahive.contracts.policy

/**
 * Version number for optimistic concurrency.
 *
 * Enforces positivity at construction time.
 * Cannot be bypassed via `copy()` like a data class `init` block.
 *
 * Fowler: "Replace Data Value with Object" — version is now a typed value.
 * Kotlin: "Inline class" — zero runtime overhead.
 *
 * @property value The version number (must be positive)
 */
@JvmInline
public value class Version(public val value: Int) {
    init {
        require(value > 0) { "version must be positive, got $value" }
    }

    override fun toString(): String = value.toString()
}
