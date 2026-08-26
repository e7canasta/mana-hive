package com.manahive.recorder.bdd

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.kind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.*
import com.manahive.recorder.*
import java.time.Instant

// ── Context ──────────────────────────────────────────────────────────────────

data class RecorderContext(
    val bed: BedId,
    val resident: ResidentId,
    val calibration: RecordingCalibration,
    val monitors: List<MonitorId> = listOf(MonitorId("CAMERA_MAIN")),
)

// ── Input wrappers ───────────────────────────────────────────────────────────

data class TriggerAt(val trigger: RecordingTrigger, val at: Instant)

// ── Assertion results ────────────────────────────────────────────────────────

data class RecorderScenarioCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

data class RecorderScenarioResult(
    val name: String,
    val commands: List<RecordingCommand>,
    val evidenceRecords: List<EvidenceRecord>,
    val ledger: RecordingLedger,
    val checks: List<RecorderScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Commands: ${commands.size}")
        println("  Evidence: ${evidenceRecords.size}")
        println("  Ledger:   ${ledger.activeCount()} active recordings")
        println()
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("  $status ${check.description}")
            if (check.error != null) println("     ${check.error}")
        }
        println()
    }
}

// ── Scenario Builder ─────────────────────────────────────────────────────────

class RecorderScenarioBuilder(private val ctx: RecorderContext) {
    private val triggers = mutableListOf<TriggerAt>()
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var lastLedger = RecordingLedger(emptyMap())
    private var lastCommands = listOf<RecordingCommand>()
    private var lastEvidence = listOf<EvidenceRecord>()

    // ── Trigger builders ─────────────────────────────────────────────────────

    fun signal(trigger: RecordingTrigger, at: Instant) {
        triggers.add(TriggerAt(trigger, at))
    }

    // ── Scene fact triggers ───────────────────────────────────────────────────

    fun transitionDetected(
        from: PersonState, to: PersonState,
        bed: BedId = ctx.bed,
        night: NightId = NightId("test-night"),
        at: Instant,
    ) {
        val fact = SceneEvent.TransitionDetected(
            bed = bed, night = night, at = at,
            from = from, to = to,
        )
        signal(SceneEventTrigger(fact = fact, bed = bed, at = at), at)
    }

    fun dwellExceeded(
        state: PersonState,
        bed: BedId = ctx.bed,
        night: NightId = NightId("test-night"),
        at: Instant,
        threshold: java.time.Duration = java.time.Duration.ofMinutes(60),
        since: Instant = at.minusSeconds(threshold.seconds),
    ) {
        val fact = SceneEvent.DwellExceeded(
            bed = bed, night = night, at = at,
            state = state, threshold = threshold, since = since,
        )
        signal(SceneEventTrigger(fact = fact, bed = bed, at = at), at)
    }

    // ── Sentinel signal triggers ──────────────────────────────────────────────

    fun episodeOpened(
        episodeId: String,
        ruleId: String = "r-fall",
        severity: Severity = Severity.WARNING,
        bed: BedId = ctx.bed,
        resident: ResidentId = ctx.resident,
        night: NightId = NightId("test-night"),
        at: Instant,
        reversible: Boolean = true,
        requiresNvr: Boolean = false,
    ) {
        val signal = SentinelSignal.EpisodeOpened(
            bed = bed, resident = resident, at = at,
            rulesFingerprint = "test",
            episode = EpisodeId(episodeId),
            rule = RuleId(ruleId),
            trigger = PersonState.Lying.kind,
            severity = severity,
            reversible = reversible,
            requiresNvr = requiresNvr,
            confirmationWindow = null,
        )
        signal(SentinelSignalTrigger(signal, bed = bed, at = at), at)
    }

    fun episodeClosed(
        episodeId: String,
        bed: BedId = ctx.bed,
        resident: ResidentId = ctx.resident,
        night: NightId = NightId("test-night"),
        at: Instant,
        cause: com.manahive.contracts.sentinel.ClosureCause = com.manahive.contracts.sentinel.ClosureCause.AUTO_RECOVERY,
    ) {
        val signal = SentinelSignal.EpisodeClosed(
            bed = bed, resident = resident, at = at,
            rulesFingerprint = "test",
            episode = EpisodeId(episodeId),
            cause = cause,
            gapDuration = null,
        )
        signal(SentinelSignalTrigger(signal, bed = bed, at = at), at)
    }

    // ── Assertion builders ────────────────────────────────────────────────────

    fun thenExpectRecordingStarted(block: (RecordingStarted) -> Unit = {}) {
        assertions.add("RecordingStarted" to {
            val result = lastCommands.filterIsInstance<RecordingStarted>()
            check(result.isNotEmpty()) {
                "Expected RecordingStarted but none found.\n     Actual: ${lastCommands.map { it::class.simpleName }}"
            }
            block(result.first())
        })
    }

    fun thenExpectRecordingStopped(block: (RecordingStopped) -> Unit = {}) {
        assertions.add("RecordingStopped" to {
            val result = lastCommands.filterIsInstance<RecordingStopped>()
            check(result.isNotEmpty()) {
                "Expected RecordingStopped but none found.\n     Actual: ${lastCommands.map { it::class.simpleName }}"
            }
            block(result.first())
        })
    }

    fun thenExpectClipCreated(block: (ClipCreated) -> Unit = {}) {
        assertions.add("ClipCreated" to {
            val result = lastCommands.filterIsInstance<ClipCreated>()
            check(result.isNotEmpty()) {
                "Expected ClipCreated but none found.\n     Actual: ${lastCommands.map { it::class.simpleName }}"
            }
            block(result.first())
        })
    }

    fun thenExpectRecordingStartedCount(count: Int) {
        assertions.add("$count RecordingStarted" to {
            val actual = lastCommands.filterIsInstance<RecordingStarted>().size
            check(actual == count) { "Expected $count RecordingStarted, got $actual" }
        })
    }

    fun thenExpectEvidenceStarted(block: (EvidenceRecordingStarted) -> Unit = {}) {
        assertions.add("EvidenceRecordingStarted" to {
            val result = lastEvidence.filterIsInstance<EvidenceRecordingStarted>()
            check(result.isNotEmpty()) {
                "Expected EvidenceRecordingStarted but none found.\n     Actual: ${lastEvidence.map { it::class.simpleName }}"
            }
            block(result.first())
        })
    }

    fun thenExpectEvidenceClip(block: (EvidenceClipCreated) -> Unit = {}) {
        assertions.add("EvidenceClipCreated" to {
            val result = lastEvidence.filterIsInstance<EvidenceClipCreated>()
            check(result.isNotEmpty()) {
                "Expected EvidenceClipCreated but none found.\n     Actual: ${lastEvidence.map { it::class.simpleName }}"
            }
            block(result.first())
        })
    }

    fun thenExpectNoCommands() {
        assertions.add("no commands" to {
            check(lastCommands.isEmpty()) {
                "Expected no commands but got ${lastCommands.size}: ${lastCommands.map { it::class.simpleName }}"
            }
        })
    }

    fun thenExpectNoEvidence() {
        assertions.add("no evidence" to {
            check(lastEvidence.isEmpty()) {
                "Expected no evidence but got ${lastEvidence.size}: ${lastEvidence.map { it::class.simpleName }}"
            }
        })
    }

    fun thenExpectLedgerActiveCount(count: Int) {
        assertions.add("ledger active count $count" to {
            check(lastLedger.activeCount() == count) { "Expected active count $count, got ${lastLedger.activeCount()}" }
        })
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    fun run(): RecorderScenarioResult {
        val engine = createRecorderEngine(ctx.calibration)
        var ledger = RecordingLedger(emptyMap())
        val allCommands = mutableListOf<RecordingCommand>()
        val allEvidence = mutableListOf<EvidenceRecord>()

        for (triggerAt in triggers) {
            val result = engine.evaluate(triggerAt.trigger, ledger, triggerAt.at)
            ledger = result.value.ledger
            allCommands.addAll(result.value.commands)
            allEvidence.addAll(result.value.evidenceRecords)
        }

        lastLedger = ledger
        lastCommands = allCommands
        lastEvidence = allEvidence

        val checks = assertions.map { (desc, check) ->
            try {
                check()
                RecorderScenarioCheck(desc, true)
            } catch (e: AssertionError) {
                RecorderScenarioCheck(desc, false, e.message)
            }
        }

        return RecorderScenarioResult(
            name = "",
            commands = allCommands,
            evidenceRecords = allEvidence,
            ledger = ledger,
            checks = checks,
        )
    }
}

// ── Top-level DSL ────────────────────────────────────────────────────────────

fun RecorderContext.scenario(
    name: String,
    block: RecorderScenarioBuilder.() -> Unit,
): RecorderScenarioResult {
    val builder = RecorderScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}

// ── Business Language Assertions (Vernon Ubiquitous Language) ────────────────

/**
 * "Se inició la grabación"
 */
fun RecorderScenarioBuilder.grabacionIniciada() {
    thenExpectRecordingStarted()
}

/**
 * "La grabación es en calidad HD"
 */
fun RecorderScenarioBuilder.grabacionEnCalidadHD() {
    thenExpectRecordingStarted { cmd ->
        check(cmd.quality == Quality.HD) { "Expected HD quality" }
    }
}

/**
 * "La grabación es en calidad FULL (máxima)"
 */
fun RecorderScenarioBuilder.grabacionEnCalidadFull() {
    thenExpectRecordingStarted { cmd ->
        check(cmd.quality == Quality.FULL) { "Expected FULL quality" }
    }
}

/**
 * "La grabación es independiente (no ligada a episodio)"
 */
fun RecorderScenarioBuilder.grabacionIndependiente() {
    thenExpectRecordingStarted { cmd ->
        check(cmd.context is RecordingContext.Standalone) {
            "Expected standalone context"
        }
    }
}

/**
 * "La grabación está ligada a un episodio"
 */
fun RecorderScenarioBuilder.grabacionLigadaAEpisodio() {
    thenExpectRecordingStarted { cmd ->
        check(cmd.context is RecordingContext.TiedToEpisode) {
            "Expected TiedToEpisode context"
        }
    }
}

/**
 * "Se creó registro de evidencia"
 */
fun RecorderScenarioBuilder.evidenciaRegistrada() {
    thenExpectEvidenceStarted()
}

/**
 * "No se generaron comandos de grabación"
 */
fun RecorderScenarioBuilder.sinGrabacion() {
    thenExpectNoCommands()
}

/**
 * "No se generaron registros de evidencia"
 */
fun RecorderScenarioBuilder.sinEvidencia() {
    thenExpectNoEvidence()
}

/**
 * "Exactamente N grabaciones activas"
 */
fun RecorderScenarioBuilder.grabacionesActivas(count: Int) {
    thenExpectRecordingStartedCount(count)
}
