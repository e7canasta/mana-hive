package com.manahive.scene.batch.commands

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
 * Runs a batch simulation: reads events.dat, processes through SceneInterpreter + ClockSweeper,
 * writes facts.jsonl, facts.out, and engine.log.
 *
 * Clock mode: event-time (default). The sweeper runs at each event timestamp.
 *
 * Fowler: "Decompose Conditional" — the core logic lives in [BatchProcessor].
 */
class RunCommand {

    fun execute(configPath: String) {
        val configFile = File(configPath)
        val config = BatchConfigLoader.load(configFile)

        val eventsFile = resolveFile(configFile, config.events.source)
        val events = EventParser.parse(eventsFile)

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

        writers.log.info("batch run started",
            "config" to configFile.absolutePath,
            "events" to events.size,
            "start" to ctx.startTime.toString(),
            "clock" to "event-time",
        )

        println("╔════════════════════════════════════════════════════════════╗")
        println("║         scene-batch — Run (event-time clock)              ║")
        println("╚════════════════════════════════════════════════════════════╝")
        println()
        println("  Config:   ${configFile.absolutePath}")
        println("  Events:   ${events.size} events from ${eventsFile.name}")
        println("  Output:   ${outputDir.absolutePath}")
        println("  Start:    ${ctx.startTime}")
        println()

        val initialState = BatchState(
            twin = config.toDigitalTwin(),
            marks = DwellMarks.NONE,
            lastTime = ctx.startTime,
        )

        val finalState = events.fold(initialState) { state, event ->
            val result = BatchProcessor.processEvent(event, state, ctx, writers)
            result.state
        }

        println()
        println("  Results:  ${finalState.passed} passed, ${finalState.discarded} discarded, ${events.size} total")
        println("  Facts:    ${File(outputDir, "facts.jsonl").absolutePath}")
        println("  Output:   ${File(outputDir, "facts.out").absolutePath}")
        println("  Log:      ${File(outputDir, "engine.log").absolutePath}")

        writers.log.info("batch run completed",
            "passed" to finalState.passed,
            "discarded" to finalState.discarded,
            "total" to events.size,
        )
    }
}
