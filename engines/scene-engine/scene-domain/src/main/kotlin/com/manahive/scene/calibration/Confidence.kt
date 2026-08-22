package com.manahive.scene.calibration

/**
 * Value Object: confidence level for an observation.
 *
 * Type-safe wrapper around Double that enforces 0.0..1.0 range.
 * Fowler: "Replace Primitive with Value Object".
 */
@JvmInline
public value class Confidence(public val value: Double) {
    init {
        require(value in 0.0..1.0) { "Confidence must be 0..1, was $value" }
    }

    public companion object {
        public val ZERO: Confidence = Confidence(0.0)
        public val FULL: Confidence = Confidence(1.0)
    }
}
