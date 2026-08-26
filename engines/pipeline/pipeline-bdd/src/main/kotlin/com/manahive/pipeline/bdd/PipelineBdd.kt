package com.manahive.pipeline.bdd

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import com.manahive.recorder.*
import com.manahive.scene.ObservedAt
import com.manahive.scene.SceneEngine
import com.manahive.scene.SceneResult
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.sentinel.*
import java.time.Instant

// ── Context: bundles all engine calibrations ─────────────────────────────────

data class PipelineContext(
    val bed: BedId,
    val resident: ResidentId,
    val night: NightId,
    val monitor: MonitorId,
    val sceneCalibration: SceneCalibration,
    val sentinelCalibration: SentinelCalibration,
    val harborCalibration: HarborCalibration,
    val recorderCalibration: RecordingCalibration,
    val start: Instant,
    /** Seconds between sweep ticks. Smaller = more precise dwell detection, more events. */
    val sweepIntervalSeconds: Long = 60,
)

// ── Stage results ────────────────────────────────────────────────────────────

data class StageResult<T>(
    val name: String,
    val items: List<T>,
    val description: String,
)

// ── Pipeline result ──────────────────────────────────────────────────────────

data class PipelineResult(
    val name: String,
    val sceneEvents: List<SceneEvent>,
    val sentinelSignals: List<SentinelSignal>,
    val harborCommands: List<NoticeCommand>,
    val recorderCommands: List<RecordingCommand>,
    val evidenceRecords: List<EvidenceRecord>,
    val finalEpisodeLedger: EpisodeLedger,
    val finalHarborState: HarborState,
    val finalRecordingLedger: RecordingLedger,
    val checks: List<PipelineCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Pipeline: $name ──")
        println()
        println("  Stage 1 — Scene (Percepción)")
        println("    Observations → ${sceneEvents.size} SceneEvents")
        sceneEvents.forEach { println("      · ${it::class.simpleName}") }
        println()
        println("  Stage 2 — Sentinel (Juicio Clínico)")
        println("    SceneEvents → ${sentinelSignals.size} SentinelSignals")
        sentinelSignals.forEach { println("      · ${it::class.simpleName}") }
        println()
        println("  Stage 3 — Harbor (Entrega)")
        println("    SentinelSignals → ${harborCommands.size} NoticeCommands")
        harborCommands.forEach { println("      · ${it::class.simpleName}") }
        println()
        println("  Stage 4 — Recorder (Grabación)")
        println("    SceneEvents+Signals → ${recorderCommands.size} RecordingCommands")
        recorderCommands.forEach { println("      · ${it::class.simpleName}") }
        println("    Evidence: ${evidenceRecords.size} records")
        println()
        println("  Checks:")
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("    $status ${check.description}")
            if (check.error != null) println("       ${check.error}")
        }
        println()
    }

    fun saveDat(path: String) {
        val sb = StringBuilder()
        sb.appendLine("# Pipeline: $name")
        sb.appendLine("# SceneEvents: ${sceneEvents.size}")
        sceneEvents.forEach { e ->
            sb.appendLine("SCENE_EVENT|${e::class.simpleName}|${e.at}")
        }
        sb.appendLine("# SentinelSignals: ${sentinelSignals.size}")
        sentinelSignals.forEach { s ->
            sb.appendLine("SIGNAL|${s::class.simpleName}|${s.at}")
        }
        sb.appendLine("# HarborCommands: ${harborCommands.size}")
        harborCommands.forEach { c ->
            sb.appendLine("HARBOR_CMD|${c::class.simpleName}")
        }
        sb.appendLine("# RecorderCommands: ${recorderCommands.size}")
        recorderCommands.forEach { c ->
            sb.appendLine("RECORDER_CMD|${c::class.simpleName}")
        }
        sb.appendLine("# EvidenceRecords: ${evidenceRecords.size}")
        evidenceRecords.forEach { e ->
            sb.appendLine("EVIDENCE|${e::class.simpleName}|${e.at}")
        }
        java.io.File(path).writeText(sb.toString())
    }

    fun saveOut(path: String) {
        val sb = StringBuilder()
        sb.appendLine("=== OUTPUT: $name ===")
        sb.appendLine()
        sb.appendLine("SCENE_EVENTS")
        sceneEvents.forEach { e -> sb.appendLine("  ${e::class.simpleName}") }
        sb.appendLine()
        sb.appendLine("SENTINEL_SIGNALS")
        sentinelSignals.forEach { s -> sb.appendLine("  ${s::class.simpleName}") }
        sb.appendLine()
        sb.appendLine("HARBOR_COMMANDS")
        harborCommands.forEach { c -> sb.appendLine("  ${c::class.simpleName}") }
        sb.appendLine()
        sb.appendLine("RECORDER_COMMANDS")
        recorderCommands.forEach { c -> sb.appendLine("  ${c::class.simpleName}") }
        sb.appendLine()
        sb.appendLine("EVIDENCE_RECORDS")
        evidenceRecords.forEach { e -> sb.appendLine("  ${e::class.simpleName}: ${e.at}") }
        sb.appendLine()
        sb.appendLine("CHECKS")
        checks.forEach { check ->
            val status = if (check.passed) "PASS" else "FAIL"
            sb.appendLine("  $status: ${check.description}")
        }
        java.io.File(path).writeText(sb.toString())
    }
}

data class PipelineCheck(
    val description: String,
    val passed: Boolean,
    val error: String? = null,
)

// ── Pipeline Scenario Builder ────────────────────────────────────────────────

class PipelineScenarioBuilder(private val ctx: PipelineContext) {
    private val observations = mutableListOf<ObservedAt>()
    private val checks = mutableListOf<Pair<String, () -> Unit>>()
    private var sceneResult: SceneResult? = null
    private var allSignals = listOf<SentinelSignal>()
    private var finalEpisodes = EpisodeLedger.empty(ctx.resident)
    private var allHarborCommands = listOf<NoticeCommand>()
    private var finalHarborState = HarborState()
    private var allRecorderCommands = listOf<RecordingCommand>()
    private var allEvidence = listOf<EvidenceRecord>()
    private var finalRecordingLedger = RecordingLedger(emptyMap())

    // ── Observation builders ─────────────────────────────────────────────────

    fun obs(kind: ObservationKind, offset: String, confidence: Double = 0.9) {
        val instant = ctx.start.plusSeconds(parseDuration(offset))
        observations.add(ObservedAt(
            Observation(
                sourceEventId = "obs-${instant.toEpochMilli()}",
                monitor = ctx.monitor,
                bed = ctx.bed,
                kind = kind,
                confidence = confidence,
                observedAt = instant,
            ),
            instant,
        ))
    }

    fun obsAt(instant: Instant, kind: ObservationKind, confidence: Double = 0.9) {
        observations.add(ObservedAt(
            Observation(
                sourceEventId = "obs-${instant.toEpochMilli()}",
                monitor = ctx.monitor,
                bed = ctx.bed,
                kind = kind,
                confidence = confidence,
                observedAt = instant,
            ),
            instant,
        ))
    }

    // ── Stage assertions ─────────────────────────────────────────────────────

    fun thenSceneEventCount(count: Int) {
        checks.add("$count SceneEvents" to {
            val actual = sceneResult?.facts?.size ?: 0
            check(actual == count) { "Expected $count SceneEvents, got $actual" }
        })
    }

    fun thenSceneEventPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("SceneEvent ${type.simpleName} present" to {
            val found = sceneResult?.facts?.any { type.isInstance(it) } ?: false
            check(found) { "Expected ${type.simpleName} in SceneEvents but not found" }
        })
    }

    fun thenSceneEventNotPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("SceneEvent ${type.simpleName} not present" to {
            val found = sceneResult?.facts?.any { type.isInstance(it) } ?: false
            check(!found) { "Expected no ${type.simpleName} in SceneEvents but found one" }
        })
    }

    fun thenSentinelSignalCount(count: Int) {
        checks.add("$count SentinelSignals" to {
            check(allSignals.size == count) { "Expected $count SentinelSignals, got ${allSignals.size}" }
        })
    }

    fun thenSignalPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("Signal ${type.simpleName} present" to {
            val found = allSignals.any { type.isInstance(it) }
            check(found) { "Expected ${type.simpleName} in signals but not found" }
        })
    }

    fun thenSignalNotPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("Signal ${type.simpleName} not present" to {
            val found = allSignals.any { type.isInstance(it) }
            check(!found) { "Expected no ${type.simpleName} in signals but found one" }
        })
    }

    // ── Business Language Assertions ────────────────────────────────────────

    fun thenSceneProdujoTransiciones(count: Int) {
        checks.add("Scene produjo $count transiciones" to {
            val actual = sceneResult?.facts?.filterIsInstance<com.manahive.contracts.scene.SceneEvent.TransitionDetected>()?.size ?: 0
            check(actual == count) { "Se esperaban $count transiciones pero Scene produjo $actual" }
        })
    }

    fun thenSceneDetectoStaff() {
        checks.add("Scene detectó presencia de staff" to {
            val found = sceneResult?.facts?.any { it is com.manahive.contracts.scene.SceneEvent.StaffPresenceDetected } ?: false
            check(found) { "Se esperaba detección de staff en Scene pero no se encontró" }
        })
    }

    fun thenSceneDetectoStaffSale() {
        checks.add("Scene detectó salida de staff" to {
            val found = sceneResult?.facts?.any { it is com.manahive.contracts.scene.SceneEvent.StaffLeftDetected } ?: false
            check(found) { "Se esperaba detección de salida de staff en Scene pero no se encontró" }
        })
    }

    fun thenSentinelAbrioEpisodio() {
        checks.add("Sentinel abrió episodio" to {
            val found = allSignals.any { it is com.manahive.contracts.sentinel.SentinelSignal.EpisodeOpened }
            check(found) { "Se esperaba que Sentinel abriera un episodio pero no lo hizo" }
        })
    }

    fun thenSentinelCerroEpisodio() {
        checks.add("Sentinel cerró episodio" to {
            val found = allSignals.any { it is com.manahive.contracts.sentinel.SentinelSignal.EpisodeClosed }
            check(found) { "Se esperaba que Sentinel cerrara un episodio pero no lo hizo" }
        })
    }

    fun thenSentinelAbrioYCerroEpisodio() {
        checks.add("Sentinel abrió y cerró episodio" to {
            val opened = allSignals.any { it is com.manahive.contracts.sentinel.SentinelSignal.EpisodeOpened }
            val closed = allSignals.any { it is com.manahive.contracts.sentinel.SentinelSignal.EpisodeClosed }
            check(opened && closed) {
                val missing = mutableListOf<String>()
                if (!opened) missing.add("apertura")
                if (!closed) missing.add("cierre")
                "Se esperaba apertura y cierre de episodio pero falta: ${missing.joinToString()}"
            }
        })
    }

    fun thenHarborNotificoYResolvio() {
        checks.add("Harbor notificó y resolvió" to {
            val dispatched = allHarborCommands.any { it is com.manahive.harbor.NoticeCommand.Dispatch }
            val resolved = allHarborCommands.any { it is com.manahive.harbor.NoticeCommand.Resolve }
            check(dispatched && resolved) {
                val missing = mutableListOf<String>()
                if (!dispatched) missing.add("dispatch")
                if (!resolved) missing.add("resolve")
                "Se esperaba dispatch y resolve en Harbor pero falta: ${missing.joinToString()}"
            }
        })
    }

    fun thenRecorderInicioGrabacion() {
        checks.add("Recorder inició grabación" to {
            val found = allRecorderCommands.any { it is com.manahive.recorder.RecordingStarted }
            check(found) { "Se esperaba que Recorder iniciara grabación pero no lo hizo" }
        })
    }

    fun thenHarborCommandCount(count: Int) {
        checks.add("$count HarborCommands" to {
            check(allHarborCommands.size == count) { "Expected $count HarborCommands, got ${allHarborCommands.size}" }
        })
    }

    fun thenHarborCommandPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("HarborCommand ${type.simpleName} present" to {
            val found = allHarborCommands.any { type.isInstance(it) }
            check(found) { "Expected ${type.simpleName} in harbor commands but not found" }
        })
    }

    fun thenRecorderCommandCount(count: Int) {
        checks.add("$count RecorderCommands" to {
            check(allRecorderCommands.size == count) { "Expected $count RecorderCommands, got ${allRecorderCommands.size}" }
        })
    }

    fun thenRecorderCommandPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("RecorderCommand ${type.simpleName} present" to {
            val found = allRecorderCommands.any { type.isInstance(it) }
            check(found) { "Expected ${type.simpleName} in recorder commands but not found" }
        })
    }

    fun thenRecorderCommandNotPresent(type: kotlin.reflect.KClass<*>) {
        checks.add("RecorderCommand ${type.simpleName} not present" to {
            val found = allRecorderCommands.any { type.isInstance(it) }
            check(!found) { "Expected no ${type.simpleName} in recorder commands but found one" }
        })
    }

    fun thenEvidenceCount(count: Int) {
        checks.add("$count EvidenceRecords" to {
            check(allEvidence.size == count) { "Expected $count EvidenceRecords, got ${allEvidence.size}" }
        })
    }

    fun thenEpisodeOpenCount(count: Int) {
        checks.add("$count episodes opened" to {
            val opened = allSignals.filterIsInstance<SentinelSignal.EpisodeOpened>().size
            check(opened == count) { "Expected $count episodes opened, got $opened" }
        })
    }

    fun thenEpisodeClosedCount(count: Int) {
        checks.add("$count episodes closed" to {
            val closed = allSignals.filterIsInstance<SentinelSignal.EpisodeClosed>().size
            check(closed == count) { "Expected $count episodes closed, got $closed" }
        })
    }

    fun thenActiveRecordingsCount(count: Int) {
        checks.add("$count active recordings" to {
            check(finalRecordingLedger.activeCount() == count) {
                "Expected $count active recordings, got ${finalRecordingLedger.activeCount()}"
            }
        })
    }

    fun thenActiveNoticesCount(count: Int) {
        checks.add("$count active notices" to {
            check(finalHarborState.registry.active.size == count) {
                "Expected $count active notices, got ${finalHarborState.registry.active.size}"
            }
        })
    }

    // ── Run the full pipeline ─────────────────────────────────────────────────

    fun run(): PipelineResult {
        // ── Stage 1: Scene Engine (with sweep for DwellExceeded) ──────────────
        val sceneEngine = SceneEngine.create(ctx.sceneCalibration)
        sceneResult = sceneEngine.processWithSweep(observations, ctx.sweepIntervalSeconds)

        // ── Stage 2: Sentinel (process each SceneEvent) ──────────────────────
        val sentinel = createSentinelEvaluator(ctx.sentinelCalibration)
        var episodes = EpisodeLedger.empty(ctx.resident)
        val signals = mutableListOf<SentinelSignal>()

        for (fact in sceneResult!!.facts) {
            val result = sentinel.evaluate(fact, episodes, fact.at)
            episodes = result.value.episodes
            signals.addAll(result.value.signals)
        }
        allSignals = signals
        finalEpisodes = episodes

        // ── Stage 3: Harbor (process each SentinelSignal) ────────────────────
        val harbor = createHarborEngine(ctx.harborCalibration)
        var harborState = HarborState(budget = ctx.harborCalibration.budget)
        val harborCommands = mutableListOf<NoticeCommand>()

        for (signal in signals) {
            val result = harbor.evaluate(signal, harborState, signal.at)
            harborState = result.value.state
            harborCommands.addAll(result.value.commands)
        }
        allHarborCommands = harborCommands
        finalHarborState = harborState

        // ── Stage 4: Recorder (process SceneEvents + SentinelSignals) ────────
        val recorder = createRecorderEngine(ctx.recorderCalibration)
        var recordingLedger = RecordingLedger(emptyMap())
        val recorderCommands = mutableListOf<RecordingCommand>()
        val evidence = mutableListOf<EvidenceRecord>()

        for (fact in sceneResult!!.facts) {
            val trigger = SceneEventTrigger(fact, ctx.bed, fact.at)
            val result = recorder.evaluate(trigger, recordingLedger, fact.at)
            recordingLedger = result.value.ledger
            recorderCommands.addAll(result.value.commands)
            evidence.addAll(result.value.evidenceRecords)
        }
        for (signal in signals) {
            val trigger = SentinelSignalTrigger(signal, ctx.bed, signal.at)
            val result = recorder.evaluate(trigger, recordingLedger, signal.at)
            recordingLedger = result.value.ledger
            recorderCommands.addAll(result.value.commands)
            evidence.addAll(result.value.evidenceRecords)
        }
        allRecorderCommands = recorderCommands
        allEvidence = evidence
        finalRecordingLedger = recordingLedger

        // ── Evaluate checks ──────────────────────────────────────────────────
        val evaluatedChecks = checks.map { (desc, check) ->
            try {
                check()
                PipelineCheck(desc, true)
            } catch (e: AssertionError) {
                PipelineCheck(desc, false, e.message)
            }
        }

        return PipelineResult(
            name = "",
            sceneEvents = sceneResult!!.facts,
            sentinelSignals = signals,
            harborCommands = harborCommands,
            recorderCommands = recorderCommands,
            evidenceRecords = evidence,
            finalEpisodeLedger = episodes,
            finalHarborState = harborState,
            finalRecordingLedger = recordingLedger,
            checks = evaluatedChecks,
        )
    }

    // ── Duration parser (simple) ─────────────────────────────────────────────

    private fun parseDuration(offset: String): Long {
        var totalSeconds = 0L
        val regex = Regex("(\\d+)(h|m|s|ms)")
        regex.findAll(offset).forEach { match ->
            val value = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            when (unit) {
                "h"  -> totalSeconds += value * 3600
                "m"  -> totalSeconds += value * 60
                "s"  -> totalSeconds += value
                "ms" -> totalSeconds += value / 1000
            }
        }
        return totalSeconds
    }
}

// ── Top-level DSL ────────────────────────────────────────────────────────────

fun PipelineContext.pipeline(
    name: String,
    outputDir: String? = null,
    block: PipelineScenarioBuilder.() -> Unit,
): PipelineResult {
    val builder = PipelineScenarioBuilder(this)
    builder.block()
    val result = builder.run().let { it.copy(name = name) }
    if (outputDir != null) {
        val dir = java.io.File(outputDir)
        dir.mkdirs()
        val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        result.saveDat("${dir.path}/${safeName}.dat")
        result.saveOut("${dir.path}/${safeName}.out")
        println("  📁 Saved: ${dir.path}/${safeName}.dat")
        println("  📁 Saved: ${dir.path}/${safeName}.out")
    }
    return result
}
