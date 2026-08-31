package com.manahive.runtime.control

/**
 * Port outbound — publishes control events to NATS.
 * Fowler: Gateway.
 */
interface ControlEventPublisher {
    fun publish(event: HiveControlEvent)
}
