package com.manahive.contracts.policy

import com.manahive.contracts.common.Channel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * The calibration that Politica Engine produces for all downstream engines.
 * This is the CONTRACT between engines — lives in platform/contracts.
 *
 * Vernon's ACL: this is the public interface of Politica engine.
 * Downstream engines only know resolved rules — NOT
 * RiskLevel, MobilityAid, AlarmProfile.
 *
 * Each engine's data is grouped into its own value object (Fowler: Extract Class).
 * The adapters extract the relevant section for each engine.
 */
public data class PolicyCalibration(
    public val residentId: ResidentId,
    public val scene: ScenePolicy,
    public val sentinel: SentinelPolicy,
    public val harbor: HarborPolicy,
    public val recorder: RecorderPolicy,
)

/**
 * Resolved rules for Scene Engine.
 * Contains dwell thresholds, hysteresis, and confidence filtering.
 */
public data class ScenePolicy(
    val hysteresis: Map<TransitionKey, Duration>,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
    val confidence: ConfidenceConfig,
)

/**
 * Resolved rules for Sentinel Engine.
 * Contains alert rules derived from ResidentStateRule in catalog.
 */
public data class SentinelPolicy(
    val alertRules: Map<StateKind, AlertRule>,
)

/**
 * Resolved rules for Harbor Engine.
 * Contains notification channels and escalation timeouts per severity.
 *
 * Channel is a shared type definition (like a C header) from contracts/common.
 * No circular dependency — harbor-domain already depends on contracts.
 */
public data class HarborPolicy(
    val defaultChannels: Map<Severity, Set<Channel>>,
    val escalationTimeouts: Map<Severity, Duration>,
)

/**
 * Resolved rules for Recorder Engine.
 * Contains recording windows for specific transitions.
 */
public data class RecorderPolicy(
    /** Recording windows keyed by transition (from → to). */
    val transitionWindows: Map<TransitionKey, TransitionWindow>,
)

/**
 * Recording window for a specific transition.
 * Derived from DagTransitionRule.recordBefore/recordAfter.
 *
 * Value Object (Vernon): no identity, compared by value.
 */
public data class TransitionWindow(
    val before: Duration,
    val after: Duration,
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
