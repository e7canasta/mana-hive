package com.manahive.runtime

import com.manahive.contracts.perception.Observation

/**
 * Receives observations from any source (NATS, file, test).
 *
 * The runtime doesn't know where observations come from — this interface
 * is the single entry point for perception data.
 */
interface ObservationSink {
    fun onObservation(obs: Observation)
}
