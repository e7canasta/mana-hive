package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Typed payload for policy buckets. Sealed interface for pattern matching.
 *
 * Each category has its own payload type with domain-specific fields.
 * This replaces Map<String, Any> — Kotlin compiler enforces correctness.
 *
 * Fowler: "Introduce Parameter Object" + "Replace Type Code with Subclasses"
 * Vernon: Value Object — no identity, compared by value.
 */
public sealed interface PolicyPayload {
    /** Content fingerprint for change detection. */
    public val fingerprint: Fingerprint
}

/**
 * Calibration payload for Scene Engine.
 * Contains dwell thresholds, hysteresis config, and confidence filtering.
 */
public data class CalibrationPayload(
    public val dwellThresholds: Map<StateKind, DwellThreshold>,
    public val hysteresis: Map<TransitionKey, Duration>,
    public val confidence: ConfidenceConfig,
    override val fingerprint: Fingerprint,
) : PolicyPayload {

    /** Convert to the existing PolicyCalibration contract. */
    public fun toPolicyCalibration(residentId: ResidentId): PolicyCalibration =
        PolicyCalibration(
            residentId = residentId,
            hysteresis = hysteresis,
            dwellThresholds = dwellThresholds,
            confidence = confidence,
        )
}

/**
 * Response payload for Sentinel.
 * Contains alert rules that define what triggers an episode and how it closes.
 */
public data class ResponsePayload(
    public val rules: List<AlertRule>,
    override val fingerprint: Fingerprint,
) : PolicyPayload

/**
 * Escalation payload for Harbor.
 * Contains escalation config and staff assist rules.
 */
public data class EscalationPayload(
    public val config: EscalationConfig,
    override val fingerprint: Fingerprint,
) : PolicyPayload

/**
 * Recording payload for Recorder.
 * Contains recording triggers, window config, and quality settings.
 */
public data class RecordingPayload(
    public val config: RecordingConfig,
    override val fingerprint: Fingerprint,
) : PolicyPayload
