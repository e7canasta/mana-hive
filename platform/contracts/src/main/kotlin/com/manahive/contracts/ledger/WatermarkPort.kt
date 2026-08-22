package com.manahive.contracts.ledger

/**
 * Watermarks: a consumer's effect and its mark advance in ONE transaction.
 */
public interface WatermarkPort {
    public fun of(consumer: String): Long
    public fun advance(consumer: String, to: Long)
}
