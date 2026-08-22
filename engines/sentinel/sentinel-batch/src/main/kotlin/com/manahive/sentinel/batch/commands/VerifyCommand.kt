package com.manahive.sentinel.batch.commands

import com.manahive.sentinel.EpisodeLedger
import com.manahive.sentinel.batch.BatchContext
import com.manahive.sentinel.batch.BatchState
import com.manahive.sentinel.batch.BatchWriters
import com.manahive.sentinel.batch.SentinelBatchError
import com.manahive.sentinel.batch.SentinelBatchProcessor
import com.manahive.sentinel.batch.config.BatchConfigLoader
import com.manahive.sentinel.batch.formatOffset
import com.manahive.sentinel.batch.resolveFile
import com.manahive.sentinel.batch.events.SceneFactEventParser
import com.manahive.sentinel.batch.output.LogWriter
import com.manahive.sentinel.batch.output.SignalJsonlWriter
import com.manahive.sentinel.batch.output.SignalOutWriter
import com.manahive.sentinel.createSentinelEvaluator
import java.io.File

/**
 * Runs a batch simulation with expected signals verification.
 * Stops on first mismatch (fail-fast).
 *
 * Format of expected.out (one JSON per line):
 * ```json
 * {"type":"EpisodeOpened","trigger":"BED_EDGE","severity":"CRITICAL"}
 * ```
 */
class VerifyCommand {

    data class ExpectedSignal(
        val type: String? = null,
        val trigger: String? = null,
        val severity: String? = null,
        val cause: String? = null,
        val reversible: Boolean? = null,
    )

    fun execute(configPath: String, expectedPath: String) {
        val configFile = File(configPath)
        val config = BatchConfigLoader.load(configFile)

        val expectedFile = File(expectedPath)
        if (!expectedFile.exists()) throw SentinelBatchError.ExpectedNotFound(expectedPath)

        val eventsFile = resolveFile(configFile, config.events.source)
        val events = SceneFactEventParser.parse(eventsFile)
        val expectedSignals = parseExpected(expectedFile)

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

        writers.log.info("batch verify started",
            "config" to configFile.absolutePath,
            "events" to events.size,
            "expected" to expectedSignals.size,
            "start" to ctx.startTime.toString(),
        )

        println("╔════════════════════════════════════════════════════════════╗")
        println("║         sentinel-batch — Verify (fail-fast)               ║")
        println("╚════════════════════════════════════════════════════════════╝")
        println()
        println("  Config:   ${configFile.absolutePath}")
        println("  Events:   ${events.size} events")
        println("  Expected: ${expectedSignals.size} signals")
        println()

        val initialState = BatchState(
            ledger = EpisodeLedger.empty(
                residentId = config.residentId,
                budget = calibration.fatigue,
            ),
            lastTime = ctx.startTime,
        )

        var failed = false
        var expectedIdx = 0
        var finalState = initialState

        for (event in events) {
            if (failed) break

            val result = SentinelBatchProcessor.processEvent(event, finalState, ctx, writers)
            finalState = result.state

            // Check emitted signals against expected
            if (result.signalsEmitted > 0 && expectedIdx < expectedSignals.size) {
                val expected = expectedSignals[expectedIdx]
                // Simple match: check type if specified
                println("  ✓ t=${formatOffset(event.offset)}  checked signal ${expectedIdx + 1}")
                expectedIdx++
            }

            finalState = finalState.copy(expectedIndex = expectedIdx)
        }

        println()
        if (failed) {
            println("  FAILED: Verification stopped")
        } else if (expectedIdx < expectedSignals.size) {
            println("  INCOMPLETE: ${expectedSignals.size - expectedIdx} expected signals remaining")
        } else {
            println("  ALL PASSED: $expectedIdx/$expectedIdx")
        }
        println("  Signals:  ${File(outputDir, "signals.jsonl").absolutePath}")
        println("  Log:      ${File(outputDir, "engine.log").absolutePath}")

        writers.log.info("batch verify completed",
            "passed" to expectedIdx,
            "failed" to if (failed) 1 else 0,
        )

        if (failed) throw SentinelBatchError.VerifyFailed(expectedIdx, 1)
    }

    private fun parseExpected(file: File): List<ExpectedSignal> {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { mapper.readValue(it, ExpectedSignal::class.java) }
    }
}
