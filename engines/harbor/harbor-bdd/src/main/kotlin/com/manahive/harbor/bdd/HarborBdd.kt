package com.manahive.harbor.bdd

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.BedId
import com.manahive.kernel.ResidentId
import java.time.Instant

// ── Context ──────────────────────────────────────────────────────────────────

data class HarborContext(
    val bed: BedId,
    val resident: ResidentId,
    val calibration: HarborCalibration,
)

// ── Scenario Builder ─────────────────────────────────────────────────────────

class HarborScenarioBuilder(private val ctx: HarborContext) {
    private val signals = mutableListOf<SignalAt>()
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var lastState = HarborState(budget = ctx.calibration.budget)
    private var lastCommands = listOf<NoticeCommand>()

    fun signal(signal: SentinelSignal, at: Instant) {
        signals.add(SignalAt(signal, at))
    }

    // ── Assertions on commands ──────────────────────────────────────────────

    fun thenExpectDispatch(block: (NoticeCommand.Dispatch) -> Unit = {}) {
        assertions.add("Dispatch" to {
            val cmd = lastCommands.filterIsInstance<NoticeCommand.Dispatch>().firstOrNull()
            val actual = lastCommands.joinToString { it::class.simpleName ?: "?" }
            check(cmd != null) { "Expected Dispatch but not found.\n     Actual: $actual" }
            block(cmd)
        })
    }

    fun thenExpectResolve(block: (NoticeCommand.Resolve) -> Unit = {}) {
        assertions.add("Resolve" to {
            val cmd = lastCommands.filterIsInstance<NoticeCommand.Resolve>().firstOrNull()
            val actual = lastCommands.joinToString { it::class.simpleName ?: "?" }
            check(cmd != null) { "Expected Resolve but not found.\n     Actual: $actual" }
            block(cmd)
        })
    }

    fun thenExpectNoCommands() {
        assertions.add("no commands" to {
            check(lastCommands.isEmpty()) {
                "Expected no commands but got: ${lastCommands.joinToString { it::class.simpleName ?: "?" }}"
            }
        })
    }

    fun thenExpectCommandCount(count: Int) {
        assertions.add("$count commands" to {
            check(lastCommands.size == count) { "Expected $count commands, got ${lastCommands.size}" }
        })
    }

    fun thenExpectNoticeCreated(episodeId: com.manahive.kernel.EpisodeId) {
        assertions.add("notice created for $episodeId" to {
            val notice = lastState.registry.get(episodeId)
            check(notice != null) { "Expected notice for episode $episodeId but not found" }
        })
    }

    fun thenExpectNoticeNotCreated(episodeId: com.manahive.kernel.EpisodeId) {
        assertions.add("no notice for $episodeId" to {
            val notice = lastState.registry.get(episodeId)
            check(notice == null) { "Expected no notice for episode $episodeId but found one" }
        })
    }

    fun thenExpectFatigueExceeded(severity: Severity) {
        assertions.add("budget exceeded for $severity" to {
            check(!lastState.budget.canDeliver(severity)) {
                "Expected budget exceeded for $severity but canDeliver is true"
            }
        })
    }

    fun thenExpectFatigueNotExceeded(severity: Severity) {
        assertions.add("budget not exceeded for $severity" to {
            check(lastState.budget.canDeliver(severity)) {
                "Expected budget not exceeded for $severity but canDeliver is false"
            }
        })
    }

    // ── Run ────────────────────────────────────────────────────────────────

    fun run(): HarborScenarioResult {
        val engine = createHarborEngine(ctx.calibration)
        var state = lastState
        val allCommands = mutableListOf<NoticeCommand>()

        for (signalAt in signals) {
            val result = engine.evaluate(signalAt.signal, state, signalAt.at)
            state = result.value.state
            allCommands.addAll(result.value.commands)
        }

        lastState = state
        lastCommands = allCommands

        val results = assertions.map { (desc, check) ->
            try {
                check()
                ScenarioCheck(desc, passed = true)
            } catch (e: AssertionError) {
                ScenarioCheck(desc, passed = false, error = e.message)
            }
        }

        return HarborScenarioResult(
            name = "",
            commands = allCommands,
            state = state,
            checks = results,
        )
    }

    private data class SignalAt(val signal: SentinelSignal, val at: Instant)
}

// ── Result ───────────────────────────────────────────────────────────────────

data class ScenarioCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

data class HarborScenarioResult(
    val name: String,
    val commands: List<NoticeCommand>,
    val state: HarborState,
    val checks: List<ScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Commands: ${commands.size}")
        println("  Fatigue: ${state.budget}")
        println()
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("  $status ${check.description}")
            if (check.error != null) println("     ${check.error}")
        }
        println()
    }
}

// ── Top-level DSL ────────────────────────────────────────────────────────────

fun HarborContext.scenario(
    name: String,
    block: HarborScenarioBuilder.() -> Unit,
): HarborScenarioResult {
    val builder = HarborScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}

// ── Business Language Assertions (Vernon Ubiquitous Language) ────────────────
//
// These assertions read like domain specifications, not technical tests.
// Fowler: "Intention-Revealing Interfaces" — the name communicates intent.
// Vernon: "Ubiquitous Language" — the assertion speaks the domain.

/**
 * "Se creó una notificación para el episodio"
 */
fun HarborScenarioBuilder.seCreoNotificacion(episodeId: com.manahive.kernel.EpisodeId) {
    thenExpectNoticeCreated(episodeId)
}

/**
 * "No se creó notificación para el episodio"
 */
fun HarborScenarioBuilder.noSeCreoNotificacion(episodeId: com.manahive.kernel.EpisodeId) {
    thenExpectNoticeNotCreated(episodeId)
}

/**
 * "Se despachó la notificación al staff"
 */
fun HarborScenarioBuilder.seDespachoAlStaff() {
    thenExpectDispatch()
}

/**
 * "Se resolvió el episodio (recuperación automática)"
 */
fun HarborScenarioBuilder.seResolvioEpisodio() {
    thenExpectResolve()
}

/**
 * "El budget de notificaciones se agotó para esta severidad"
 */
fun HarborScenarioBuilder.budgetAgotado(severity: Severity) {
    thenExpectFatigueExceeded(severity)
}

/**
 * "El budget de notificaciones aún tiene capacidad"
 */
fun HarborScenarioBuilder.budgetDisponible(severity: Severity) {
    thenExpectFatigueNotExceeded(severity)
}

/**
 * "No se generaron comandos (no hubo acción)"
 */
fun HarborScenarioBuilder.sinAcciones() {
    thenExpectNoCommands()
}
