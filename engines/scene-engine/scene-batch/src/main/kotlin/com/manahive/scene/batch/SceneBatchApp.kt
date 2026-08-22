package com.manahive.scene.batch

import com.manahive.scene.batch.commands.DiffCommand
import com.manahive.scene.batch.commands.RunCommand
import com.manahive.scene.batch.commands.VerifyCommand

/**
 * scene-batch — CLI entry point.
 *
 * Usage:
 * ```
 * scene-batch run <run.yaml>                          # execute simulation
 * scene-batch verify <run.yaml> <expected.out>        # verify against expected
 * scene-batch diff <expected.out> <actual.out>         # compare two output files
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
    } catch (e: BatchError) {
        System.err.println("ERROR: ${e.message}")
        throw e
    } catch (e: Exception) {
        System.err.println("ERROR: ${e.message}")
        throw e
    }
}

private fun run(args: Array<String>) {
    if (args.isEmpty()) {
        throw BatchError.MissingArguments("run", "scene-batch run <run.yaml>")
    }
    RunCommand().execute(args[0])
}

private fun verify(args: Array<String>) {
    if (args.size < 2) {
        throw BatchError.MissingArguments("verify", "scene-batch verify <run.yaml> <expected.out>")
    }
    VerifyCommand().execute(args[0], args[1])
}

private fun diff(args: Array<String>) {
    if (args.size < 2) {
        throw BatchError.MissingArguments("diff", "scene-batch diff <expected.out> <actual.out>")
    }
    DiffCommand().execute(args[0], args[1])
}

private fun printUsage() {
    println("""
╔════════════════════════════════════════════════════════════╗
║         scene-batch — Scene Engine Batch Tool              ║
╚════════════════════════════════════════════════════════════╝

Usage:
  scene-batch run <run.yaml>
      Execute a batch simulation.

  scene-batch verify <run.yaml> <expected.out>
      Execute and verify against expected facts (fail-fast).

  scene-batch diff <expected.out> <actual.out>
      Compare two output files.

Examples:
  scene-batch run scenarios/fall-at-03/run.yaml
  scene-batch verify scenarios/fall-at-03/run.yaml expected/fall-at-03.out
  scene-batch diff expected/fall-at-03.out output/fall-at-03/facts.out
    """.trimIndent())
}
