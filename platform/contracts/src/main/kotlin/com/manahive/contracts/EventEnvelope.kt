package com.manahive.contracts

import java.time.Instant

/**
 * Wire envelope for every event on the bus. `eventId` is the end-to-end
 * idempotency key (doubles as Nats-Msg-Id). Payload schemas are versioned in
 * resources/schemas; emitted events are API forever — incompatible change
 * means a new type, never a mutation.
 */
public data class EventEnvelope(
    public val eventId: String,
    public val type: String,
    public val version: Int,
    public val occurredAt: Instant,
    public val source: String,
    public val payloadJson: String,
)
