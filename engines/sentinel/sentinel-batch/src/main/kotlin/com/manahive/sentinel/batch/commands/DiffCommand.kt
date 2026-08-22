package com.manahive.sentinel.batch.commands

import com.manahive.sentinel.batch.SentinelBatchError
import java.io.File

/**
 * Compares two .out files (signal format) and shows differences.
 *
 * Usage: sentinel-batch diff <expected.out> <actual.out>
 *
 * Output:
 * ```
 * Line 1: ✓ MATCH
 * Line 2: ✗ MISMATCH
 *   Expected: t=0s    EPISODE_OPENED episode=301-xxx rule=r-fall trigger=BED_EDGE severity=CRITICAL
 *   Actual:   t=0s    EPISODE_OPENED episode=301-xxx rule=r-fall trigger=BED_EDGE severity=WARNING
 * ```
 */
class DiffCommand {

    data class DiffResult(
        val matched: Int = 0,
        val mismatched: Int = 0,
        val missing: Int = 0,
        val extra: Int = 0,
    ) {
        val hasErrors: Boolean get() = mismatched > 0 || missing > 0 || extra > 0
    }

    fun execute(expectedPath: String, actualPath: String) {
        val expectedFile = File(expectedPath)
        val actualFile = File(actualPath)

        if (!expectedFile.exists()) throw SentinelBatchError.ExpectedNotFound(expectedPath)
        if (!actualFile.exists()) throw SentinelBatchError.ExpectedNotFound(actualPath)

        val expectedLines = expectedFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        val actualLines = actualFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        println("╔════════════════════════════════════════════════════════════╗")
        println("║         sentinel-batch — Diff                             ║")
        println("╚════════════════════════════════════════════════════════════╝")
        println()
        println("  Expected: $expectedPath (${expectedLines.size} lines)")
        println("  Actual:   $actualPath (${actualLines.size} lines)")
        println()

        val result = (0 until maxOf(expectedLines.size, actualLines.size)).fold(DiffResult()) { acc, i ->
            val expected = expectedLines.getOrNull(i)
            val actual = actualLines.getOrNull(i)

            when {
                expected == null -> {
                    println("  Line ${i + 1}: + EXTRA (not in expected)")
                    println("    Actual:   ${actual!!.trim()}")
                    println()
                    acc.copy(extra = acc.extra + 1)
                }
                actual == null -> {
                    println("  Line ${i + 1}: - MISSING (not in actual)")
                    println("    Expected: ${expected.trim()}")
                    println()
                    acc.copy(missing = acc.missing + 1)
                }
                normalizeLine(expected) == normalizeLine(actual) -> {
                    println("  Line ${i + 1}: ✓ ${expected.trim()}")
                    acc.copy(matched = acc.matched + 1)
                }
                else -> {
                    println("  Line ${i + 1}: ✗ MISMATCH")
                    println("    Expected: ${expected.trim()}")
                    println("    Actual:   ${actual.trim()}")
                    println()
                    acc.copy(mismatched = acc.mismatched + 1)
                }
            }
        }

        println()
        println("  Results: ${result.matched} matched, ${result.mismatched} mismatched, ${result.missing} missing, ${result.extra} extra, ${maxOf(expectedLines.size, actualLines.size)} total")

        if (result.hasErrors) {
            println()
            println("  EXIT: DIFFERENCES FOUND")
            throw SentinelBatchError.DiffFound(result.mismatched, result.missing, result.extra)
        } else {
            println()
            println("  EXIT: ALL MATCH")
        }
    }

    private fun normalizeLine(line: String): String = line
        .replace(Regex("""#.*"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase()
}
