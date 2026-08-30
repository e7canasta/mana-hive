package com.manahive.kernel

import java.time.Instant
import java.time.Duration

/**
 * Abstraction over time — the "chronos" of the system.
 *
 * Production: [SystemClock] delegates to Instant.now().
 * Tests / examples: [ManualClock] gives you god-like control over time.
 *
 * Vernon: "the only time a clock should ask the system for the time is at
 * the adapter boundary." This interface IS that boundary.
 */
public interface Clock {
    public fun instant(): Instant
}

/**
 * Real-world clock. Delegates to the JVM.
 */
public object SystemClock : Clock {
    override fun instant(): Instant = Instant.now()
}

/**
 * Test clock. You advance time manually — the chronos god of time.
 *
 * Usage:
 *   val clock = ManualClock(Instant.parse("2024-01-15T23:00:00Z"))
 *   clock.instant()   // → 23:00
 *   clock.advance(Duration.ofMinutes(12))
 *   clock.instant()   // → 23:12
 */
public class ManualClock(private var current: Instant) : Clock {
    override fun instant(): Instant = current

    public fun advance(d: Duration) { current += d }
    public fun setTo(t: Instant) { current = t }
}
