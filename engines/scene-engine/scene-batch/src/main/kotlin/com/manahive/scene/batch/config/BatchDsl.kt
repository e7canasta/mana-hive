package com.manahive.scene.batch.config

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.core.TransitionTable
import java.time.Duration
import java.time.Instant

/**
 * DSL for building [BatchConfig] instances.
 *
 * Pattern: Interpreter (Vernon) — the DSL is a mini-language for config.
 * Pattern: Builder (GoF) — hides construction complexity.
 *
 * ```kotlin
 * val config = sceneConfig {
 *     scene {
 *         bed = "bed-1"
 *         night = "night-1"
 *         resident = "maria"
 *         monitor = "m1"
 *     }
 *     calibration {
 *         transitions = TransitionTable.RELEASE_2
 *         confidence(StateKind.BED_EDGE) min 0.8
 *         dwell {
 *             STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
 *         }
 *         heartbeat { timeout = Duration.ofSeconds(90) }
 *     }
 *     events {
 *         source = "events.dat"
 *         output = "output"
 *     }
 * }
 * ```
 */
@DslMarker
annotation class SceneBatchDsl

fun sceneConfig(block: BatchConfigBuilder.() -> Unit): BatchConfig =
    BatchConfigBuilder().apply(block).build()

@SceneBatchDsl
class BatchConfigBuilder {
    private var scene: SceneConfig = SceneConfig("", "", "", "")
    private var calibration: CalibrationConfig = CalibrationConfig()
    private var events: EventsConfig = EventsConfig("")

    fun scene(block: SceneConfigBuilder.() -> Unit) {
        scene = SceneConfigBuilder().apply(block).build()
    }

    fun calibration(block: CalibrationConfigBuilder.() -> Unit) {
        calibration = CalibrationConfigBuilder().apply(block).build()
    }

    fun events(block: EventsConfigBuilder.() -> Unit) {
        events = EventsConfigBuilder().apply(block).build()
    }

    fun build(): BatchConfig = BatchConfig(scene, calibration, events)
}

@SceneBatchDsl
class SceneConfigBuilder {
    var bed: String = ""
    var night: String = ""
    var resident: String = ""
    var monitor: String = ""

    fun build(): SceneConfig = SceneConfig(bed, night, resident, monitor)
}

@SceneBatchDsl
class CalibrationConfigBuilder {
    var transitions: TransitionTable = TransitionTable.RELEASE_2
    private val confidence = mutableMapOf<StateKind, Double>()
    private val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()
    private var heartbeatTimeout: Duration = Duration.ofSeconds(90)

    fun confidence(kind: StateKind): ConfidenceBuilder = ConfidenceBuilder(kind, confidence)

    fun dwell(block: DwellConfigBuilder.() -> Unit) {
        DwellConfigBuilder(dwellThresholds).apply(block)
    }

    fun heartbeat(block: HeartbeatConfigBuilder.() -> Unit) {
        heartbeatTimeout = HeartbeatConfigBuilder().apply(block).timeout
    }

    fun build(): CalibrationConfig = CalibrationConfig(
        transitionTable = transitions,
        confidence = confidence.toMap(),
        dwellThresholds = dwellThresholds.toMap(),
        heartbeatTimeout = heartbeatTimeout,
    )
}

@SceneBatchDsl
class ConfidenceBuilder(
    private val kind: StateKind,
    private val confidence: MutableMap<StateKind, Double>,
) {
    infix fun min(value: Double) {
        confidence[kind] = value
    }
}

@SceneBatchDsl
class DwellConfigBuilder(
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    val LYING: DwellStateBuilder get() = DwellStateBuilder(StateKind.LYING, thresholds)
    val SITTING_IN_BED: DwellStateBuilder get() = DwellStateBuilder(StateKind.SITTING_IN_BED, thresholds)
    val ATTEMPTING_EXIT: DwellStateBuilder get() = DwellStateBuilder(StateKind.ATTEMPTING_EXIT, thresholds)
    val BED_EDGE: DwellStateBuilder get() = DwellStateBuilder(StateKind.BED_EDGE, thresholds)
    val STANDING: DwellStateBuilder get() = DwellStateBuilder(StateKind.STANDING, thresholds)
    val IN_BATHROOM: DwellStateBuilder get() = DwellStateBuilder(StateKind.IN_BATHROOM, thresholds)
    val IN_ROOM: DwellStateBuilder get() = DwellStateBuilder(StateKind.IN_ROOM, thresholds)
    val IN_HALLWAY: DwellStateBuilder get() = DwellStateBuilder(StateKind.IN_HALLWAY, thresholds)
    val OUTDOOR: DwellStateBuilder get() = DwellStateBuilder(StateKind.OUTDOOR, thresholds)
    val ABSENT: DwellStateBuilder get() = DwellStateBuilder(StateKind.ABSENT, thresholds)
    val IN_CHAIR: DwellStateBuilder get() = DwellStateBuilder(StateKind.IN_CHAIR, thresholds)
    val IN_WHEELCHAIR: DwellStateBuilder get() = DwellStateBuilder(StateKind.IN_WHEELCHAIR, thresholds)
}

@SceneBatchDsl
class DwellStateBuilder(
    private val kind: StateKind,
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    infix fun warning(warning: Duration): DwellExceededBuilder =
        DwellExceededBuilder(kind, warning, thresholds)

    infix fun exceeded(exceeded: Duration) {
        thresholds[kind] = DwellThreshold(Duration.ZERO, exceeded)
    }
}

@SceneBatchDsl
class DwellExceededBuilder(
    private val kind: StateKind,
    private val warning: Duration,
    private val thresholds: MutableMap<StateKind, DwellThreshold>,
) {
    infix fun exceeded(exceeded: Duration) {
        thresholds[kind] = DwellThreshold(warning, exceeded)
    }
}

@SceneBatchDsl
class HeartbeatConfigBuilder {
    var timeout: Duration = Duration.ofSeconds(90)
}

@SceneBatchDsl
class EventsConfigBuilder {
    var source: String = ""
    var output: String = "output"
    var start: Instant? = null

    fun build(): EventsConfig = EventsConfig(source, output, start)
}
