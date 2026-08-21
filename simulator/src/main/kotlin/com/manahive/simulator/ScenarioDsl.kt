package com.manahive.simulator

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The night-scenario DSL. One scenario is three things at once: an acceptance
 * test in CI (virtual clock, in-memory transport), an input to the golden
 * replay, and the tool with which clinical staff SEE a rule before enabling
 * it. That is why it is main source, not test util.
 */
@DslMarker
annotation class ScenarioDsl

fun scenario(name: String, block: ScenarioBuilder.() -> Unit): Scenario =
    ScenarioBuilder(name).apply(block).build()

data class Scenario(
    val name: String,
    val startAt: Instant,
    val steps: List<Step>,
)

sealed interface Step {
    val at: Instant

    data class Emit(override val at: Instant, val observation: Observation) : Step
    data class SensorGoesSilent(override val at: Instant, val bed: BedId) : Step

    /** Expectations are the acceptance criteria, in the clinician's language. */
    data class Expect(override val at: Instant, val expectation: Expectation) : Step
}

sealed interface Expectation {
    data class AlertRaised(val rule: String, val severity: String) : Expectation
    data class AlertEscalated(val toStep: Int) : Expectation
    data class AlertResolvedByPresence(val maxSecondsToStaff: Long) : Expectation
    data object NoAlert : Expectation
    data class SuppressedWithCause(val cause: String) : Expectation
}

@ScenarioDsl
class ScenarioBuilder(private val name: String) {
    private var start: Instant = Instant.parse("2026-08-20T22:00:00Z")
    private var cursor: Instant = start
    private val steps = mutableListOf<Step>()
    private var bed: BedId = BedId("12A")
    private var monitor: MonitorId = MonitorId("cell-12A")

    fun startsAt(iso: String) {
        start = Instant.parse(iso); cursor = start
    }

    fun bed(id: String) { bed = BedId(id); monitor = MonitorId("cell-$id") }

    fun at(iso: String, block: MomentBuilder.() -> Unit) {
        cursor = Instant.parse(iso)
        MomentBuilder(cursor).apply(block)
    }

    fun after(duration: Duration, block: MomentBuilder.() -> Unit) {
        cursor = cursor.plus(duration)
        MomentBuilder(cursor).apply(block)
    }

    fun build():  Scenario = Scenario(name, start, steps.sortedBy { it.at })

    @ScenarioDsl
    inner class MomentBuilder(private val moment: Instant) {
        fun observes(kind: ObservationKind, confidence: Double = 0.9) {
            this@ScenarioBuilder.steps += Step.Emit(
                moment,
                Observation(
                    sourceEventId = UUID.randomUUID().toString(),
                    monitor = this@ScenarioBuilder.monitor,
                    bed = this@ScenarioBuilder.bed,
                    kind = kind,
                    confidence = confidence,
                    observedAt = moment,
                ),
            )
        }

        fun sensorGoesSilent() {
            this@ScenarioBuilder.steps += Step.SensorGoesSilent(moment, this@ScenarioBuilder.bed)
        }

        fun expect(expectation: Expectation) {
            this@ScenarioBuilder.steps += Step.Expect(moment, expectation)
        }
    }
}
