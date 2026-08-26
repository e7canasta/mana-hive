package com.manahive.sentinel.bdd

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.SuppressionCause
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.sentinel.EpisodeLedger
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.createSentinelEvaluator
import java.time.Instant

// ── Context ──────────────────────────────────────────────────────────────────

data class SentinelContext(
    val bed: BedId,
    val resident: ResidentId,
    val night: NightId,
    val calibration: SentinelCalibration,
    val start: Instant,
)

// ── Scenario Builder ─────────────────────────────────────────────────────────

class SentinelScenarioBuilder(private val ctx: SentinelContext) {
    private val facts = mutableListOf<FactAt>()
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var lastSignals: List<SentinelSignal> = emptyList()
    private var lastEpisodes: EpisodeLedger = EpisodeLedger.empty(ctx.resident)

    fun fact(fact: SceneEvent, at: Instant) {
        facts.add(FactAt(fact, at))
    }

    fun fact(kind: StateKind, from: PersonState, to: PersonState, at: Instant) {
        facts.add(FactAt(
            SceneEvent.TransitionDetected(
                bed = ctx.bed,
                night = ctx.night,
                at = at,
                from = from,
                to = to,
            ),
            at,
        ))
    }

    fun factStaffPresent(staffId: String, at: Instant) {
        facts.add(FactAt(
            SceneEvent.StaffPresenceDetected(
                bed = ctx.bed,
                night = ctx.night,
                at = at,
                staff = com.manahive.kernel.StaffId(staffId),
            ),
            at,
        ))
    }

    fun factDwellExceeded(state: PersonState, threshold: java.time.Duration, at: Instant) {
        facts.add(FactAt(
            SceneEvent.DwellExceeded(
                bed = ctx.bed,
                night = ctx.night,
                at = at,
                state = state,
                threshold = threshold,
                since = at.minus(threshold),
            ),
            at,
        ))
    }

    // ── Assertions on signals ──────────────────────────────────────────────

    fun thenExpectEpisodeOpened(block: (SentinelSignal.EpisodeOpened) -> Unit = {}) {
        assertions.add("EpisodeOpened" to {
            val signal = lastSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().firstOrNull()
            val actual = lastSignals.joinToString { it::class.simpleName ?: "?" }
            check(signal != null) { "Expected EpisodeOpened but not found.\n     Actual: $actual" }
            block(signal)
        })
    }

    fun thenExpectEpisodeOpenedCount(count: Int) {
        assertions.add("$count EpisodeOpened" to {
            val actual = lastSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().size
            check(actual == count) { "Expected $count EpisodeOpened, got $actual" }
        })
    }

    fun thenExpectAutoRecovery(block: (SentinelSignal.AutoRecovery) -> Unit = {}) {
        assertions.add("AutoRecovery" to {
            val signal = lastSignals.filterIsInstance<SentinelSignal.AutoRecovery>().firstOrNull()
            val actual = lastSignals.joinToString { it::class.simpleName ?: "?" }
            check(signal != null) { "Expected AutoRecovery but not found.\n     Actual: $actual" }
            block(signal)
        })
    }

    fun thenExpectEpisodeClosed(block: (SentinelSignal.EpisodeClosed) -> Unit = {}) {
        assertions.add("EpisodeClosed" to {
            val signal = lastSignals.filterIsInstance<SentinelSignal.EpisodeClosed>().firstOrNull()
            val actual = lastSignals.joinToString { it::class.simpleName ?: "?" }
            check(signal != null) { "Expected EpisodeClosed but not found.\n     Actual: $actual" }
            block(signal)
        })
    }

    fun thenExpectEpisodeClosedCount(count: Int) {
        assertions.add("$count EpisodeClosed" to {
            val actual = lastSignals.filterIsInstance<SentinelSignal.EpisodeClosed>().size
            check(actual == count) { "Expected $count EpisodeClosed, got $actual" }
        })
    }

    fun thenExpectSuppressed(block: (SentinelSignal.SuppressedWithRecord) -> Unit = {}) {
        assertions.add("SuppressedWithRecord" to {
            val signal = lastSignals.filterIsInstance<SentinelSignal.SuppressedWithRecord>().firstOrNull()
            val actual = lastSignals.joinToString { it::class.simpleName ?: "?" }
            check(signal != null) { "Expected SuppressedWithRecord but not found.\n     Actual: $actual" }
            block(signal)
        })
    }

    fun thenExpectUmbrellaEvent(block: (SentinelSignal.UmbrellaEvent) -> Unit = {}) {
        assertions.add("UmbrellaEvent" to {
            val signal = lastSignals.filterIsInstance<SentinelSignal.UmbrellaEvent>().firstOrNull()
            val actual = lastSignals.joinToString { it::class.simpleName ?: "?" }
            check(signal != null) { "Expected UmbrellaEvent but not found.\n     Actual: $actual" }
            block(signal)
        })
    }

    fun thenExpectNoSignals() {
        assertions.add("no signals" to {
            check(lastSignals.isEmpty()) {
                "Expected no signals but got: ${lastSignals.joinToString { it::class.simpleName ?: "?" }}"
            }
        })
    }

    fun thenExpectSignalCount(count: Int) {
        assertions.add("$count signals" to {
            check(lastSignals.size == count) { "Expected $count signals, got ${lastSignals.size}" }
        })
    }

    fun thenExpectSeverity(severity: Severity) {
        assertions.add("severity = $severity" to {
            val opened = lastSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().firstOrNull()
            check(opened?.severity == severity) {
                "Expected severity $severity but got ${opened?.severity}"
            }
        })
    }

    // ── Business Language Assertions ────────────────────────────────────────

    fun episodioAbierto() {
        assertions.add("episodio abierto" to {
            val opened = lastSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().firstOrNull()
            check(opened != null) {
                "Se esperaba episodio abierto pero no se detectó"
            }
        })
    }

    fun episodioCerrado() {
        assertions.add("episodio cerrado" to {
            val closed = lastSignals.filterIsInstance<SentinelSignal.EpisodeClosed>().firstOrNull()
            check(closed != null) {
                "Se esperaba episodio cerrado pero no se detectó"
            }
        })
    }

    fun episodioCerradoPor(cause: ClosureCause) {
        assertions.add("episodio cerrado por $cause" to {
            val closed = lastSignals.filterIsInstance<SentinelSignal.EpisodeClosed>().firstOrNull()
            check(closed != null) {
                "Se esperaba episodio cerrado por $cause pero no se detectó cierre"
            }
            check(closed.cause == cause) {
                "Se esperaba cierre por $cause pero fue por ${closed.cause}"
            }
        })
    }

    fun episodioConSeveridad(severity: Severity) {
        assertions.add("episodio con severidad $severity" to {
            val opened = lastSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().firstOrNull()
            check(opened != null) {
                "Se esperaba episodio abierto con severidad $severity pero no se detectó"
            }
            check(opened.severity == severity) {
                "Se esperaba severidad $severity pero fue ${opened.severity}"
            }
        })
    }

    fun staffPresente() {
        assertions.add("staff presente" to {
            val episode = lastEpisodes.openForBed(ctx.bed)
            check(episode != null && episode.staffPresent) {
                "Se esperaba staff presente pero el episodio no tiene staff marcado"
            }
        })
    }

    fun staffAusente() {
        assertions.add("staff ausente" to {
            val episode = lastEpisodes.openForBed(ctx.bed)
            if (episode != null) {
                check(!episode.staffPresent) {
                    "Se esperaba staff ausente pero staff sigue presente"
                }
            }
        })
    }

    fun sinEpisodiosAbiertos() {
        assertions.add("sin episodios abiertos" to {
            check(lastEpisodes.open.isEmpty()) {
                "Se esperaba sin episodios abiertos pero hay ${lastEpisodes.open.size}"
            }
        })
    }

    fun cantidadDeEpisodios(count: Int) {
        assertions.add("$count episodios abiertos" to {
            check(lastEpisodes.open.size == count) {
                "Se esperaban $count episodios abiertos pero hay ${lastEpisodes.open.size}"
            }
        })
    }

    // ── Run ────────────────────────────────────────────────────────────────

    fun run(): SentinelScenarioResult {
        val evaluator = createSentinelEvaluator(ctx.calibration)
        var episodes = lastEpisodes
        val allSignals = mutableListOf<SentinelSignal>()

        for (factAt in facts) {
            val result = evaluator.evaluate(factAt.fact, episodes, factAt.at)
            episodes = result.value.episodes
            allSignals.addAll(result.value.signals)
        }

        lastSignals = allSignals
        lastEpisodes = episodes

        val results = assertions.map { (desc, check) ->
            try {
                check()
                ScenarioCheck(desc, passed = true)
            } catch (e: AssertionError) {
                ScenarioCheck(desc, passed = false, error = e.message)
            }
        }

        return SentinelScenarioResult(
            name = "",
            signals = allSignals,
            checks = results,
        )
    }

    private data class FactAt(val fact: SceneEvent, val at: Instant)
}

// ── Result ───────────────────────────────────────────────────────────────────

data class ScenarioCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

data class SentinelScenarioResult(
    val name: String,
    val signals: List<SentinelSignal>,
    val checks: List<ScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Signals: ${signals.size}")
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

fun SentinelContext.scenario(
    name: String,
    block: SentinelScenarioBuilder.() -> Unit,
): SentinelScenarioResult {
    val builder = SentinelScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}
