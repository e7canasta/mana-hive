package com.manahive.politica.batch

import java.io.File

/**
 * politica-batch — CLI for batch testing politica-engine pipeline.
 *
 * Usage:
 *   politica-batch run <events.dat> <output.out>
 *   politica-batch diff <expected.out> <actual.out>
 *
 * events.dat format:
 * ```
 * # comment
 * resident maria risk HIGH mobility WALKER autopilot false mode PRESET at 2026-08-21T03:00:00Z
 * ```
 */
internal fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (args[0]) {
        "run" -> {
            if (args.size < 3) {
                System.err.println("Usage: politica-batch run <events.dat> <output.out>")
                return
            }
            run(args[1], args[2])
        }
        "diff" -> {
            if (args.size < 3) {
                System.err.println("Usage: politica-batch diff <expected.out> <actual.out>")
                return
            }
            diff(args[1], args[2])
        }
        else -> {
            System.err.println("Unknown command: ${args[0]}")
            printUsage()
        }
    }
}

private fun run(datPath: String, outPath: String) {
    val events = PolicyEventParser.parse(File(datPath))
    val results = PolicyBatchProcessor.run(events)
    PolicyOutWriter.write(results, File(outPath))

    println("poliica-batch run")
    println("  Input:   ${events.size} events from $datPath")
    println("  Output:  $outPath")
    println()
    results.forEach { result ->
        val source = result.emittedEvents.firstOrNull()?.source
        println("  ${result.residentId.value} → $source")
    }
}

private fun diff(expectedPath: String, actualPath: String) {
    val expected = File(expectedPath).readLines().filter { it.isNotBlank() }
    val actual = File(actualPath).readLines().filter { it.isNotBlank() }

    if (expected == actual) {
        println("✓ MATCH")
        return
    }

    println("✗ DIFF")
    println("  Expected: ${expected.size} lines ($expectedPath)")
    println("  Actual:   ${actual.size} lines ($actualPath)")
    println()

    val max = maxOf(expected.size, actual.size)
    for (i in 0 until max) {
        val e = expected.getOrNull(i) ?: "<missing>"
        val a = actual.getOrNull(i) ?: "<missing>"
        if (e != a) {
            println("  line ${i + 1}:")
            println("    expected: $e")
            println("    actual:   $a")
        }
    }
}

private fun printUsage() {
    println("""
politica-batch — Policy Engine Batch Tool

Usage:
  politica-batch run <events.dat> <output.out>
  politica-batch diff <expected.out> <actual.out>

events.dat format:
  resident <id> risk <HIGH|LOW|MEDIUM> mobility <NONE|WALKER|WHEELCHAIR> autopilot <true|false> mode <PRESET|CUSTOM> at <ISO-8601>
    """.trimIndent())
}
