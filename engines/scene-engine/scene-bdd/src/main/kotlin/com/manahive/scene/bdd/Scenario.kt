package com.manahive.scene.bdd

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneEvent.TransitionDetected
import com.manahive.scene.SceneEngine
import com.manahive.scene.ObservedAt
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import java.time.Instant

// ── Context: blueprint-specific data ────────────────────────────────────────

data class BddContext(
    val bed: BedId,
    val monitor: MonitorId,
    val start: Instant,
    val initialTwin: () -> DigitalTwin,
    val timeParser: (String) -> Instant,
)

// ── Episode: reusable observation sequence ───────────────────────────────────

class EpisodeBuilder(private val ctx: BddContext) {
    private val observations = mutableListOf<ObservedAt>()

    infix fun ObservationKind.at(offset: String): ObservedBuilder =
        ObservedBuilder(this, ctx.timeParser(offset))

    inner class ObservedBuilder(private val kind: ObservationKind, private val at: Instant) {
        infix fun withConfidence(confidence: Double): ObservedAt {
            val obs = ObservedAt(
                Observation(
                    sourceEventId = "episode-${at.toEpochMilli()}",
                    monitor = ctx.monitor,
                    bed = ctx.bed,
                    kind = kind,
                    confidence = confidence,
                    observedAt = at,
                ),
                at,
            )
            observations.add(obs)
            return obs
        }
    }

    fun build(): Episode = Episode(observations.toList())
}

data class Episode(val observations: List<ObservedAt>)

fun BddContext.episode(block: EpisodeBuilder.() -> Unit): Episode {
    val builder = EpisodeBuilder(this)
    builder.block()
    return builder.build()
}

// ── Scenario DSL ────────────────────────────────────────────────────────────

class ScenarioBuilder(private val ctx: BddContext) {
    private var calibration: SceneCalibration? = null
    private val observations = mutableListOf<ObservedAt>()
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var lastFacts: List<SceneEvent> = emptyList()

    fun given(block: ScenarioBuilder.() -> Unit) {
        block()
    }

    fun calibration(cal: SceneCalibration) {
        this.calibration = cal
    }

    fun includes(vararg episodes: Episode) {
        for (ep in episodes) {
            observations.addAll(ep.observations)
        }
    }

    fun whenObserving(block: EpisodeBuilder.() -> Unit) {
        val builder = EpisodeBuilder(ctx)
        builder.block()
        observations.addAll(builder.build().observations)
    }

    // ── Assertions ────────────────────────────────────────────────────────

    fun thenExpect(description: String = "", block: (List<SceneEvent>) -> Unit) {
        assertions.add(description to { block(lastFacts) })
    }

    fun thenExpectTransition(transition: Transition) {
        assertions.add("${transition.from} → ${transition.to}" to {
            lastFacts.shouldHaveTransition(transition.from, transition.to)
        })
    }

    fun thenExpectTransitions(count: Int) {
        assertions.add("$count transiciones" to {
            lastFacts.shouldHaveExactlyTransitions(count)
        })
    }

    fun thenExpectComeBackExceeded(baseline: PersonState) {
        assertions.add("ComeBackExceeded($baseline)" to {
            lastFacts.shouldHaveComeBackExceeded(baseline)
        })
    }

    fun thenExpectNoComeBackExceeded() {
        assertions.add("sin ComeBackExceeded" to {
            lastFacts.shouldNotHaveComeBackExceeded()
        })
    }

    fun thenExpectDwellExceeded(state: PersonState) {
        assertions.add("DwellExceeded($state)" to {
            lastFacts.shouldHaveDwellExceeded(state)
        })
    }

    fun thenExpectNoDwellExceeded() {
        assertions.add("sin DwellExceeded" to {
            lastFacts.shouldNotHaveDwellExceeded()
        })
    }

    fun thenExpectSignalLost() {
        assertions.add("SignalLost" to {
            lastFacts.shouldHaveSignalLost()
        })
    }

    fun thenExpectFacts(count: Int) {
        assertions.add("$count facts" to {
            lastFacts.shouldHaveExactlyFacts(count)
        })
    }

    // ── Run ───────────────────────────────────────────────────────────────

    fun run(sweepIntervalSeconds: Long = 60): ScenarioResult {
        val cal = calibration ?: error("No calibration defined")
        val engine = SceneEngine.create(cal)
        val result = engine.processWithSweep(observations, sweepIntervalSeconds, ctx.initialTwin())
        lastFacts = result.facts

        val results = assertions.map { (desc, check) ->
            try {
                check()
                ScenarioCheck(desc, passed = true)
            } catch (e: AssertionError) {
                ScenarioCheck(desc, passed = false, error = e.message)
            }
        }

        return ScenarioResult(name = "", facts = result.facts, checks = results)
    }

    // ── Observation Builder ───────────────────────────────────────────────

    infix fun ObservationKind.at(offset: String): ObservedBuilder =
        ObservedBuilder(this, ctx.timeParser(offset))

    inner class ObservedBuilder(private val kind: ObservationKind, private val at: Instant) {
        infix fun withConfidence(confidence: Double): ObservedAt =
            ObservedAt(
                Observation(
                    sourceEventId = "scenario-${at.toEpochMilli()}",
                    monitor = ctx.monitor,
                    bed = ctx.bed,
                    kind = kind,
                    confidence = confidence,
                    observedAt = at,
                ),
                at,
            )
    }

    // ── Assertion Helpers ─────────────────────────────────────────────────

    private fun List<SceneEvent>.shouldHaveTransition(from: PersonState, to: PersonState) {
        val transitions = filterIsInstance<TransitionDetected>()
        val found = transitions.any { it.from == from && it.to == to }
        check(found) {
            val actual = transitions.joinToString("\n     ") { "${it.from} → ${it.to}" }
            "Expected transition $from → $to but not found.\n     Actual transitions:\n     $actual"
        }
    }

    private fun List<SceneEvent>.shouldHaveExactlyTransitions(count: Int) {
        val actual = filterIsInstance<TransitionDetected>().size
        check(actual == count) {
            val all = filterIsInstance<TransitionDetected>().joinToString("\n     ") { "${it.from} → ${it.to}" }
            "Expected $count transitions, got $actual.\n     Actual:\n     $all"
        }
    }

    private fun List<SceneEvent>.shouldHaveComeBackExceeded(baseline: PersonState) {
        val found = filterIsInstance<SceneEvent.ComeBackExceeded>().any { it.baseline == baseline }
        check(found) {
            val comeBacks = filterIsInstance<SceneEvent.ComeBackExceeded>()
            if (comeBacks.isEmpty()) {
                "Expected ComeBackExceeded($baseline) but none found."
            } else {
                "Expected ComeBackExceeded($baseline) but found: ${comeBacks.joinToString { it.baseline.toString() }}"
            }
        }
    }

    private fun List<SceneEvent>.shouldNotHaveComeBackExceeded() {
        val found = filterIsInstance<SceneEvent.ComeBackExceeded>()
        check(found.isEmpty()) {
            "Expected no ComeBackExceeded but found: ${found.joinToString { it.baseline.toString() }}"
        }
    }

    private fun List<SceneEvent>.shouldHaveDwellExceeded(state: PersonState) {
        val found = filterIsInstance<SceneEvent.DwellExceeded>().any { it.state == state }
        check(found) { "Expected DwellExceeded($state) but not found" }
    }

    private fun List<SceneEvent>.shouldNotHaveDwellExceeded() {
        val found = filterIsInstance<SceneEvent.DwellExceeded>()
        check(found.isEmpty()) {
            "Expected no DwellExceeded but found: ${found.joinToString { it.state.toString() }}"
        }
    }

    private fun List<SceneEvent>.shouldHaveSignalLost() {
        val found = filterIsInstance<SceneEvent.SignalLost>()
        check(found.isNotEmpty()) { "Expected SignalLost but none found" }
    }

    private fun List<SceneEvent>.shouldHaveExactlyFacts(count: Int) {
        check(size == count) {
            "Expected $count facts, got $size:\n${joinToString("\n") { "  ${it::class.simpleName}" }}"
        }
    }
}

// ── Typed transition pair ────────────────────────────────────────────────────

data class Transition(
    val from: PersonState,
    val to: PersonState,
)

infix fun PersonState.to(to: PersonState): Transition = Transition(this, to)

// ── Result types ────────────────────────────────────────────────────────────

data class ScenarioCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

data class ScenarioResult(
    val name: String,
    val facts: List<SceneEvent>,
    val checks: List<ScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Facts: ${facts.size}")
        println()
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("  $status ${check.description}")
            if (check.error != null) println("     ${check.error}")
        }
        println()
    }
}

// ── Top-level DSL ───────────────────────────────────────────────────────────

fun BddContext.scenario(
    name: String,
    block: ScenarioBuilder.() -> Unit,
): ScenarioResult {
    val builder = ScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}

// ── Config Comparison DSL ───────────────────────────────────────────────────

class ComparisonBuilder(private val ctx: BddContext) {
    private var observations: List<ObservedAt> = emptyList()
    private val configs = mutableListOf<ConfigExpectation>()

    fun observations(block: ObservationCollector.() -> Unit) {
        val collector = ObservationCollector()
        collector.block()
        observations = collector.observations
    }

    fun config(name: String, block: ConfigBuilder.() -> Unit): ConfigExpectationBuilder {
        val configBuilder = ConfigBuilder()
        configBuilder.block()
        return ConfigExpectationBuilder(name, configBuilder.calibration!!, observations, ctx).also {
            configs.add(it.build())
        }
    }

    fun run(): ComparisonResult {
        val results = configs.map { config ->
            val engine = SceneEngine.create(config.calibration)
            val result = engine.processWithSweep(observations, 60, ctx.initialTwin())
            val checks = config.assertions.map { (desc, check) ->
                try {
                    check(result.facts)
                    ScenarioCheck(desc, passed = true)
                } catch (e: AssertionError) {
                    ScenarioCheck(desc, passed = false, error = e.message)
                }
            }
            ConfigResult(config.name, result.facts, checks)
        }
        return ComparisonResult(name = "", results)
    }
}

class ObservationCollector {
    val observations = mutableListOf<ObservedAt>()
}

class ConfigBuilder {
    var calibration: SceneCalibration? = null
}

class ConfigExpectationBuilder(
    private val name: String,
    private val calibration: SceneCalibration,
    private val observations: List<ObservedAt>,
    private val ctx: BddContext,
) {
    private val assertions = mutableListOf<Pair<String, (List<SceneEvent>) -> Unit>>()

    fun transitions(count: Int) {
        assertions.add("$count transiciones" to { facts ->
            val actual = facts.filterIsInstance<TransitionDetected>().size
            check(actual == count) { "Expected $count transitions, got $actual" }
        })
    }

    fun comeBackExceeded(count: Int) {
        assertions.add("$count ComeBackExceeded" to { facts ->
            val actual = facts.filterIsInstance<SceneEvent.ComeBackExceeded>().size
            check(actual == count) { "Expected $count ComeBackExceeded, got $actual" }
        })
    }

    fun noComeBackExceeded() {
        assertions.add("sin ComeBackExceeded" to { facts ->
            val actual = facts.filterIsInstance<SceneEvent.ComeBackExceeded>().size
            check(actual == 0) { "Expected 0 ComeBackExceeded, got $actual" }
        })
    }

    fun build(): ConfigExpectation = ConfigExpectation(name, calibration, assertions)
}

data class ConfigExpectation(
    val name: String,
    val calibration: SceneCalibration,
    val assertions: List<Pair<String, (List<SceneEvent>) -> Unit>>,
)

data class ConfigResult(
    val name: String,
    val facts: List<SceneEvent>,
    val checks: List<ScenarioCheck>,
)

data class ComparisonResult(
    val name: String,
    val configResults: List<ConfigResult>,
) {
    fun report() {
        println("═══ Comparison: $name ═══")
        println()
        for (config in configResults) {
            println("  Config: ${config.name}")
            println("  Facts: ${config.facts.size}")
            config.checks.forEach { check ->
                val status = if (check.passed) "✅" else "❌"
                println("  $status ${check.description}")
                if (check.error != null) println("     ${check.error}")
            }
            println()
        }
    }
}

fun BddContext.compare(
    name: String,
    block: ComparisonBuilder.() -> Unit,
): ComparisonResult {
    val builder = ComparisonBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}
