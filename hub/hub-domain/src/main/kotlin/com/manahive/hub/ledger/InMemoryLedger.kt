package com.manahive.hub.ledger

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.ledger.AppendResult
import com.manahive.contracts.ledger.LedgerPort
import com.manahive.contracts.ledger.StoredEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory ledger implementation for testing and batch processing.
 *
 * Fowler: "The in-memory ledger is the simplest possible implementation.
 * It's perfect for batch tools and integration tests."
 *
 * Vernon: "The ledger is the system of record. Every event is stored
 * exactly once, in global order."
 *
 * Single Responsibility: Only handles event storage and retrieval.
 * Watermark tracking is in InMemoryWatermarkStore.
 *
 * Thread Safety: Uses ConcurrentHashMap.computeIfAbsent for atomic
 * stream creation and CopyOnWriteArrayList for thread-safe event storage.
 */
public class InMemoryLedger(
    private val initialGlobalSeq: Long = 0,
) : LedgerPort {

    private val globalSeq = AtomicLong(initialGlobalSeq)
    private val streams = ConcurrentHashMap<String, CopyOnWriteArrayList<StoredEvent>>()

    override fun append(
        stream: String,
        expectedSeq: Long?,
        events: List<EventEnvelope>,
    ): AppendResult {
        // computeIfAbsent is atomic — no race condition
        val streamEvents = streams.computeIfAbsent(stream) { CopyOnWriteArrayList() }

        // Optimistic concurrency check
        if (expectedSeq != null) {
            val currentSeq = streamEvents.size.toLong()
            if (currentSeq != expectedSeq) {
                return AppendResult.Conflict
            }
        }

        // Check for duplicates
        val existingIds = streamEvents.map { it.envelope.eventId }.toSet()
        val duplicates = events.filter { it.eventId in existingIds }
        if (duplicates.isNotEmpty()) {
            return AppendResult.Duplicate
        }

        // Append events — CopyOnWriteArrayList is thread-safe
        val storedEvents = events.map { envelope ->
            val seq = globalSeq.incrementAndGet()
            val streamSeq = streamEvents.size.toLong() + 1
            StoredEvent(
                globalSeq = seq,
                stream = stream,
                streamSeq = streamSeq,
                envelope = envelope,
            )
        }

        streamEvents.addAll(storedEvents)
        return AppendResult.Appended(lastGlobalSeq = globalSeq.get())
    }

    override fun read(stream: String): List<StoredEvent> {
        return streams[stream]?.toList() ?: emptyList()
    }

    override fun readFrom(globalSeq: Long, limit: Int): List<StoredEvent> {
        return streams.values
            .flatten()
            .filter { it.globalSeq >= globalSeq }
            .sortedBy { it.globalSeq }
            .take(limit)
    }

    override fun count(stream: String): Long {
        return streams[stream]?.size?.toLong() ?: 0L
    }

    override fun globalSeq(): Long {
        return globalSeq.get()
    }

    /**
     * Get all streams.
     */
    public fun streams(): Set<String> {
        return streams.keys.toSet()
    }

    /**
     * Get stream size.
     */
    public fun streamSize(stream: String): Int {
        return streams[stream]?.size ?: 0
    }

    /**
     * Get total events across all streams.
     */
    public fun totalEvents(): Long {
        return streams.values.sumOf { it.size }.toLong()
    }

    /**
     * Get current global sequence.
     */
    public fun currentGlobalSeq(): Long {
        return globalSeq.get()
    }

    /**
     * Clear all data.
     */
    public fun clear() {
        streams.clear()
        globalSeq.set(initialGlobalSeq)
    }
}
