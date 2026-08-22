package com.manahive.scene

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.calibration.dsl.calibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.core.TransitionTable
import com.manahive.scene.interpreter.SceneInterpreter
import com.manahive.scene.interpreter.createInterpreter
import java.time.Duration
import java.time.Instant

/**
 * Scene Domain Playground
 *
 * Ejecuta: ./gradlew :engines:scene-engine:scene-domain:run
 *
 * Prueba manual del SceneInterpreter con escenarios clínicos reales.
 * Modifica los parámetros para explorar comportamiento del motor.
 */
internal fun main() {
    println("╔════════════════════════════════════════════════════════════╗")
    println("║         Scene Domain — Interpreter Playground            ║")
    println("╚════════════════════════════════════════════════════════════╝")
    println()

    val cal = buildCalibration()
    val interpreter = createInterpreter(cal)

    runScenario1_LyingToBedEdge(interpreter)
    runScenario2_SignalLostAndRecovered(interpreter)
    runScenario3_DwellWarningAndExceeded(interpreter)
    runScenario4_IllegalTransition(interpreter)
    runScenario5_LowConfidence(interpreter)
    runScenario6_HysteresisBlock(interpreter)
    runScenario7_SittingInBedToStanding(interpreter)
    runScenario8_OutdoorSequence(interpreter)

    println()
    println("══════════════════════════════════════════════════════════════")
    println("  All scenarios completed.")
    println("══════════════════════════════════════════════════════════════")
}

// ── Calibration ─────────────────────────────────────────────────────────────

private fun buildCalibration(): SceneCalibration = calibration {
    table = TransitionTable.RELEASE_2

    confidence(StateKind.BED_EDGE) min 0.8
    confidence(StateKind.STANDING) min 0.7
    confidence(StateKind.IN_BATHROOM) min 0.75
    confidence(StateKind.ATTEMPTING_EXIT) min 0.85

    dwell {
        STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
        IN_BATHROOM warning Duration.ofMinutes(3) exceeded Duration.ofMinutes(4)
        BED_EDGE warning Duration.ofMinutes(1) exceeded Duration.ofMinutes(2)
    }

    heartbeat {
        timeout = Duration.ofSeconds(90)
    }
}

// ── Scenario 1: Lying → BedEdge ────────────────────────────────────────────

private fun runScenario1_LyingToBedEdge(interpreter: SceneInterpreter) {
    println("── Scenario 1: Lying → BedEdge (transition normal) ──")

    val bed = BedId("bed-1")
    val night = NightId("night-1")
    val monitor = MonitorId("m1")
    val maria = ResidentId("maria")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")
    val t1 = Instant.parse("2024-01-01T03:00:02Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = maria,
        state = PersonState.Lying,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-001",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.BED_EDGE,
        confidence = 0.92,
        observedAt = t1,
    )

    val result = interpreter.interpret(twin, obs, t1)

    println("  Before: ${twin.state} (since ${twin.stateSince})")
    println("  Observation: ${obs.kind} (confidence=${obs.confidence})")
    println("  After:  ${result.value.twin.state} (since ${result.value.twin.stateSince})")
    println("  Facts:  ${result.value.facts}")
    println("  Discards: ${result.discards}")
    println()
}

// ── Scenario 2: Signal Lost & Recovered ─────────────────────────────────────

private fun runScenario2_SignalLostAndRecovered(interpreter: SceneInterpreter) {
    println("── Scenario 2: Signal Lost → Recovered ──")

    val bed = BedId("bed-2")
    val night = NightId("night-1")
    val monitor = MonitorId("m2")
    val jose = ResidentId("jose")
    val t0 = Instant.parse("2024-01-01T02:50:00Z")
    val tObs = Instant.parse("2024-01-01T03:02:30Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = jose,
        state = PersonState.SittingInBed,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(120), true),
    )

    val obs = Observation(
        sourceEventId = "obs-002",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.SITTING_IN_BED,
        confidence = 0.88,
        observedAt = tObs,
    )

    val result = interpreter.interpret(twin, obs, tObs)

    println("  Before: state=${twin.state}, signal.lost=${twin.signal.lost}")
    println("  Observation: ${obs.kind} (confidence=${obs.confidence})")
    println("  After:  state=${result.value.twin.state}, signal.lost=${result.value.twin.signal.lost}")
    println("  Facts:  ${result.value.facts}")
    println()
}

// ── Scenario 3: Dwell Warning & Exceeded ────────────────────────────────────

private fun runScenario3_DwellWarningAndExceeded(interpreter: SceneInterpreter) {
    println("── Scenario 3: Dwell Warning & Exceeded (Standing > 4min → 5min) ──")

    val bed = BedId("bed-3")
    val night = NightId("night-1")
    val monitor = MonitorId("m3")
    val maria = ResidentId("maria")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")

    val tCheck = Instant.parse("2024-01-01T03:04:30Z")
    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = maria,
        state = PersonState.Standing,
        stateSince = t0,
        signal = SignalHealth(monitor, tCheck.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-003",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.STANDING,
        confidence = 0.95,
        observedAt = tCheck,
    )

    val result = interpreter.interpret(twin, obs, tCheck)

    val duration = Duration.between(t0, tCheck)
    println("  Twin in Standing for: ${duration.toMinutes()}min ${duration.toSecondsPart()}s")
    println("  Warning threshold: 4min, Exceeded threshold: 5min")
    println("  After: state=${result.value.twin.state}")
    println("  Facts: ${result.value.facts}")
    println("  Discards: ${result.discards}")
    println("  Note: DwellWarning/DwellExceeded emitted by ClockSweeper, not Interpreter")
    println()
}

// ── Scenario 4: Illegal Transition ──────────────────────────────────────────

private fun runScenario4_IllegalTransition(interpreter: SceneInterpreter) {
    println("── Scenario 4: Illegal Transition (Lying → InBathroom) ──")

    val bed = BedId("bed-4")
    val night = NightId("night-1")
    val monitor = MonitorId("m4")
    val jose = ResidentId("jose")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")
    val t1 = Instant.parse("2024-01-01T03:00:03Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = jose,
        state = PersonState.Lying,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-004",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.IN_BATHROOM,
        confidence = 0.95,
        observedAt = t1,
    )

    val result = interpreter.interpret(twin, obs, t1)

    println("  Before: ${twin.state}")
    println("  Observation: ${obs.kind} (confidence=${obs.confidence})")
    println("  After:  ${result.value.twin.state} (should remain Lying)")
    println("  Discards: ${result.discards}")
    println()
}

// ── Scenario 5: Low Confidence ──────────────────────────────────────────────

private fun runScenario5_LowConfidence(interpreter: SceneInterpreter) {
    println("── Scenario 5: Low Confidence (BED_EDGE with confidence=0.5 < 0.8) ──")

    val bed = BedId("bed-5")
    val night = NightId("night-1")
    val monitor = MonitorId("m5")
    val maria = ResidentId("maria")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")
    val t1 = Instant.parse("2024-01-01T03:00:02Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = maria,
        state = PersonState.Lying,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-005",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.BED_EDGE,
        confidence = 0.5,
        observedAt = t1,
    )

    val result = interpreter.interpret(twin, obs, t1)

    println("  Before: ${twin.state}")
    println("  Observation: ${obs.kind} (confidence=${obs.confidence} < 0.8 threshold)")
    println("  After:  ${result.value.twin.state} (should remain Lying)")
    println("  Discards: ${result.discards}")
    println()
}

// ── Scenario 6: Hysteresis Block ────────────────────────────────────────────

private fun runScenario6_HysteresisBlock(interpreter: SceneInterpreter) {
    println("── Scenario 6: Hysteresis Block (Lying → BedEdge too fast) ──")

    val bed = BedId("bed-6")
    val night = NightId("night-1")
    val monitor = MonitorId("m6")
    val jose = ResidentId("jose")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")
    val t1 = Instant.parse("2024-01-01T03:00:01Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = jose,
        state = PersonState.Lying,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-006",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.BED_EDGE,
        confidence = 0.95,
        observedAt = t1,
    )

    val result = interpreter.interpret(twin, obs, t1)

    println("  Before: ${twin.state} (since ${t0})")
    println("  Observation: ${obs.kind} at ${t1} (only 1s later)")
    println("  Hysteresis for Lying→BedEdge: 1500ms")
    println("  After:  ${result.value.twin.state} (should remain Lying)")
    println("  Discards: ${result.discards}")
    println()
}

// ── Scenario 7: SittingInBed → Standing (permissive) ───────────────────────

private fun runScenario7_SittingInBedToStanding(interpreter: SceneInterpreter) {
    println("── Scenario 7: SittingInBed → Standing (skip BedEdge) ──")

    val bed = BedId("bed-7")
    val night = NightId("night-1")
    val monitor = MonitorId("m7")
    val maria = ResidentId("maria")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")
    val t1 = Instant.parse("2024-01-01T03:00:02Z")

    val twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = maria,
        state = PersonState.SittingInBed,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    val obs = Observation(
        sourceEventId = "obs-007",
        monitor = monitor,
        bed = bed,
        kind = ObservationKind.STANDING,
        confidence = 0.9,
        observedAt = t1,
    )

    val result = interpreter.interpret(twin, obs, t1)

    println("  Before: ${twin.state}")
    println("  Observation: ${obs.kind} (skip BedEdge → Standing)")
    println("  After:  ${result.value.twin.state}")
    println("  Facts: ${result.value.facts}")
    println()
}

// ── Scenario 8: Full Outdoor Sequence ───────────────────────────────────────

private fun runScenario8_OutdoorSequence(interpreter: SceneInterpreter) {
    println("── Scenario 8: Lying → BedEdge → Standing → InRoom → InHallway → Outdoor ──")

    val bed = BedId("bed-8")
    val night = NightId("night-1")
    val monitor = MonitorId("m8")
    val maria = ResidentId("maria")
    val t0 = Instant.parse("2024-01-01T03:00:00Z")

    var twin = DigitalTwin(
        bed = bed,
        night = night,
        occupant = maria,
        state = PersonState.Lying,
        stateSince = t0,
        signal = SignalHealth(monitor, t0.minusSeconds(10), false),
    )

    data class Step(val kind: ObservationKind, val confidence: Double, val offsetSeconds: Long)

    val steps = listOf(
        Step(ObservationKind.BED_EDGE, 0.92, 2),
        Step(ObservationKind.STANDING, 0.9, 4),
        Step(ObservationKind.IN_ROOM, 0.88, 6),
        Step(ObservationKind.IN_HALLWAY, 0.85, 8),
        Step(ObservationKind.OUTDOOR, 0.82, 10),
    )

    steps.forEachIndexed { index, step ->
        val t = t0.plusSeconds(step.offsetSeconds)
        val obs = Observation(
            sourceEventId = "obs-step-${index + 1}",
            monitor = monitor,
            bed = bed,
            kind = step.kind,
            confidence = step.confidence,
            observedAt = t,
        )

        val result = interpreter.interpret(twin, obs, t)
        println("  Step ${index + 1}: ${twin.state} → ${step.kind} (${step.confidence})")
        println("    Result: ${result.value.twin.state}, Facts: ${result.value.facts.size}")

        twin = result.value.twin
    }

    println("  Final state: ${twin.state}")
    println()
}
