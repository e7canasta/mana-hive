package com.manahive.hub.policy

import com.manahive.kernel.ResidentId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory implementation of [PolicyLayerStore] — now explicitly a **CACHE** derived from mana-hub SOR.
 *
 * Stores the event history per resident and folds it to [PolicyLayers].
 * **No es SOR:** la verdad vive en mana-hub Postgres `alarm_profile_versions` + `hub_policy_outbox`.
 * Al arrancar se hidrata via `hub.policy.change.v1` (NATS) o `GET /api/v1/alarm-presets/{id}` snapshot.
 * Pierde todo al reiniciar y se reconstruye — como `current_bed_states` o `EpisodeLedger` en NightWatch.
 * Para tests/dev sigue siendo útil; en prod es PolicyCache.
 *
 * Vernon: "Infrastructure" — implementation detail behind the port.
 */
public class InMemoryPolicyLayerStore : PolicyLayerStore {

    private val events = ConcurrentHashMap<ResidentId, CopyOnWriteArrayList<PolicyEvent>>()

    override fun layersFor(residentId: ResidentId, at: Instant): PolicyLayers? {
        val residentEvents = events[residentId] ?: return null
        if (residentEvents.isEmpty()) return null
        return foldPolicyLayers(residentEvents.filter { it.at <= at })
    }

    override fun eventsFor(residentId: ResidentId): List<PolicyEvent> =
        events[residentId]?.toList() ?: emptyList()

    override fun applyEvent(event: PolicyEvent) {
        val list = events.computeIfAbsent(event.residentId) { CopyOnWriteArrayList() }
        list.add(event)
    }

    /**
     * Number of residents with policy events.
     * Internal: test helper only.
     */
    internal fun size(): Int = events.size

    /**
     * Clear all stored events.
     * Internal: test helper only.
     */
    internal fun clear() {
        events.clear()
    }
}

/** Alias explícito: deja claro que es caché de trabajo, no disco — usar este nombre en prod */
public typealias PolicyCache = InMemoryPolicyLayerStore
