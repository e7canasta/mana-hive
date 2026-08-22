package com.manahive.sentinel.batch

import com.manahive.sentinel.batch.commands.DiffCommand
import com.manahive.sentinel.batch.commands.RunCommand
import com.manahive.sentinel.batch.commands.VerifyCommand

/**
 * sentinel-batch — CLI entry point.
 *
 * Usage:
 * ```
 * sentinel-batch run <run.yaml>                          # execute simulation
 * sentinel-batch verify <run.yaml> <expected.out>        # verify against expected
 * sentinel-batch diff <expected.out> <actual.out>         # compare two output files
 * ```
 */
internal fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    val commandArgs = args.drop(1).toTypedArray()

    try {
        when (command) {
            "run" -> run(commandArgs)
            "verify" -> verify(commandArgs)
            "diff" -> diff(commandArgs)
            else -> {
                println("Unknown command: $command")
                printUsage()
                throw IllegalArgumentException("Unknown command: $command")
            }
        }
    } catch (e: SentinelBatchError) {
        System.err.println("ERROR: ${e.message}")
        throw e
    } catch (e: Exception) {
        System.err.println("ERROR: ${e.message}")
        throw e
    }
}

private fun run(args: Array<String>) {
    if (args.isEmpty()) {
        throw SentinelBatchError.MissingArguments("run", "sentinel-batch run <run.yaml>")
    }
    RunCommand().execute(args[0])
}

private fun verify(args: Array<String>) {
    if (args.size < 2) {
        throw SentinelBatchError.MissingArguments("verify", "sentinel-batch verify <run.yaml> <expected.out>")
    }
    VerifyCommand().execute(args[0], args[1])
}

private fun diff(args: Array<String>) {
    if (args.size < 2) {
        throw SentinelBatchError.MissingArguments("diff", "sentinel-batch diff <expected.out> <actual.out>")
    }
    DiffCommand().execute(args[0], args[1])
}

private fun printUsage() {
    println("""
╔════════════════════════════════════════════════════════════╗
║         sentinel-batch — Sentinel Engine Batch Tool         ║
╚════════════════════════════════════════════════════════════╝

Usage:
  sentinel-batch run <run.yaml>
      Execute a batch simulation.

  sentinel-batch verify <run.yaml> <expected.out>
      Execute and verify against expected signals (fail-fast).

  sentinel-batch diff <expected.out> <actual.out>
      Compare two output files.

Examples:
  sentinel-batch run scenarios/fall-at-03/run.yaml
  sentinel-batch verify scenarios/fall-at-03/run.yaml expected/fall-at-03.out
  sentinel-batch diff expected/fall-at-03.out output/fall-at-03/signals.out
    """.trimIndent())
}
