package com.manahive.recorder.batch

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.recorder.*
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.ResidentId
import java.io.File
import java.time.Clock
import java.time.Instant

/**
 * Recorder batch CLI.
 *
 * Processes Scene facts and Sentinel signals to produce recording commands.
 *
 * Usage:
 *   recorder-batch run --scene scene.out --sentinel sentinel.out --output recording.out
 *   recorder-batch run --signals input.jsonl --output output.jsonl
 */
class RecorderBatchApp : CliktCommand(
    name = "recorder-batch",
    help = "Process scene facts and sentinel signals to produce recording commands"
) {
    override fun run() = Unit
}

class RunCommand : CliktCommand(
    name = "run",
    help = "Process scene facts and sentinel signals to produce recording commands"
) {
    // JSONL mode (legacy)
    private val signals by option("--signals", help = "SentinelSignal JSONL file").file()
    private val output by option("--output", help = "Output JSONL file").file()

    // Data.out mode
    private val sentinel by option("--sentinel", help = "Sentinel.out file").file()
    private val recorder by option("--recorder", help = "Recorder.out file").file()

    // Sprint 7: Scene + Sentinel mode
    private val scene by option("--scene", help = "Scene.out file").file()

    // Common options
    private val resident by option("--resident", help = "Resident ID").default("default")

    override fun run() {
        runWithClock(Clock.systemUTC())
    }

    internal fun runWithClock(clock: Clock) {
        when {
            scene != null && sentinel != null -> runSceneSentinelMode(clock)
            signals != null && output != null -> runJsonlMode(clock)
            sentinel != null && recorder != null -> runDataOutMode(clock)
            else -> throw IllegalArgumentException(
                "Either: --scene <scene.out> --sentinel <sentinel.out>\n" +
                "    or: --signals <input.jsonl> --output <output.jsonl>\n" +
                "    or: --sentinel <sentinel.out> --recorder <recorder.out>"
            )
        }
    }

    private fun runSceneSentinelMode(clock: Clock) {
        val sceneEvents = SignalParser.parseSceneEvents(scene!!)
        val sentinelSignals = SignalParser.parseSentinelSignals(sentinel!!)
        val outputFile = File(sentinel!!.parentFile, "recorder.out")
        processEvents(sceneEvents, sentinelSignals, outputFile, clock)
    }

    private fun runJsonlMode(clock: Clock) {
        val inputSignals = SignalParser.parseWithLineNumbers(signals!!)
        processSignals(inputSignals.map { it.signal }, output!!, clock)
    }

    private fun runDataOutMode(clock: Clock) {
        val inputSignals = SignalParser.parse(sentinel!!)
        processSignals(inputSignals, recorder!!, clock)
    }

    private fun processEvents(
        sceneEvents: List<SceneEvent>,
        sentinelSignals: List<SentinelSignal>,
        outputFile: File,
        clock: Clock,
    ) {
        val calibration = defaultCalibration(ResidentId(resident))
        val engine = createRecorderEngine(calibration)
        var ledger = RecordingLedger()
        val commands = mutableListOf<RecordingCommand>()
        val evidenceRecords = mutableListOf<EvidenceRecord>()

        println("Processing ${sceneEvents.size} scene facts + ${sentinelSignals.size} sentinel signals...")

        // Process scene facts first
        for (fact in sceneEvents) {
            val now = Instant.now(clock)
            val trigger = SceneEventTrigger(
                fact = fact,
                bed = fact.bed,
                at = fact.at,
            )
            val result = engine.evaluate(trigger, ledger, now)
            ledger = result.value.ledger
            commands.addAll(result.value.commands)
            evidenceRecords.addAll(result.value.evidenceRecords)

            println("  t=${fact.at}  ${fact::class.simpleName} → ${result.value.commands.size} command(s), ${result.value.evidenceRecords.size} evidence(s)")
        }

        // Process sentinel signals
        for (signal in sentinelSignals) {
            val now = Instant.now(clock)
            val trigger = SentinelSignalTrigger(
                signal = signal,
                bed = signal.bed,
                at = signal.at,
            )
            val result = engine.evaluate(trigger, ledger, now)
            ledger = result.value.ledger
            commands.addAll(result.value.commands)
            evidenceRecords.addAll(result.value.evidenceRecords)

            println("  t=${signal.at}  ${signal::class.simpleName} → ${result.value.commands.size} command(s), ${result.value.evidenceRecords.size} evidence(s)")
        }

        commands.writeTo(outputFile)
        println("Wrote ${commands.size} recording commands to ${outputFile.absolutePath}")

        if (evidenceRecords.isNotEmpty()) {
            val evidenceFile = File(outputFile.parentFile, "evidence-${outputFile.name}")
            evidenceRecords.writeTo(evidenceFile)
            println("Wrote ${evidenceRecords.size} evidence records to ${evidenceFile.absolutePath}")
        }
    }

    private fun processSignals(
        signals: List<SentinelSignal>,
        outputFile: File,
        clock: Clock,
    ) {
        val calibration = defaultCalibration(ResidentId(resident))
        val engine = createRecorderEngine(calibration)
        var ledger = RecordingLedger()
        val commands = mutableListOf<RecordingCommand>()
        val evidenceRecords = mutableListOf<EvidenceRecord>()

        println("Processing ${signals.size} signals...")

        for (signal in signals) {
            val now = Instant.now(clock)
            val trigger = SentinelSignalTrigger(
                signal = signal,
                bed = signal.bed,
                at = signal.at,
            )
            val result = engine.evaluate(trigger, ledger, now)
            ledger = result.value.ledger
            commands.addAll(result.value.commands)
            evidenceRecords.addAll(result.value.evidenceRecords)

            println("  t=${signal.at}  ${signal::class.simpleName} → ${result.value.commands.size} recording command(s), ${result.value.evidenceRecords.size} evidence record(s)")
        }

        commands.writeTo(outputFile)
        println("Wrote ${commands.size} recording commands to ${outputFile.absolutePath}")

        if (evidenceRecords.isNotEmpty()) {
            val evidenceFile = File(outputFile.parentFile, "evidence-${outputFile.name}")
            evidenceRecords.writeTo(evidenceFile)
            println("Wrote ${evidenceRecords.size} evidence records to ${evidenceFile.absolutePath}")
        }
    }

    private fun defaultCalibration(residentId: ResidentId): RecordingCalibration =
        recordingCalibration {
            resident(residentId)

            rule("r-fall-recording") {
                trigger {
                    transition(from = PersonState.Lying, to = PersonState.Standing)
                }
                recordingWindow {
                    before = 2.minutes
                    after = 5.minutes
                }
                quality = Quality.HD
                monitors = listOf(MonitorId("CAMERA_MAIN"))
            }

            rule("r-bathroom-recording") {
                trigger {
                    dwellExceeded(state = PersonState.InBathroom)
                }
                recordingWindow {
                    before = 5.minutes
                    after = 10.minutes
                }
                quality = Quality.HD
                monitors = listOf(MonitorId("CAMERA_MAIN"))
            }

            rule("r-incident-recording") {
                trigger {
                    episodeOpened(severity = Severity.CRITICAL)
                }
                recordingWindow {
                    before = 10.minutes
                    after = 15.minutes
                }
                quality = Quality.FULL
                monitors = listOf(MonitorId("CAMERA_MAIN"), MonitorId("CAMERA_CORRIDOR"))
            }

            evidenceRule("e-fall-evidence") {
                trigger {
                    episodeOpened(severity = Severity.WARNING)
                }
                evidenceType = EvidenceType.INCIDENT
            }
        }
}

class VerifyCommand : CliktCommand(
    name = "verify",
    help = "Verify signals produce expected recording events"
) {
    private val signals by option("--signals", help = "SentinelSignal JSONL file").file()
    private val expected by option("--expected", help = "Expected events JSONL file").file()

    override fun run() {
        println("Verify not yet implemented")
    }
}

fun main(args: Array<String>) = RecorderBatchApp()
    .subcommands(RunCommand(), VerifyCommand())
    .main(args)
