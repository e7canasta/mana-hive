package com.manahive.harbor.batch

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.manahive.harbor.*
import com.manahive.kernel.ResidentId
import java.io.File
import java.time.Clock
import java.time.Instant

/**
 * Harbor batch CLI.
 *
 * Two modes of operation:
 * 1. JSONL mode: --signals input.jsonl --output output.jsonl
 * 2. Data.out mode: --sentinel sentinel.out --harbor harbor.out
 *
 * Usage:
 *   harbor-batch run --signals input.jsonl --output output.jsonl
 *   harbor-batch run --sentinel sentinel.out --harbor harbor.out
 *   harbor-batch verify --signals input.jsonl --expected expected.jsonl
 */
class HarborBatchApp : CliktCommand(
    name = "harbor-batch",
    help = "Process Sentinel signals and manage notice lifecycle"
) {
    override fun run() = Unit
}

class RunCommand : CliktCommand(
    name = "run",
    help = "Process signals and produce notice events"
) {
    // JSONL mode
    private val signals by option("--signals", help = "SentinelSignal JSONL file").file()
    private val output by option("--output", help = "Output JSONL file").file()

    // Data.out mode
    private val sentinel by option("--sentinel", help = "Sentinel.out file").file()
    private val harbor by option("--harbor", help = "Harbor.out file").file()

    // Common options
    private val resident by option("--resident", help = "Resident ID").default("default")

    override fun run() {
        runWithClock(Clock.systemUTC())
    }

    internal fun runWithClock(clock: Clock) {
        when {
            signals != null && output != null -> runJsonlMode(clock)
            sentinel != null && harbor != null -> runDataOutMode(clock)
            else -> throw IllegalArgumentException(
                "Either: --signals <input.jsonl> --output <output.jsonl>\n" +
                "    or: --sentinel <sentinel.out> --harbor <harbor.out>"
            )
        }
    }

    private fun runJsonlMode(clock: Clock) {
        val inputSignals = SignalParser.parseWithLineNumbers(signals!!)
        processSignals(inputSignals.map { it.signal }, output!!, clock)
    }

    private fun runDataOutMode(clock: Clock) {
        val inputSignals = SignalParser.parse(sentinel!!)
        processSignals(inputSignals, harbor!!, clock)
    }

    /**
     * Fowler: "Extract Method" — shared processing logic.
     * Both modes use the same processing; only the input/output differs.
     */
    private fun processSignals(
        signals: List<com.manahive.contracts.sentinel.SentinelSignal>,
        outputFile: File,
        clock: Clock,
    ) {
        val calibration = HarborCalibration.default(ResidentId(resident))
        val engine = createHarborEngine(calibration)
        var state = HarborState(budget = calibration.budget)
        val events = mutableListOf<NoticeEvent>()

        println("Processing ${signals.size} signals...")

        for (signal in signals) {
            val now = Instant.now(clock)
            val result = engine.evaluate(signal, state, now)
            state = result.value.state
            events.addAll(result.value.commands.mapNotNull { it.toEvent(now) })

            println("  t=${signal.at}  ${signal::class.simpleName} → ${result.value.commands.size} command(s)")
        }

        events.writeTo(outputFile)
        println("Wrote ${events.size} events to ${outputFile.absolutePath}")
    }
}

class VerifyCommand : CliktCommand(
    name = "verify",
    help = "Verify signals produce expected events"
) {
    private val signals by option("--signals", help = "SentinelSignal JSONL file").file()
    private val expected by option("--expected", help = "Expected events JSONL file").file()

    override fun run() {
        println("Verify not yet implemented")
    }
}

fun main(args: Array<String>) = HarborBatchApp()
    .subcommands(RunCommand(), VerifyCommand())
    .main(args)
