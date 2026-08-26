package com.manahive.scene.batch.commands

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.kind
import com.manahive.scene.batch.BatchContext
import com.manahive.scene.batch.BatchError
import com.manahive.scene.batch.BatchProcessor
import com.manahive.scene.batch.BatchState
import com.manahive.scene.batch.BatchWriters
import com.manahive.scene.batch.config.BatchConfigLoader
import com.manahive.scene.batch.config.formatOffset
import com.manahive.scene.batch.config.resolveFile
import com.manahive.scene.batch.events.EventParser
import com.manahive.scene.batch.output.FactsOutWriter
import com.manahive.scene.batch.output.FactsWriter
import com.manahive.scene.batch.output.LogWriter
import com.manahive.scene.calibration.toDwellCatalog
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.sweeper.DwellMarks
import com.manahive.scene.sweeper.createSweeper
import java.io.File

/**
 * Runs a batch simulation with expected facts verification.
 * Stops on first mismatch (fail-fast).
 *
 * Format of expected.out (human-readable, diff-friendly):
 * ```
 * t=2s      TRANSITION LYING -> BED_EDGE           # <- evento 6
 * t=4m      SIGNAL_LOST monitor=m1                 # <- evento 8
 * t=5m      DWELL_EXCEEDED STANDING PT5M           # <- evento 10
 * ```
 *
 * Also supports DISCARD markers for expected rejections:
 * ```
 * t=0s      DISCARD                                # <- evento 1
 * ```
 *
 * Fowler: "Fail-Fast" - stops on first deviation from expected behavior.
 * Vernon: "Specification as first-class citizen" - expected.out IS the spec.
 */
class VerifyCommand {

    /**
     * Parsed expected fact from the golden file.
     *
     * Fowler: "Replace Temp with Query" - the parsed line IS the expected spec,
     * not a raw string we keep re-parsing.
     */
    data class ExpectedFact(
        val type: String,
        val from: String? = null,
        val to: String? = null,
        val detail: String? = null,
    )

    fun execute(configPath: String, expectedPath: String) {
        val configFile = File(configPath)
        val config = BatchConfigLoader.load(configFile)

        val expectedFile = File(expectedPath)
        if (!expectedFile.exists()) throw BatchError.ExpectedNotFound(expectedPath)

        val eventsFile = resolveFile(configFile, config.events.source)
        val events = EventParser.parse(eventsFile)
        val expectedFacts = parseExpected(expectedFile)

        val outputDir = resolveFile(configFile, config.events.output)
        outputDir.mkdirs()

        val writers = BatchWriters(
            jsonl = FactsWriter(File(outputDir, "facts.jsonl")),
            out = FactsOutWriter(File(outputDir, "facts.out")),
            log = LogWriter(File(outputDir, "engine.log")),
        )

        val ctx = BatchContext(
            config = config,
            interpreter = createInterpreter(config.toSceneCalibration()),
            sweeper = createSweeper(),
            dwellCatalog = config.toSceneCalibration().toDwellCatalog(),
            startTime = config.startTime,
        )

        writers.log.info("batch verify started",
            "config" to configFile.absolutePath,
            "events" to events.size,
            "expected" to expectedFacts.size,
            "start" to ctx.startTime.toString(),
        )

        println("===========================================================")
        println("         scene-batch -- Verify (fail-fast)")
        println("===========================================================")
        println()
        println("  Config:   ${configFile.absolutePath}")
        println("  Events:   ${events.size} events")
        println("  Expected: ${expectedFacts.size} facts")
        println()

        val initialState = BatchState(
            twin = config.toDigitalTwin(),
            marks = DwellMarks.NONE,
            lastTime = ctx.startTime,
        )

        var failed = false
        var failReason = ""
        var finalState = initialState
        var expectedIndex = 0

        for (event in events) {
            if (failed) break

            val result = BatchProcessor.processEvent(event, finalState, ctx, writers)
            val allFacts = result.facts

            for (fact in allFacts) {
                if (failed) break
                if (expectedIndex >= expectedFacts.size) {
                    println("  X t=${formatOffset(event.offset)}  EXTRA fact: ${fact::class.simpleName}")
                    failReason = "extra fact at t=${formatOffset(event.offset)}: ${fact::class.simpleName}"
                    failed = true
                    break
                }

                val expected = expectedFacts[expectedIndex]
                val match = matchFact(fact, expected)

                if (match) {
                    println("  OK t=${formatOffset(event.offset)}  ${formatFactShort(fact)}")
                    expectedIndex++
                } else {
                    println("  X t=${formatOffset(event.offset)}  MISMATCH")
                    println("    Expected: ${expected.type} ${expected.from ?: ""} ${expected.to ?: ""}")
                    println("    Actual:   ${formatFactShort(fact)}")
                    failReason = "mismatch at t=${formatOffset(event.offset)}: expected ${expected.type}, got ${fact::class.simpleName}"
                    failed = true
                }
            }

            if (!failed && result.state.discarded > finalState.discarded && expectedIndex < expectedFacts.size) {
                val expected = expectedFacts[expectedIndex]
                if (expected.type == "DISCARD") {
                    println("  OK t=${formatOffset(event.offset)}  DISCARD (expected)")
                    expectedIndex++
                }
            }

            finalState = result.state
        }

        println()
        if (failed) {
            println("  FAILED: $failReason")
        } else if (expectedIndex < expectedFacts.size) {
            val remaining = expectedFacts.size - expectedIndex
            println("  INCOMPLETE: $remaining expected facts not produced")
            println("  Results: ${finalState.passed} passed, ${finalState.discarded} discarded, events exhausted")
        } else {
            println("  ALL PASSED: ${finalState.passed} accepted, ${finalState.discarded} discarded")
        }
        println("  Facts:    ${File(outputDir, "facts.jsonl").absolutePath}")
        println("  Log:      ${File(outputDir, "engine.log").absolutePath}")

        writers.log.info("batch verify completed",
            "passed" to finalState.passed,
            "failed" to if (failed) 1 else 0,
        )

        if (failed) throw BatchError.VerifyFailed(finalState.passed, 1)
    }

    /**
     * Matches a produced [SceneEvent] against an [ExpectedFact].
     *
     * Uses the FactOutWriter's format semantics to compare.
     * Returns true if the fact matches the expected type and parameters.
     */
    private fun matchFact(fact: SceneEvent, expected: ExpectedFact): Boolean {
        return when (expected.type) {
            "DISCARD" -> false

            "TRANSITION" -> {
                fact is SceneEvent.TransitionDetected &&
                    fact.from.kind.name == expected.from &&
                    fact.to.kind.name == expected.to
            }

            "DWELL_WARNING" -> {
                fact is SceneEvent.DwellWarning &&
                    fact.state.kind.name == expected.from
            }

            "DWELL_EXCEEDED" -> {
                fact is SceneEvent.DwellExceeded &&
                    fact.state.kind.name == expected.from
            }

            "COMEBACK_WARNING" -> {
                fact is SceneEvent.ComeBackWarning &&
                    fact.baseline.kind.name == expected.from
            }

            "COMEBACK_EXCEEDED" -> {
                fact is SceneEvent.ComeBackExceeded &&
                    fact.baseline.kind.name == expected.from
            }

            "SIGNAL_LOST" -> {
                fact is SceneEvent.SignalLost
            }

            "SIGNAL_RECOVERED" -> {
                fact is SceneEvent.SignalRecovered
            }

            "SCENE_CHANGED" -> {
                fact is SceneEvent.SceneStateChanged &&
                    fact.field == expected.from
            }

            "SCENE_DWELL_WARNING" -> {
                fact is SceneEvent.SceneDwellWarning &&
                    fact.field == expected.from
            }

            "SCENE_DWELL_EXCEEDED" -> {
                fact is SceneEvent.SceneDwellExceeded &&
                    fact.field == expected.from
            }

            "STAFF_PRESENCE" -> {
                fact is SceneEvent.StaffPresenceDetected
            }

            "STAFF_LEFT" -> {
                fact is SceneEvent.StaffLeftDetected
            }

            "NIGHT_OPENED" -> {
                fact is SceneEvent.NightOpened
            }

            "NIGHT_CLOSED" -> {
                fact is SceneEvent.NightClosed
            }

            else -> false
        }
    }

    /**
     * Formats a fact as a short string for display.
     */
    private fun formatFactShort(fact: SceneEvent): String = when (fact) {
        is SceneEvent.TransitionDetected ->
            "TRANSITION ${fact.from.kind.name} -> ${fact.to.kind.name}"
        is SceneEvent.DwellWarning ->
            "DWELL_WARNING ${fact.state.kind.name}"
        is SceneEvent.DwellExceeded ->
            "DWELL_EXCEEDED ${fact.state.kind.name}"
        is SceneEvent.ComeBackWarning ->
            "COMEBACK_WARNING ${fact.baseline.kind.name}"
        is SceneEvent.ComeBackExceeded ->
            "COMEBACK_EXCEEDED ${fact.baseline.kind.name}"
        is SceneEvent.SignalLost ->
            "SIGNAL_LOST monitor=${fact.monitor.value}"
        is SceneEvent.SignalRecovered ->
            "SIGNAL_RECOVERED monitor=${fact.monitor.value}"
        is SceneEvent.SceneStateChanged ->
            "SCENE_CHANGED ${fact.field}"
        is SceneEvent.SceneDwellWarning ->
            "SCENE_DWELL_WARNING ${fact.field}"
        is SceneEvent.SceneDwellExceeded ->
            "SCENE_DWELL_EXCEEDED ${fact.field}"
        is SceneEvent.StaffPresenceDetected ->
            "STAFF_PRESENCE"
        is SceneEvent.StaffLeftDetected ->
            "STAFF_LEFT"
        is SceneEvent.NightOpened ->
            "NIGHT_OPENED"
        is SceneEvent.NightClosed ->
            "NIGHT_CLOSED"
    }

    /**
     * Parses expected.out format into [ExpectedFact] list.
     *
     * Format: `t=<offset>  <TYPE> <details>          # <- evento <n>`
     *
     * Examples:
     * - `t=2s      TRANSITION LYING -> BED_EDGE`
     * - `t=4m      SIGNAL_LOST monitor=m1`
     * - `t=0s      DISCARD`
     */
    private fun parseExpected(file: File): List<ExpectedFact> {
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line -> parseExpectedLine(line) }
    }

    private fun parseExpectedLine(line: String): ExpectedFact {
        // Strip comment: `# <- evento N`
        val body = line.replace(Regex("""#.*"""), "").trim()
        // Strip `t=<offset>` prefix
        val afterT = body.replace(Regex("""^t=\S+\s*"""), "").trim()
        val parts = afterT.split(Regex("""\s+"""))

        if (parts.isEmpty() || parts[0].isEmpty()) {
            return ExpectedFact(type = "UNKNOWN", detail = line)
        }

        return when (parts[0]) {
            "TRANSITION" -> ExpectedFact(
                type = "TRANSITION",
                from = parts.getOrNull(1),
                to = parts.getOrNull(3),
            )
            "DWELL_WARNING" -> ExpectedFact(
                type = "DWELL_WARNING",
                from = parts.getOrNull(1),
                detail = parts.getOrNull(2),
            )
            "DWELL_EXCEEDED" -> ExpectedFact(
                type = "DWELL_EXCEEDED",
                from = parts.getOrNull(1),
                detail = parts.getOrNull(2),
            )
            "COMEBACK_WARNING" -> ExpectedFact(
                type = "COMEBACK_WARNING",
                from = parts.getOrNull(1),
                detail = parts.getOrNull(2),
            )
            "COMEBACK_EXCEEDED" -> ExpectedFact(
                type = "COMEBACK_EXCEEDED",
                from = parts.getOrNull(1),
                detail = parts.getOrNull(2),
            )
            "SIGNAL_LOST" -> ExpectedFact(type = "SIGNAL_LOST")
            "SIGNAL_RECOVERED" -> ExpectedFact(type = "SIGNAL_RECOVERED")
            "SCENE_CHANGED" -> ExpectedFact(
                type = "SCENE_CHANGED",
                from = parts.getOrNull(1),
            )
            "SCENE_DWELL_WARNING" -> ExpectedFact(
                type = "SCENE_DWELL_WARNING",
                from = parts.getOrNull(1),
            )
            "SCENE_DWELL_EXCEEDED" -> ExpectedFact(
                type = "SCENE_DWELL_EXCEEDED",
                from = parts.getOrNull(1),
            )
            "STAFF_PRESENCE" -> ExpectedFact(type = "STAFF_PRESENCE")
            "STAFF_LEFT" -> ExpectedFact(type = "STAFF_LEFT")
            "NIGHT_OPENED" -> ExpectedFact(type = "NIGHT_OPENED")
            "NIGHT_CLOSED" -> ExpectedFact(type = "NIGHT_CLOSED")
            "DISCARD" -> ExpectedFact(type = "DISCARD")
            else -> ExpectedFact(type = parts[0], detail = afterT)
        }
    }
}
