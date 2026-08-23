package com.manahive.hub.ledger

import com.manahive.contracts.ledger.WatermarkPort
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory watermark store for tracking consumer progress.
 *
 * Fowler: "Single Responsibility" — watermark tracking is separate
 * from event storage.
 *
 * Vernon: "The watermark is a consumer's position in the event stream.
 * It advances atomically with the consumer's effect."
 */
public class InMemoryWatermarkStore : WatermarkPort {

    private val watermarks = ConcurrentHashMap<String, Long>()

    override fun of(consumer: String): Long {
        return watermarks[consumer] ?: 0L
    }

    override fun advance(consumer: String, to: Long) {
        watermarks[consumer] = to
    }

    /**
     * Clear all watermarks.
     */
    public fun clear() {
        watermarks.clear()
    }
}
