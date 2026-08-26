package com.manahive.pipeline.batch

import com.manahive.batchio.EventParser
import com.manahive.batchio.HarborCommandWriter
import com.manahive.contracts.common.Channel
import com.manahive.batchio.PipelineTraceWriter
import com.manahive.batchio.SceneEventWriter
import com.manahive.batchio.SentinelSignalWriter
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import com.manahive.recorder.*
import com.manahive.scene.calibration.sceneCalibration
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.sweeper.createSweeper
import com.manahive.sentinel.*
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Pipeline Batch: reads events.dat, processes through all engines, writes pipeline.out
 *
 * Input: events.dat (same format as scene-batch)
 * Output: pipeline.out (full chain results)
 *
 * Uses platform:batch-io for parsing and writing.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: pipeline-batch <events.dat> <output-dir>")
        println()
        println("Example:")
        println("  pipeline-batch events.dat output/")
        println()
        println("Input format (events.dat):")
        println("  t=0s OBS IN_BED confidence=0.95")
        println("  t=1h15m OBS SITTING_IN_BED confidence=0.92")
        println("  t=1h32m OBS IN_BED confidence=0.95")
        println()
        println("Output files:")
        println("  pipeline.out - Full chain results")
        println("  scene.out - Scene events")
        println("  sentinel.out - Sentinel signals")
        println("  harbor.out - Harbor commands")
        println("  recorder.out - Recorder commands")
        return
    }

    val eventsFile = File(args[0])
    val outputDir = File(args[1])
    outputDir.mkdirs()

    if (!eventsFile.exists()) {
        println("Error: ${eventsFile.absolutePath} not found")
        return
    }

    println("╔════════════════════════════════════════════════════════════╗")
    println("║         pipeline-batch — Full Chain Processing            ║")
    println("╚════════════════════════════════════════════════════════════╝")
    println()
    println("  Input:    ${eventsFile.absolutePath}")
    println("  Output:   ${outputDir.absolutePath}")
    println()

    // Parse events using batch-io
    val events = EventParser.parse(eventsFile)
    println("  Events:   ${events.size} observations")
    println()

    // Configure engines
    val bedId = BedId("bed-default")
    val residentId = ResidentId("resident-default")
    val nightId = NightId("night-default")
    val monitorId = MonitorId("CAMERA_MAIN")
    val startTime = Instant.parse("2024-01-15T22:00:00Z")

    // Scene calibration
    val sceneCal = sceneCalibration {
        heartbeatTimeout = Duration.ofSeconds(90)
        dwell {
            SITTING_IN_BED warning Duration.ofMinutes(30) exceeded Duration.ofMinutes(45)
            IN_BATHROOM warning Duration.ofMinutes(20) exceeded Duration.ofMinutes(30)
        }
        comeBack {
            LYING warning Duration.ofMinutes(15) exceeded Duration.ofMinutes(30)
        }
        confidence {
            StateKind.SITTING_IN_BED min 0.85
            StateKind.STANDING min 0.85
        }
    }

    // Sentinel calibration
    val sentinelCal = sentinelCalibration {
        resident("resident-default")
        rule("r-sitting") {
            trigger = StateKind.SITTING_IN_BED
            severity = Severity.WARNING
            closureCondition = ClosureCondition.STAFF_OR_SAFE
            reversible = true
            requiresConfirmation = false
        }
        rule("r-dwell-bathroom") {
            trigger = StateKind.IN_BATHROOM
            severity = Severity.WARNING
            reversible = true
            requiresConfirmation = false
        }
    }

    // Harbor calibration
    val harborCal = harborCalibration {
        resident("resident-default")
        budget {
            warning(5)
            info(3)
        }
        notice {
            channels = setOf(Channel.CONSOLE)
            escalationTimeout = Duration.ofMinutes(30)
        }
        alert {
            channels = setOf(Channel.PUSH, Channel.TABLET)
            escalationTimeout = Duration.ofMinutes(5)
        }
        incident {
            channels = setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE)
            escalationTimeout = Duration.ZERO
        }
    }

    // Recorder calibration
    val recorderCal = recordingCalibration {
        resident("resident-default")
        rule("r-fall-recording") {
            trigger { transition(from = PersonState.Lying, to = PersonState.Standing) }
            recordingWindow { before = Duration.ofMinutes(2); after = Duration.ofMinutes(5) }
            quality = Quality.HD
            monitors = listOf(monitorId)
        }
    }

    // Process through Scene Engine
    println("  Stage 1: Scene Engine")
    val interpreter = createInterpreter(sceneCal)
    val sweeper = createSweeper()
    val dwellCatalog = sceneCal.toDwellCatalog()

    // Initialize digital twin with proper parameters
    var digitalTwin = com.manahive.scene.core.DigitalTwin(
        bed = bedId,
        night = nightId,
        occupant = residentId,
        state = PersonState.Lying,
        stateSince = startTime,
        scene = SceneState(),
        sceneSince = startTime,
        signal = com.manahive.scene.core.SignalHealth(
            monitor = monitorId,
            lastHeartbeat = startTime,
            lost = false,
        ),
        calibration = sceneCal,
    )

    var dwellMarks = DwellMarks.NONE
    var currentTime = startTime
    val sceneEvents = mutableListOf<SceneEvent>()

    for (event in events) {
        val eventTime = startTime.plusMillis(event.offset.toMillis())

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
            monitor = monitorId,
            bed = bedId,
            kind = event.kind,
            confidence = event.confidence,
            observedAt = eventTime,
        )

        val result = interpreter.interpret(digitalTwin, observation, eventTime)
        digitalTwin = result.value.twin
        sceneEvents.addAll(result.value.facts)
        currentTime = eventTime
    }

    println("    → ${sceneEvents.size} SceneEvents")
    sceneEvents.forEach { println("      · ${it::class.simpleName}") }
    println()

    // Process through Sentinel
    println("  Stage 2: Sentinel")
    val evaluator = createSentinelEvaluator(sentinelCal)
    var ledger = EpisodeLedger.empty(residentId)
    val sentinelSignals = mutableListOf<SentinelSignal>()

    for (event in sceneEvents) {
        val result = evaluator.evaluate(event, ledger, event.at)
        ledger = result.value.episodes
        sentinelSignals.addAll(result.value.signals)
    }

    println("    → ${sentinelSignals.size} SentinelSignals")
    sentinelSignals.forEach { println("      · ${it::class.simpleName}") }
    println()

    // Process through Harbor
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
    harborCommands.forEach { println("      · ${it::class.simpleName}") }
    println()

    // Process through Recorder
    println("  Stage 4: Recorder")
    val recorderEngine = createRecorderEngine(recorderCal)
    var recordingLedger = RecordingLedger(emptyMap())
    val recorderCommands = mutableListOf<RecordingCommand>()
    val evidence = mutableListOf<EvidenceRecord>()

    for (event in sceneEvents) {
        val trigger = SceneEventTrigger(event, bedId, event.at)
        val result = recorderEngine.evaluate(trigger, recordingLedger, event.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
        evidence.addAll(result.value.evidenceRecords)
    }

    for (signal in sentinelSignals) {
        val trigger = SentinelSignalTrigger(signal, bedId, signal.at)
        val result = recorderEngine.evaluate(trigger, recordingLedger, signal.at)
        recordingLedger = result.value.ledger
        recorderCommands.addAll(result.value.commands)
        evidence.addAll(result.value.evidenceRecords)
    }

    println("    → ${recorderCommands.size} RecordingCommands")
    recorderCommands.forEach { println("      · ${it::class.simpleName}") }
    println("    → ${evidence.size} EvidenceRecords")
    println()

    // Write output files using batch-io
    SceneEventWriter.write(File(outputDir, "scene.out"), sceneEvents, startTime)
    SentinelSignalWriter.write(File(outputDir, "sentinel.out"), sentinelSignals, startTime)
    HarborCommandWriter.write(File(outputDir, "harbor.out"), harborCommands)
    PipelineTraceWriter.write(File(outputDir, "pipeline.out"), startTime, events, sceneEvents, sentinelSignals, harborCommands)

    println("  Output files:")
    println("    scene.out:    ${File(outputDir, "scene.out").absolutePath}")
    println("    sentinel.out: ${File(outputDir, "sentinel.out").absolutePath}")
    println("    harbor.out:   ${File(outputDir, "harbor.out").absolutePath}")
    println("    pipeline.out: ${File(outputDir, "pipeline.out").absolutePath}")
    println()
    println("  ✅ Pipeline batch completed")
}
