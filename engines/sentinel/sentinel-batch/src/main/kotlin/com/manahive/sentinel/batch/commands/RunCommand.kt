package com.manahive.sentinel.batch.commands

import com.manahive.sentinel.EpisodeLedger
import com.manahive.sentinel.batch.BatchContext
import com.manahive.sentinel.batch.BatchState
import com.manahive.sentinel.batch.BatchWriters
import com.manahive.sentinel.batch.SentinelBatchProcessor
import com.manahive.sentinel.batch.config.BatchConfigLoader
import com.manahive.sentinel.batch.formatOffset
import com.manahive.sentinel.batch.resolveFile
import com.manahive.sentinel.batch.events.SceneEventEventParser
import com.manahive.sentinel.batch.output.LogWriter
import com.manahive.sentinel.batch.output.SignalJsonlWriter
import com.manahive.sentinel.batch.output.SignalOutWriter
import com.manahive.sentinel.createSentinelEvaluator
import java.io.File

/**
 * Runs a batch simulation: reads events.dat, processes through SentinelEvaluator,
 * writes signals.out, signals.jsonl, and engine.log.
 *
 * Clock mode: event-time (default).
 *
 * Fowler: "Decompose Conditional" — the core logic lives in [SentinelBatchProcessor].
 */
class RunCommand {

    fun execute(configPath: String) {
        val configFile = File(configPath)
        val config = BatchConfigLoader.load(configFile)

        val eventsFile = resolveFile(configFile, config.events.source)
        val events = SceneEventEventParser.parse(eventsFile)

        val outputDir = resolveFile(configFile, config.events.output)
        outputDir.mkdirs()

        val writers = BatchWriters(
            jsonl = SignalJsonlWriter(File(outputDir, "signals.jsonl")),
            out = SignalOutWriter(File(outputDir, "signals.out")),
            log = LogWriter(File(outputDir, "engine.log")),
        )

        val calibration = config.toSentinelCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        val ctx = BatchContext(
            config = config,
            evaluator = evaluator,
            calibration = calibration,
            startTime = config.startTime,
            bedId = config.bedId,
            nightId = config.nightId,
        )

        writers.log.info("batch run started",
            "config" to configFile.absolutePath,
            "events" to events.size,
            "start" to ctx.startTime.toString(),
            "clock" to "event-time",
        )

        println("╔════════════════════════════════════════════════════════════╗")
        println("║         sentinel-batch — Run (event-time clock)           ║")
        println("╚════════════════════════════════════════════════════════════╝")
        println()
        println("  Config:   ${configFile.absolutePath}")
        println("  Events:   ${events.size} events from ${eventsFile.name}")
        println("  Output:   ${outputDir.absolutePath}")
        println("  Start:    ${ctx.startTime}")
        println("  Resident: ${config.resident.id} (bed ${config.resident.bed})")
        println("  Rules:    ${config.rules.size}")
        println()

        val initialState = BatchState(
            ledger = EpisodeLedger.empty(
                residentId = config.residentId,
            ),
            lastTime = ctx.startTime,
        )

        val finalState = events.fold(initialState) { state, event ->
            val result = SentinelBatchProcessor.processEvent(event, state, ctx, writers)
            result.state
        }

        println()
        println("  Results:  ${finalState.passed} processed, ${events.size} total")
        println("  Signals:  ${File(outputDir, "signals.jsonl").absolutePath}")
        println("  Output:   ${File(outputDir, "signals.out").absolutePath}")
        println("  Log:      ${File(outputDir, "engine.log").absolutePath}")

        writers.log.info("batch run completed",
            "passed" to finalState.passed,
            "total" to events.size,
        )
    }
}
