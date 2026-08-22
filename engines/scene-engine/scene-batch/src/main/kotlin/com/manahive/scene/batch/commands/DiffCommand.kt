package com.manahive.scene.batch.commands

import com.manahive.scene.batch.BatchError
import java.io.File

/**
 * Compares two .out files (events.dat format) and shows differences.
 *
 * Usage: scene-batch diff <expected.out> <actual.out>
 *
 * Output:
 * ```
 * Line 1: ✓ MATCH
 * Line 2: ✗ MISMATCH
 *   Expected: t=2s    TRANSITION Lying → BedEdge
 *   Actual:   t=2s    TRANSITION Lying → Standing
 * Line 3: ✓ MATCH
 *
 * Results: 2 matched, 1 mismatched, 3 total
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

        if (!expectedFile.exists()) throw BatchError.ExpectedNotFound(expectedPath)
        if (!actualFile.exists()) throw BatchError.ExpectedNotFound(actualPath)

        val expectedLines = expectedFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        val actualLines = actualFile.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        println("╔════════════════════════════════════════════════════════════╗")
        println("║         scene-batch — Diff                                ║")
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
            throw BatchError.DiffFound(result.mismatched, result.missing, result.extra)
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
