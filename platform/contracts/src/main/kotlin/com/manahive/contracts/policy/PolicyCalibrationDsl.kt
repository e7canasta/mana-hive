package com.manahive.contracts.policy

import com.manahive.contracts.shared.HeartbeatBuilder
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * DSL for building [PolicyCalibration] instances.
 *
 * ```kotlin
 * val calibration = buildPolicyCalibration {
 *     resident(ResidentId("maria"))
 *
 *     hysteresis {
 *         from(LYING) { to(BED_EDGE) after 1500.ms }
 *         from(BED_EDGE) { to(STANDING) after 1500.ms }
 *     }
 *
 *     dwell {
 *         STANDING warning 4.minutes exceeded 5.minutes
 *         BED_EDGE warning 2.minutes exceeded 3.minutes
 *     }
 *
 *     confidence {
 *         BED_EDGE min 0.9
 *         STANDING min 0.85
 *     }
 *
 *     heartbeat {
 *         timeout 90.seconds
 *     }
 * }
 * ```
 *
 * Fowler: "A DSL should read like natural language."
 * Vernon: "Domain invariants are validated at the boundary."
 */
public fun buildPolicyCalibration(block: PolicyCalibrationBuilder.() -> Unit): PolicyCalibration =
    PolicyCalibrationBuilder().apply(block).build()

@PolicyDsl
public class PolicyCalibrationBuilder {
    private var residentId: ResidentId? = null
    private val hysteresis = mutableMapOf<TransitionKey, Duration>()
    private val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()
    private val minConfidence = mutableMapOf<StateKind, Double>()
    private var heartbeatTimeout: Duration = PolicyDefaults.heartbeatTimeout

    public fun resident(id: ResidentId) {
        residentId = id
    }

    public fun hysteresis(block: HysteresisBuilder.() -> Unit) {
        HysteresisBuilder(hysteresis).apply(block)
    }

    public fun dwell(block: DwellBuilder.() -> Unit) {
        DwellBuilder(dwellThresholds).apply(block)
    }

    public fun confidence(block: ConfidenceBuilder.() -> Unit) {
        ConfidenceBuilder(minConfidence).apply(block)
    }

    public fun heartbeat(block: HeartbeatBuilder.() -> Unit) {
        HeartbeatBuilder().apply(block).also { heartbeatTimeout = it.timeout }
    }

    internal fun build(): PolicyCalibration {
        val id = requireNotNull(residentId) { "resident() must be called" }
        return PolicyCalibration(
            residentId = id,
            hysteresis = hysteresis.toMap(),
            dwellThresholds = dwellThresholds.toMap(),
            confidence = ConfidenceConfig(
                minConfidence = minConfidence.toMap(),
                heartbeatTimeout = heartbeatTimeout,
            ),
        )
    }
}

@PolicyDsl
public class HysteresisBuilder(
    private val hysteresis: MutableMap<TransitionKey, Duration>,
) {
    public fun from(from: StateKind, block: TransitionFromBuilder.() -> Unit) {
        TransitionFromBuilder(from, hysteresis).apply(block)
    }
}

@PolicyDsl
public class TransitionFromBuilder(
    private val from: StateKind,
    private val hysteresis: MutableMap<TransitionKey, Duration>,
) {
    public fun to(to: StateKind): TransitionToBuilder =
        TransitionToBuilder(from, to, hysteresis)
}

@PolicyDsl
public class TransitionToBuilder(
    private val from: StateKind,
    private val to: StateKind,
    private val hysteresis: MutableMap<TransitionKey, Duration>,
) {
    public infix fun after(duration: Duration) {
        hysteresis[TransitionKey(from, to)] = duration
    }
}

@PolicyDsl
public class DwellBuilder(
    private val dwellThresholds: MutableMap<StateKind, DwellThreshold>,
) {
    public infix fun StateKind.warning(warning: Duration): DwellWarningBuilder =
        DwellWarningBuilder(this, warning, dwellThresholds)
}

@PolicyDsl
public class DwellWarningBuilder(
    private val state: StateKind,
    private val warning: Duration,
    private val dwellThresholds: MutableMap<StateKind, DwellThreshold>,
) {
    public infix fun exceeded(exceeded: Duration) {
        dwellThresholds[state] = DwellThreshold(warning, exceeded)
    }
}

@PolicyDsl
public class ConfidenceBuilder(
    private val minConfidence: MutableMap<StateKind, Double>,
) {
    public infix fun StateKind.min(value: Double) {
        require(value in 0.0..1.0) { "confidence must be in 0.0..1.0, got $value" }
        minConfidence[this] = value
    }
}

// HeartbeatBuilder is shared via com.manahive.contracts.shared.HeartbeatBuilder
