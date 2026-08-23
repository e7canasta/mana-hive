package com.manahive.hub.ledger

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.ledger.AppendResult
import com.manahive.contracts.ledger.LedgerPort
import com.manahive.contracts.ledger.StoredEvent

/**
 * High-level event store API for the Hub.
 *
 * Vernon: "The event store is the Hub's primary interface for event
 * ingestion and retrieval. It wraps the ledger with domain-specific
 * operations."
 *
 * Fowler: "The event store provides a clean API for the Hub's core
 * responsibility: storing events as the system of record."
 *
 * Depends on LedgerPort (abstraction), not InMemoryLedger (concrete).
 */
public class EventStore(
    private val ledger: LedgerPort,
) {

    /**
     * Store events for a stream.
     */
    public fun store(
        stream: String,
        events: List<EventEnvelope>,
        expectedSeq: Long? = null,
    ): StoreResult {
        val result = ledger.append(stream, expectedSeq, events)
        return when (result) {
            is AppendResult.Appended -> StoreResult.Stored(
                lastGlobalSeq = result.lastGlobalSeq,
                count = events.size,
            )
            is AppendResult.Conflict -> StoreResult.Conflict(
                message = "Expected sequence $expectedSeq but stream has different sequence",
            )
            is AppendResult.Duplicate -> StoreResult.Duplicate(
                message = "One or more events already exist in stream",
            )
        }
    }

    /**
     * Store events with existing events (merge).
     *
     * Fowler: "Domain Logic in Application Layer" — this merge logic
     * belongs in the domain, not in the batch command.
     */
    public fun storeMerged(
        stream: String,
        events: List<EventEnvelope>,
        existingEvents: List<StoredEvent>,
    ): StoreResult {
        val maxGlobalSeq = existingEvents.maxOfOrNull { it.globalSeq } ?: 0L

        // Assign globalSeq to new events
        val newStoredEvents = events.mapIndexed { index, envelope ->
            StoredEvent(
                globalSeq = maxGlobalSeq + index + 1,
                stream = stream,
                streamSeq = existingEvents.size.toLong() + index + 1,
                envelope = envelope,
            )
        }

        val merged = mergeEvents(existingEvents, newStoredEvents)
        val lastGlobalSeq = merged.lastOrNull()?.globalSeq ?: maxGlobalSeq

        return StoreResult.Merged(
            lastGlobalSeq = lastGlobalSeq,
            totalCount = merged.size,
            newCount = events.size,
            existingCount = existingEvents.size,
            mergedEvents = merged,
        )
    }

    /**
     * Replay events from a global sequence.
     */
    public fun replay(
        fromGlobalSeq: Long = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<StoredEvent> {
        return ledger.readFrom(fromGlobalSeq, limit)
    }

    /**
     * Replay events for a specific stream.
     */
    public fun replayStream(stream: String): List<StoredEvent> {
        return ledger.read(stream)
    }
}

/**
 * Merge existing and new events, preserving globalSeq.
 *
 * Vernon: "The merge operation is a domain concept — it ensures
 * event ordering and deduplication."
 *
 * Fowler: "Pure Function" — stateless, testable, no side effects.
 *
 * Note: This is an internal implementation detail of EventStore.storeMerged().
 * It's exposed as a top-level function for testability, but should not be
 * used directly by other modules.
 */
internal fun mergeEvents(
    existing: List<StoredEvent>,
    newEvents: List<StoredEvent>,
): List<StoredEvent> {
    val merged = mutableListOf<StoredEvent>()
    val seenIds = mutableSetOf<String>()

    // First add existing events with their original globalSeq
    existing.forEach { stored ->
        if (stored.envelope.eventId !in seenIds) {
            merged.add(stored)
            seenIds.add(stored.envelope.eventId)
        }
    }

    // Then add new events
    newEvents.forEach { stored ->
        if (stored.envelope.eventId !in seenIds) {
            merged.add(stored)
            seenIds.add(stored.envelope.eventId)
        }
    }

    // Sort by globalSeq
    return merged.sortedBy { it.globalSeq }
}

/**
 * Store result.
 *
 * Vernon: "Ubiquitous Language" — these are store results in the domain.
 */
public sealed interface StoreResult {
    public data class Stored(
        val lastGlobalSeq: Long,
        val count: Int,
    ) : StoreResult

    public data class Merged(
        val lastGlobalSeq: Long,
        val totalCount: Int,
        val newCount: Int,
        val existingCount: Int,
        val mergedEvents: List<StoredEvent>,
    ) : StoreResult

    public data class Conflict(val message: String) : StoreResult
    public data class Duplicate(val message: String) : StoreResult
}
