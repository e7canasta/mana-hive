package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * The calibration that Politica Engine produces for Scene Engine.
 * This is the CONTRACT between engines — lives in platform/contracts.
 *
 * Named "PolicyCalibration" (not "SceneCalibration") because:
 * - It's the output of the policy engine
 * - Scene Engine has its own SceneCalibration (compiled for the interpreter)
 * - Fowler: "Name things for what they ARE, not for where they're used"
 *
 * Vernon's ACL: this is the public interface of Politica engine.
 * Scene Engine only knows hysteresis, dwell, confidence — NOT
 * RiskLevel, MobilityAid, AlarmProfile.
 */
public data class PolicyCalibration(
    public val residentId: ResidentId,
    public val hysteresis: Map<TransitionKey, Duration>,
    public val dwellThresholds: Map<StateKind, DwellThreshold>,
    public val confidence: ConfidenceConfig,
)

/**
 * Confidence filtering rules for a resident.
 * Groups minConfidence and heartbeatTimeout — they always travel together.
 *
 * Fowler's "Extract Class": instead of two separate fields in PolicyCalibration,
 * we group them into a cohesive value object.
 *
 * Value Object (Vernon): no identity, compared by value.
 */
public data class ConfidenceConfig(
    public val minConfidence: Map<StateKind, Double>,
    public val heartbeatTimeout: Duration,
) {
    init {
        require(heartbeatTimeout >= Duration.ZERO) {
            "heartbeatTimeout must not be negative"
        }
        minConfidence.values.forEach { value ->
            require(value in 0.0..1.0) {
                "confidence must be in 0.0..1.0, got $value"
            }
        }
    }
}

/**
 * Key for hysteresis transitions: from → to.
 * Used as map key in AlarmCatalog and PolicyCalibration.
 *
 * Data class gives us stable hashCode/equals for map keys.
 */
public data class TransitionKey(
    public val from: StateKind,
    public val to: StateKind,
)

/**
 * Dwell thresholds for a state: warning and exceeded.
 * Warning fires first, exceeded fires later.
 *
 * Value Object (Vernon): no identity, compared by value.
 * Invariant: warning must be less than exceeded.
 */
public data class DwellThreshold(
    val warning: Duration,
    val exceeded: Duration,
) {
    init {
        require(warning < exceeded) {
            "warning ($warning) must be less than exceeded ($exceeded)"
        }
    }
}
