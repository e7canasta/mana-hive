package com.manahive.runtime

import java.time.Duration
import java.time.Instant

/**
 * Receives time control commands from any source (NATS, HTTP, test).
 *
 * Supports switching between ManualClock (testing) and SystemClock (production).
 */
interface TimeSink {
    fun advanceTime(duration: Duration)
    fun setTime(instant: Instant)
    fun useManual(startAt: Instant)
    fun useSystem()
}
