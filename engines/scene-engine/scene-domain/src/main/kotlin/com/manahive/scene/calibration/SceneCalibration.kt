package com.manahive.scene.calibration

import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.core.TransitionTable
import java.time.Duration

/**
 * Compiled business rules for one bed's scene engine: transition table,
 * confidence thresholds, heartbeat timeout, and dwell thresholds.
 *
 * Consumed by [com.manahive.scene.interpreter.SceneInterpreterImpl] and
 * derived into [DwellCatalog] for [com.manahive.scene.sweeper.ClockSweeper].
 */
public data class SceneCalibration(
    public val table: TransitionTable,
    public val confidence: ConfidenceThresholds,
    public val heartbeatTimeout: Duration,
    // ── Person State ──────────────────────────────────────────
    public val dwellThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    // ── ComeBack (Inverse Dwell) ──────────────────────────────
    // Measures time AWAY from a baseline state, not time IN a state.
    // Key: the baseline state (e.g., LYING). Value: warning/exceeded thresholds.
    public val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    // ── Scene State ───────────────────────────────────────────
    public val sceneHysteresis: Map<String, Duration> = emptyMap(),  // field → hysteresis
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),  // field → dwell
    public val sceneConfidence: Map<ObservationKind, Confidence> = emptyMap(),  // event → min confidence
) {
    /**
     * Get hysteresis for a scene state field.
     * Default: 0 seconds (no hysteresis for scene events unless configured).
     */
    public fun sceneHysteresisFor(field: String): Duration = sceneHysteresis[field] ?: Duration.ZERO

    /**
     * Get dwell threshold for a scene state field.
     * Returns null if no threshold configured for this field.
     */
    public fun sceneDwellFor(field: String): DwellThreshold? = sceneThresholds[field]

    /**
     * Get minimum confidence for a scene event.
     * Default: 0.0 (accept all).
     */
    public fun sceneConfidenceFor(kind: ObservationKind): Confidence = sceneConfidence[kind] ?: Confidence(0.0)
}

// ── DSL ─────────────────────────────────────────────────────────────────────

/**
 * Type-safe DSL for building [SceneCalibration] instances.
 *
 * Example:
 * ```kotlin
 * val calibration = sceneCalibration {
 *     table = TransitionTable.RELEASE_2
 *     confidence(StateKind.STANDING) min 0.8
 *     dwell {
 *         STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
 *     }
 *     comeBack {
 *         LYING warning Duration.ofMinutes(12) exceeded Duration.ofMinutes(15)
 *     }
 *     sceneHysteresis {
 *         "staff" hysteresis Duration.ofSeconds(1.5)
 *     }
 *     sceneDwell {
 *         "staff" warning Duration.ofMinutes(10) exceeded Duration.ofMinutes(30)
 *     }
 *     sceneConfidence {
 *         STAFF_ENTERED min 0.8
 *     }
 * }
 * ```
 */
public fun sceneCalibration(init: SceneCalibrationBuilder.() -> Unit): SceneCalibration =
    SceneCalibrationBuilder().apply(init).build()

@SceneCalibrationDsl
public class SceneCalibrationBuilder {
    public var table: TransitionTable = TransitionTable.RELEASE_2
    public var heartbeatTimeout: Duration = Duration.ofSeconds(90)

    private val confidenceBuilder = ConfidenceThresholdsBuilder()
    private val dwellBuilder = DwellThresholdsBuilder()
    private val comeBackBuilder = ComeBackThresholdsBuilder()
    private val sceneHysteresisBuilder = SceneHysteresisBuilder()
    private val sceneDwellBuilder = SceneDwellThresholdsBuilder()
    private val sceneConfidenceBuilder = SceneConfidenceBuilder()

    public fun confidence(init: ConfidenceThresholdsBuilder.() -> Unit) {
        confidenceBuilder.apply(init)
    }

    public fun dwell(init: DwellThresholdsBuilder.() -> Unit) {
        dwellBuilder.apply(init)
    }

    /**
     * Configure come-back (inverse dwell) thresholds.
     *
     * Unlike normal dwell which measures time IN a state,
     * come-back measures time SINCE LEAVING a baseline state.
     *
     * Example:
     * ```kotlin
     * comeBack {
     *     LYING warning Duration.ofMinutes(12) exceeded Duration.ofMinutes(15)
     * }
     * ```
     *
     * This means: if the resident has been away from LYING for more than
     * 12 minutes, emit a warning. If more than 15 minutes, emit exceeded.
     */
    public fun comeBack(init: ComeBackThresholdsBuilder.() -> Unit) {
        comeBackBuilder.apply(init)
    }

    public fun sceneHysteresis(init: SceneHysteresisBuilder.() -> Unit) {
        sceneHysteresisBuilder.apply(init)
    }

    public fun sceneDwell(init: SceneDwellThresholdsBuilder.() -> Unit) {
        sceneDwellBuilder.apply(init)
    }

    public fun sceneConfidence(init: SceneConfidenceBuilder.() -> Unit) {
        sceneConfidenceBuilder.apply(init)
    }

    public fun build(): SceneCalibration = SceneCalibration(
        table = table,
        confidence = confidenceBuilder.build(),
        heartbeatTimeout = heartbeatTimeout,
        dwellThresholds = dwellBuilder.build(),
        comeBackThresholds = comeBackBuilder.build(),
        sceneHysteresis = sceneHysteresisBuilder.build(),
        sceneThresholds = sceneDwellBuilder.build(),
        sceneConfidence = sceneConfidenceBuilder.build(),
    )
}

@SceneCalibrationDsl
public class ConfidenceThresholdsBuilder {
    private val thresholds = mutableMapOf<StateKind, Confidence>()

    public infix fun StateKind.min(value: Double) {
        thresholds[this] = Confidence(value)
    }

    public fun build(): ConfidenceThresholds = ConfidenceThresholds(thresholds)
}

// ── Generic Dwell Validation (Fowler: Extract Method) ────────────────────────

private fun <K> requireValidDwell(key: K, warning: Duration, exceeded: Duration, label: (K) -> String) {
    require(warning < exceeded) {
        "${label(key)}: warning ($warning) must be less than exceeded ($exceeded)"
    }
}

// ── Person State Dwell ───────────────────────────────────────────────────────

@SceneCalibrationDsl
public class DwellThresholdsBuilder {
    private val thresholds = mutableMapOf<StateKind, DwellThreshold>()

    public inner class DwellEntry(private val state: StateKind) {
        public infix fun warning(duration: Duration): DwellWarningEntry = DwellWarningEntry(state, duration)
    }

    public inner class DwellWarningEntry(private val state: StateKind, private val warning: Duration) {
        public infix fun exceeded(duration: Duration) {
            requireValidDwell(state, warning, duration) { "dwell ${it.name}" }
            thresholds[state] = DwellThreshold(warning, duration)
        }
    }

    public val STANDING: DwellEntry get() = DwellEntry(StateKind.STANDING)
    public val LYING: DwellEntry get() = DwellEntry(StateKind.LYING)
    public val SITTING_IN_BED: DwellEntry get() = DwellEntry(StateKind.SITTING_IN_BED)
    public val BED_EDGE: DwellEntry get() = DwellEntry(StateKind.BED_EDGE)
    public val IN_BATHROOM: DwellEntry get() = DwellEntry(StateKind.IN_BATHROOM)
    public val IN_HALLWAY: DwellEntry get() = DwellEntry(StateKind.IN_HALLWAY)
    public val IN_ROOM: DwellEntry get() = DwellEntry(StateKind.IN_ROOM)
    public val OUTDOOR: DwellEntry get() = DwellEntry(StateKind.OUTDOOR)
    public val ABSENT: DwellEntry get() = DwellEntry(StateKind.ABSENT)

    /**
     * Acceso genérico por estado.
     *
     * Las propiedades de arriba son azúcar para escribir calibraciones a mano.
     * Un adapter que traduce un mapa debe usar esto: enumerar estados a mano
     * hace que olvidar uno pierda en silencio el umbral que el director
     * configuró. Es lo que pasaba con ABSENT — la fila "fuera de la habitación"
     * de los cuatro niveles no llegaba nunca al motor de escena.
     */
    public fun state(kind: StateKind): DwellEntry = DwellEntry(kind)

    public fun build(): Map<StateKind, DwellThreshold> = thresholds.toMap()
}

// ── ComeBack (Inverse Dwell) ─────────────────────────────────────────────────

@SceneCalibrationDsl
public class ComeBackThresholdsBuilder {
    private val thresholds = mutableMapOf<StateKind, DwellThreshold>()

    public inner class ComeBackEntry(private val baseline: StateKind) {
        public infix fun warning(duration: Duration): ComeBackWarningEntry =
            ComeBackWarningEntry(baseline, duration)
    }

    public inner class ComeBackWarningEntry(
        private val baseline: StateKind,
        private val warning: Duration,
    ) {
        public infix fun exceeded(duration: Duration) {
            requireValidDwell(baseline, warning, duration) { "comeBack ${it.name}" }
            thresholds[baseline] = DwellThreshold(warning, duration)
        }
    }

    public val LYING: ComeBackEntry get() = ComeBackEntry(StateKind.LYING)
    public val STANDING: ComeBackEntry get() = ComeBackEntry(StateKind.STANDING)

    public fun build(): Map<StateKind, DwellThreshold> = thresholds.toMap()
}

// ── Scene Dwell ──────────────────────────────────────────────────────────────

@SceneCalibrationDsl
public class SceneDwellThresholdsBuilder {
    private val thresholds = mutableMapOf<String, DwellThreshold>()

    public inner class SceneDwellEntry(private val field: String) {
        public infix fun warning(duration: Duration): SceneDwellWarningEntry = SceneDwellWarningEntry(field, duration)
    }

    public inner class SceneDwellWarningEntry(private val field: String, private val warning: Duration) {
        public infix fun exceeded(duration: Duration) {
            requireValidDwell(field, warning, duration) { "sceneDwell $it" }
            thresholds[field] = DwellThreshold(warning, duration)
        }
    }

    public val staff: SceneDwellEntry get() = SceneDwellEntry("staff")
    public val wheelchair: SceneDwellEntry get() = SceneDwellEntry("wheelchair")
    public val walker: SceneDwellEntry get() = SceneDwellEntry("walker")
    public val bedLeft: SceneDwellEntry get() = SceneDwellEntry("bed.left")
    public val bedRight: SceneDwellEntry get() = SceneDwellEntry("bed.right")

    public fun build(): Map<String, DwellThreshold> = thresholds.toMap()
}

@SceneCalibrationDsl
public class SceneHysteresisBuilder {
    private val hysteresis = mutableMapOf<String, Duration>()

    public infix fun String.hysteresis(duration: Duration) {
        hysteresis[this] = duration
    }

    public fun build(): Map<String, Duration> = hysteresis.toMap()
}

@SceneCalibrationDsl
public class SceneConfidenceBuilder {
    private val confidence = mutableMapOf<ObservationKind, Confidence>()

    public infix fun ObservationKind.min(value: Double) {
        confidence[this] = Confidence(value)
    }

    public fun build(): Map<ObservationKind, Confidence> = confidence.toMap()
}

@DslMarker
public annotation class SceneCalibrationDsl
