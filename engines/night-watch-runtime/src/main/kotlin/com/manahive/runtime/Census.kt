package com.manahive.runtime

import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps beds to residents. The single source of truth for "who sleeps where."
 *
 * In production this comes from the housing census. For the runtime, it's
 * the registry that tells the ingest layer which [ResidentRuntime] to route
 * an observation to.
 *
 * Vernon: "Lookup Service" — small, focused, thread-safe.
 */
class Census {
    private val entries = ConcurrentHashMap<BedId, CensusEntry>()

    fun register(
        bed: BedId,
        resident: ResidentId,
        night: NightId,
        monitor: MonitorId,
    ) {
        entries[bed] = CensusEntry(resident, night, monitor)
    }

    fun unregister(bed: BedId) {
        entries.remove(bed)
    }

    fun lookup(bed: BedId): CensusEntry? = entries[bed]

    /**
     * Dónde está alojado un residente.
     *
     * El censo indexa por cama porque las observaciones llegan por cama, pero la
     * política llega por residente: sin esta vuelta, un alta no sabe en qué cama
     * dar de alta el runtime.
     */
    fun bedFor(resident: ResidentId): Placement? =
        entries.entries.firstOrNull { it.value.resident == resident }
            ?.let { Placement(it.key, it.value.night, it.value.monitor) }

    val size: Int get() = entries.size

    fun residentIds(): List<ResidentId> = entries.values.map { it.resident }.distinct()

    fun entries(): Map<BedId, CensusEntry> = entries.toMap()
}

data class CensusEntry(
    val resident: ResidentId,
    val night: NightId,
    val monitor: MonitorId,
)

/** Cama, noche y cámara de un residente. */
data class Placement(
    val bed: BedId,
    val night: NightId,
    val monitor: MonitorId,
)
