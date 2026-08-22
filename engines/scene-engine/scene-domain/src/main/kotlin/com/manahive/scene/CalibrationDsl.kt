package com.manahive.scene

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.shared.HeartbeatBuilder
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * DSL for building [SceneCalibration] instances.
 *
 * ```kotlin
 * val cal = calibration {
 *     table = TransitionTable.RELEASE_2
 *     confidence(BED_EDGE) min 0.8
 *     confidence(STANDING) min 0.7
 *     dwell {
 *         STANDING warning 4.minutes exceeded 5.minutes
 *     }
 *     heartbeat {
 *         timeout = 90.seconds
 *     }
 * }
 * ```
 *
 * Uses the shared [DwellThresholdsBuilder] — no parallel hierarchy (Fowler).
 */
public fun calibration(block: CalibrationBuilder.() -> Unit): SceneCalibration =
    CalibrationBuilder().apply(block).build()

@SceneDsl
public class CalibrationBuilder {
    public var table: TransitionTable = TransitionTable.RELEASE_2

    private val confidence = mutableMapOf<StateKind, Double>()
    private val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()
    private var heartbeatTimeout: Duration = Duration.ofSeconds(90)

    public fun confidence(kind: StateKind): ConfidenceBuilder =
        ConfidenceBuilder(kind, confidence)

    public fun dwell(block: DwellThresholdsBuilder.() -> Unit) {
        DwellThresholdsBuilder(dwellThresholds).apply(block)
    }

    public fun heartbeat(block: HeartbeatBuilder.() -> Unit) {
        HeartbeatBuilder().apply(block).also { heartbeatTimeout = it.timeout }
    }

    internal fun build(): SceneCalibration =
        SceneCalibration(table, confidence.toMap(), heartbeatTimeout, dwellThresholds.toMap())
}

@SceneDsl
public class ConfidenceBuilder(
    private val kind: StateKind,
    private val confidence: MutableMap<StateKind, Double>,
) {
    public infix fun min(value: Double) {
        confidence[kind] = value
    }
}
