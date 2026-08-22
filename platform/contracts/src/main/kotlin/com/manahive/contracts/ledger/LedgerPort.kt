package com.manahive.contracts.ledger

import com.manahive.contracts.EventEnvelope

/**
 * The ledger: append-only, one global order, optimistic concurrency per
 * stream. The conformance kit (test fixtures) is the executable spec every
 * adapter must pass — Postgres and in-memory alike.
 */
public interface LedgerPort {
    public fun append(stream: String, expectedSeq: Long?, events: List<EventEnvelope>): AppendResult
    public fun read(stream: String): List<StoredEvent>
    public fun readFrom(globalSeq: Long, limit: Int): List<StoredEvent>
}
