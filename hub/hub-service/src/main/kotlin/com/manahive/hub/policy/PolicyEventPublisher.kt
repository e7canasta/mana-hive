package com.manahive.hub.policy

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Port for publishing policy change events to the bus.
 *
 * The hub writes policy changes and publishes a [PolicyChangeDetected] event.
 * Politica Engine consumes it, resolves rules, and publishes per-category
 * events (CalibrationChanged, ResponseChanged, etc.) to downstream engines.
 *
 * Vernon: "Driven port" — the hub pushes events out; the adapter (NATS)
 * implements the actual delivery.
 */
public fun interface PolicyEventPublisher {
    public fun publishPolicyChange(
        residentId: ResidentId,
        snapshot: AlarmProfile,
        at: Instant,
    )
}
