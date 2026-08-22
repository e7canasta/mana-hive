package com.manahive.sentinel.batch

/**
 * Typed errors for sentinel-batch operations.
 *
 * Fowler: "Replace Error Code with Exception" — each error carries context.
 */
sealed class SentinelBatchError(override val message: String) : RuntimeException(message) {

    data class ConfigNotFound(val path: String) :
        SentinelBatchError("Config file not found: $path")

    data class EventsNotFound(val path: String) :
        SentinelBatchError("Events file not found: $path")

    data class ExpectedNotFound(val path: String) :
        SentinelBatchError("Expected facts file not found: $path")

    data class InvalidDuration(val raw: String) :
        SentinelBatchError("Invalid duration format: $raw (expected: 30s, 5m, 1h)")

    data class ParseError(val line: Int, val reason: String) :
        SentinelBatchError("Parse error at line $line: $reason")

    data class VerifyFailed(val passed: Int, val failed: Int) :
        SentinelBatchError("Verification failed: $passed passed, $failed failed")

    data class DiffFound(val mismatched: Int, val missing: Int, val extra: Int) :
        SentinelBatchError("Diff found: $mismatched mismatched, $missing missing, $extra extra")

    class MissingArguments(command: String, usage: String) :
        SentinelBatchError("Missing arguments for '$command'. Usage: $usage")
}
