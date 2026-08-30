package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.batchio.EventParser
import com.manahive.batchio.HarborCommandWriter
import com.manahive.batchio.PipelineTraceWriter
import com.manahive.batchio.SceneEventWriter
import com.manahive.batchio.SentinelSignalWriter
import com.manahive.recorder.batch.RecordingEventWriter
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.recorder.*
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.scene.calibration.sceneCalibration
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.sweeper.createSweeper
import com.manahive.sentinel.*
import java.io.File
import java.time.Instant

private val mapper = jacksonObjectMapper()

fun main(args: Array<String>) {
    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Pipeline Completa: JSON + events.dat → .out")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Cargar perfil desde JSON ───────────────────────────────────
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
    if (cpStream == null) {
        println("  ❌ No se encontró profiles/jose.json en el classpath")
        return
    }
    val dto = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    println("  Perfil: ${dto.profileId} (v${dto.version})")

    // ── 2. Arranque en frío: generar calibración ─────────────────────
    val runtime = NightWatchRuntime()
    val census = Census()
    census.register(BED_4, JOSE, NIGHT, MONITOR)
    val calibrator = ProfileCalibrator(runtime, census)
    calibrator.accept(dto)

    val calibrations = runtime.get(JOSE)!!.calibrations
    val sceneCal = calibrations.scene
    val sentinelCal = calibrations.sentinel
    val harborCal = calibrations.harbor
    val recorderCal = calibrations.recorder

    println("  Calibración generada desde JSON")
    println()

    // ── 3. Cargar events.dat ─────────────────────────────────────────
    val eventsFile = File("jose-301-sitting-bed/events.dat")
    if (!eventsFile.exists()) {
        println("  ❌ No se encontró jose-301-sitting-bed/events.dat")
        return
    }
    val events = EventParser.parse(eventsFile)
    println("  Events: ${events.size} observaciones")
    println()

    // ── 4. Crear directorio de salida ────────────────────────────────
    val outputDir = File("output-jose-e1")
    outputDir.mkdirs()

    // ── 5. Scene Engine ──────────────────────────────────────────────
    println("  Stage 1: Scene Engine")
    val interpreter = createInterpreter(sceneCal)
    val sweeper = createSweeper()
    val dwellCatalog = sceneCal.toDwellCatalog()

    var digitalTwin = DigitalTwin(
        bed = BED_4,
        night = NIGHT,
        occupant = JOSE,
        state = PersonState.Lying,
        stateSince = START,
        scene = SceneState(),
        sceneSince = START,
        signal = SignalHealth(
            monitor = MONITOR,
            lastHeartbeat = START,
            lost = false,
        ),
        calibration = sceneCal,
    )

    var dwellMarks = DwellMarks.NONE
    val sceneEvents = mutableListOf<SceneEvent>()

    for (event in events) {
        val eventTime = START.plusMillis(event.offset.toMillis())

        // Run sweeper
        val sweepResult = sweeper.sweep(
            twins = listOf(digitalTwin),
            now = eventTime,
            thresholds = dwellCatalog,
            marks = dwellMarks,
        )
        sceneEvents.addAll(sweepResult.value.facts)
        dwellMarks = sweepResult.value.marks

        // Run interpreter
        val observation = Observation(
            sourceEventId = "batch-${event.offset}",
            monitor = MONITOR,
            bed = BED_4,
            kind = event.kind,
            confidence = event.confidence,
            observedAt = eventTime,
        )

        val result = interpreter.interpret(digitalTwin, observation, eventTime)
        digitalTwin = result.value.twin
        sceneEvents.addAll(result.value.facts)
    }

    println("    → ${sceneEvents.size} SceneEvents")
    println()

    // ── 6. Sentinel Engine ───────────────────────────────────────────
    println("  Stage 2: Sentinel")
    val evaluator = createSentinelEvaluator(sentinelCal)
    var ledger = EpisodeLedger.empty(JOSE)
    val sentinelSignals = mutableListOf<SentinelSignal>()

    for (event in sceneEvents) {
        val result = evaluator.evaluate(event, ledger, event.at)
        ledger = result.value.episodes
        sentinelSignals.addAll(result.value.signals)
    }

    println("    → ${sentinelSignals.size} SentinelSignals")
    println()

    // ── 7. Harbor Engine ─────────────────────────────────────────────
    println("  Stage 3: Harbor")
    val harborEngine = createHarborEngine(harborCal)
    var harborState = HarborState(budget = harborCal.budget)
    val harborCommands = mutableListOf<NoticeCommand>()

    for (signal in sentinelSignals) {
        val result = harborEngine.evaluate(signal, harborState, signal.at)
        harborState = result.value.state
        harborCommands.addAll(result.value.commands)
    }

    println("    → ${harborCommands.size} NoticeCommands")
    println()

    // ── 8. Recorder Engine ───────────────────────────────────────────
    println("  Stage 4: Recorder")
    val recorderEngine = createRecorderEngine(recorderCal)
    var recordingLedger = RecordingLedger(emptyMap())
    val recorderCommands = mutableListOf<RecordingCommand>()
    val evidence = mutableListOf<EvidenceRecord>()

    for (event in sceneEvents) {
        val trigger = SceneEventTrigger(event, BED_4, event.at)
        val result = recorderEngine.evaluate(trigger, recordingLedger, event.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
        evidence.addAll(result.value.evidenceRecords)
    }

    for (signal in sentinelSignals) {
        val trigger = SentinelSignalTrigger(signal, BED_4, signal.at)
        val result = recorderEngine.evaluate(trigger, recordingLedger, signal.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
        evidence.addAll(result.value.evidenceRecords)
    }

    println("    → ${recorderCommands.size} RecordingCommands")
    println("    → ${evidence.size} EvidenceRecords")
    println()

    // ── 9. Escribir archivos de salida ───────────────────────────────
    SceneEventWriter.write(File(outputDir, "scene.out"), sceneEvents, START)
    SentinelSignalWriter.write(File(outputDir, "sentinel.out"), sentinelSignals, START)
    HarborCommandWriter.write(File(outputDir, "harbor.out"), harborCommands)
    RecordingEventWriter.write(File(outputDir, "recorder.out"), recorderCommands, START)
    PipelineTraceWriter.write(
        File(outputDir, "pipeline.out"),
        START,
        events,
        sceneEvents,
        sentinelSignals,
        harborCommands,
    )

    println("  Output files:")
    println("    scene.out:    ${File(outputDir, "scene.out").absolutePath}")
    println("    sentinel.out: ${File(outputDir, "sentinel.out").absolutePath}")
    println("    harbor.out:   ${File(outputDir, "harbor.out").absolutePath}")
    println("    recorder.out: ${File(outputDir, "recorder.out").absolutePath}")
    println("    pipeline.out: ${File(outputDir, "pipeline.out").absolutePath}")
    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ PIPELINE COMPLETA: JSON + events.dat → .out")
    println("═══════════════════════════════════════════════════════════════")
}
