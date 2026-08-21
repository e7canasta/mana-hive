package com.manahive.hub.ledger

import com.manahive.contracts.EventEnvelope

/**
 * The ledger: append-only, one global order, optimistic concurrency per
 * stream. The conformance kit (test fixtures) is the executable spec every
 * adapter must pass — Postgres and in-memory alike.
 */
interface LedgerPort {
    fun append(stream: String, expectedSeq: Long?, events: List<EventEnvelope>): AppendResult
    fun read(stream: String): List<StoredEvent>
    fun readFrom(globalSeq: Long, limit: Int): List<StoredEvent>
}

sealed interface AppendResult {
    data class Appended(val lastGlobalSeq: Long) : AppendResult
    data object Conflict : AppendResult
    data object Duplicate : AppendResult
}

data class StoredEvent(
    val globalSeq: Long,
    val stream: String,
    val streamSeq: Long,
    val envelope: EventEnvelope,
)

/** Watermarks: a consumer's effect and its mark advance in ONE transaction. */
interface WatermarkPort {
    fun of(consumer: String): Long
    fun advance(consumer: String, to: Long)
}
