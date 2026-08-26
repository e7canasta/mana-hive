package com.manahive.hub.policy

import com.manahive.kernel.ResidentId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory implementation of [PolicyLayerStore].
 *
 * Stores the event history per resident and folds it to [PolicyLayers].
 * Intended for development, testing, and single-instance deployments.
 * For production, replace with an event-sourced implementation backed by
 * the ledger.
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
