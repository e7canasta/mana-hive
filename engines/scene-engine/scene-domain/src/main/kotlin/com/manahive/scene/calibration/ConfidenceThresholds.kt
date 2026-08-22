package com.manahive.scene.calibration

import com.manahive.contracts.scene.StateKind

/**
 * Value Object: confidence thresholds per state kind.
 *
 * Encapsulates the map + default fallback logic.
 * Fowler: "Replace Data Value with Object".
 */
public data class ConfidenceThresholds(
    private val byState: Map<StateKind, Confidence>,
    private val default: Confidence = Confidence.ZERO,
) {
    public fun forState(kind: StateKind): Confidence = byState[kind] ?: default
}
