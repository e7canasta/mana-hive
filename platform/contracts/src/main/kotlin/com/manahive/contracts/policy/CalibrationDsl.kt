package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Build a [CalibrationPayload] with type-safe DSL.
 *
 * ```kotlin
 * val payload = buildCalibrationPayload(ResidentId("maria")) {
 *     dwell(StateKind.IN_BATHROOM, warning = 20.seconds, exceeded = 45.seconds)
 *     hysteresis(StateKind.LYING, StateKind.SITTING_IN_BED, 3.seconds)
 *     confidence(StateKind.STANDING, 0.8)
 *     heartbeatTimeout(90.seconds)
 * }
 * ```
 */
public fun buildCalibrationPayload(
    residentId: ResidentId,
    init: CalibrationBuilder.() -> Unit,
): CalibrationPayload {
    val builder = CalibrationBuilder(residentId)
    builder.init()
    return builder.build()
}

@PolicyDsl
public class CalibrationBuilder(private val residentId: ResidentId) {
    private val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()
    private val hysteresis = mutableMapOf<TransitionKey, Duration>()
    private val minConfidence = mutableMapOf<StateKind, Double>()
    private var heartbeatTimeout: Duration = Duration.ofSeconds(90)

    /** Add a dwell threshold for a state. Warning must be < exceeded. */
    public fun dwell(state: StateKind, warning: Duration, exceeded: Duration) {
        require(warning < exceeded) {
            "dwell warning ($warning) must be less than exceeded ($exceeded)"
        }
        dwellThresholds[state] = DwellThreshold(warning, exceeded)
    }

    /** Add a hysteresis transition: from → to with duration. */
    public fun hysteresis(from: StateKind, to: StateKind, duration: Duration) {
        require(duration >= Duration.ZERO) { "hysteresis must not be negative" }
        hysteresis[TransitionKey(from, to)] = duration
    }

    /** Set minimum confidence for a state. Must be in 0.0..1.0. */
    public fun confidence(state: StateKind, min: Double) {
        require(min in 0.0..1.0) { "confidence must be in 0.0..1.0, got $min" }
        minConfidence[state] = min
    }

    /** Set heartbeat timeout. Must be non-negative. */
    public fun heartbeatTimeout(timeout: Duration) {
        require(timeout >= Duration.ZERO) { "heartbeatTimeout must not be negative" }
        heartbeatTimeout = timeout
    }

    /** Build the payload with fingerprint. */
    public fun build(): CalibrationPayload {
        val fingerprint = buildFingerprint()
        return CalibrationPayload(
            dwellThresholds = dwellThresholds.toMap(),
            hysteresis = hysteresis.toMap(),
            confidence = ConfidenceConfig(
                minConfidence = minConfidence.toMap(),
                heartbeatTimeout = heartbeatTimeout,
            ),
            fingerprint = fingerprint,
        )
    }

    private fun buildFingerprint(): Fingerprint = buildFingerprint(
        "dwell" to dwellThresholds,
        "hysteresis" to hysteresis,
        "confidence" to minConfidence,
        "heartbeat" to heartbeatTimeout,
    )
}
