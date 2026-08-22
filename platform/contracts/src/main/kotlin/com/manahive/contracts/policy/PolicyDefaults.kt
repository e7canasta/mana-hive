package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Centralized defaults for policy calibration.
 *
 * Fowler's "Magic Number" smell: instead of scattering
 * `Duration.ofSeconds(90)` across files, we have one source of truth.
 */
public object PolicyDefaults {
    public val heartbeatTimeout: Duration = Duration.ofSeconds(90)
    public val minConfidence: Map<StateKind, Double> = emptyMap()
}
