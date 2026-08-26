package com.manahive.scene

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.scene.SceneEvent
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.sweeper.createSweeper
import java.time.Instant

/**
 * Single entry point for the Scene Engine.
 *
 * Hides the complexity of [SceneInterpreter], [ClockSweeper],
 * [DigitalTwin] lifecycle and [DwellMarks] behind a simple
 * process-oriented API.
 *
 * Fowler: "Facade" — simplify a complex subsystem.
 * Vernon: "Published Interface" — the user doesn't need to know the internals.
 *
 * Usage:
 * ```kotlin
 * val engine = SceneEngine.create(calibration)
 * val result = engine.process(observations)
 * result.facts  // all SceneEvents produced
 * ```
 */
public class SceneEngine private constructor(
    private val calibration: SceneCalibration,
) {
    private val interpreter = createInterpreter(calibration)
    private val sweeper = createSweeper()
    private val dwellCatalog = calibration.toDwellCatalog()

    public companion object {
        public fun create(calibration: SceneCalibration): SceneEngine =
            SceneEngine(calibration)
    }

    /**
     * Processes observations, producing SceneEvents.
     * No sweep is performed between observations.
     */
    public fun process(
        observations: List<ObservedAt>,
        initialTwin: DigitalTwin? = null,
    ): SceneResult = processOnly(observations, initialTwin)

    /**
     * Processes observations with periodic sweep between events.
     * The sweep checks dwell thresholds and signal health.
     */
    public fun processWithSweep(
        observations: List<ObservedAt>,
        sweepIntervalSeconds: Long = 60,
        initialTwin: DigitalTwin? = null,
    ): SceneResult = processWithSweepInternal(observations, sweepIntervalSeconds, initialTwin)

    // ── Internal: process only (no sweep) ───────────────────────────────

    private fun processOnly(
        observations: List<ObservedAt>,
        initialTwin: DigitalTwin?,
    ): SceneResult {
        var twin = initialTwin ?: defaultTwin(observations.firstOrNull()?.at ?: Instant.EPOCH)
        val facts = mutableListOf<SceneEvent>()

        for (obs in observations) {
            val result = interpreter.interpret(twin, obs.observation, obs.at)
            twin = result.value.twin
            facts += result.value.facts
        }

        return SceneResult(facts, twin)
    }

    // ── Internal: process with sweep ────────────────────────────────────

    private fun processWithSweepInternal(
        observations: List<ObservedAt>,
        sweepIntervalSeconds: Long = 60,
        initialTwin: DigitalTwin? = null,
    ): SceneResult {
        var twin = initialTwin ?: defaultTwin(observations.firstOrNull()?.at ?: Instant.EPOCH)
        val facts = mutableListOf<SceneEvent>()
        var marks = DwellMarks(emptySet())
        var lastTime = twin.stateSince

        for (obs in observations) {
            val now = obs.at

            // Sweep between last event and this event
            var sweepTime = lastTime.plusSeconds(sweepIntervalSeconds)
            while (!sweepTime.isAfter(now)) {
                val result = sweeper.sweep(listOf(twin), sweepTime, dwellCatalog, marks)
                facts += result.value.facts
                marks = result.value.marks
                sweepTime = sweepTime.plusSeconds(sweepIntervalSeconds)
            }

            // Process the observation
            val result = interpreter.interpret(twin, obs.observation, now)
            twin = result.value.twin
            facts += result.value.facts
            lastTime = now

            // Sweep AT the event time
            val atResult = sweeper.sweep(listOf(twin), now, dwellCatalog, marks)
            facts += atResult.value.facts
            marks = atResult.value.marks
        }

        return SceneResult(facts, twin)
    }

    private fun defaultTwin(at: Instant): DigitalTwin = DigitalTwin(
        bed = com.manahive.kernel.BedId("default"),
        night = com.manahive.kernel.NightId("default"),
        occupant = null,
        state = com.manahive.contracts.scene.PersonState.Unknown(
            com.manahive.contracts.scene.UnknownCause.SCENE,
        ),
        stateSince = at,
        signal = SignalHealth(com.manahive.kernel.MonitorId("default"), at.minusSeconds(60), false),
    )
}

/**
 * An observation tied to a specific timestamp.
 */
public data class ObservedAt(
    val observation: Observation,
    val at: Instant,
)

/**
 * Result of processing observations through the Scene Engine.
 */
public data class SceneResult(
    val facts: List<SceneEvent>,
    val finalTwin: DigitalTwin,
)
