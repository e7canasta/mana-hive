package com.manahive.scene.batch

/**
 * Typed errors for scene-batch operations.
 *
 * Fowler: "Replace Error Code with Exception" — each error carries context.
 * Evans: "Domain events" — these are failure modes of the batch process.
 */
sealed class BatchError(override val message: String) : RuntimeException(message) {

    data class ConfigNotFound(val path: String) :
        BatchError("Config file not found: $path")

    data class EventsNotFound(val path: String) :
        BatchError("Events file not found: $path")

    data class ExpectedNotFound(val path: String) :
        BatchError("Expected facts file not found: $path")

    data class InvalidDuration(val raw: String) :
        BatchError("Invalid duration format: $raw (expected: 30s, 5m, 1h)")

    data class InvalidTransitionTable(val name: String) :
        BatchError("Unknown transition table: $name (expected: RELEASE_1, RELEASE_2)")

    data class ParseError(val line: Int, val reason: String) :
        BatchError("Parse error at line $line: $reason")

    data class VerifyFailed(val passed: Int, val failed: Int) :
        BatchError("Verification failed: $passed passed, $failed failed")

    data class DiffFound(val mismatched: Int, val missing: Int, val extra: Int) :
        BatchError("Diff found: $mismatched mismatched, $missing missing, $extra extra")

    class MissingArguments(command: String, usage: String) :
        BatchError("Missing arguments for '$command'. Usage: $usage")
}
