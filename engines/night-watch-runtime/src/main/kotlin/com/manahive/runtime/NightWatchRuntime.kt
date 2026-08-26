package com.manahive.runtime

import com.manahive.contracts.perception.Observation
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of all active resident runtimes.
 *
 * One runtime per resident. Residents are added via [register] and
 * removed via [unregister]. The sweep tick goes through [tickAll].
 *
 * ConcurrentHashMap.compute gives us per-resident locking for free:
 * observation and tick for the same resident are serialized, but
 * different residents process in parallel.
 */
class NightWatchRuntime {
    private val log = LoggerFactory.getLogger(javaClass)
    private val runtimes = ConcurrentHashMap<ResidentId, ResidentRuntime>()

    val size: Int get() = runtimes.size

    fun register(
        residentId: ResidentId,
        bed: BedId,
        night: NightId,
        monitor: MonitorId,
        calibrations: EngineCalibrations,
    ): ResidentRuntime {
        val rt = ResidentRuntime(residentId, bed, night, monitor, calibrations)
        runtimes[residentId] = rt
        log.info("Registered runtime for resident {} on bed {}", residentId.value, bed.value)
        return rt
    }

    fun unregister(residentId: ResidentId) {
        runtimes.remove(residentId)
        log.info("Unregistered runtime for resident {}", residentId.value)
    }

    fun get(residentId: ResidentId): ResidentRuntime? = runtimes[residentId]

    /** Vista de sólo lectura de quiénes están bajo vigilancia. */
    fun residents(): Map<ResidentId, ResidentRuntime> = runtimes.toMap()

    /**
     * Process an observation for a specific resident.
     * Thread-safe: uses ConcurrentHashMap.compute for per-resident locking.
     */
    fun onObservation(residentId: ResidentId, obs: Observation): Outbound {
        val rt = runtimes[residentId]
            ?: error("No runtime registered for resident ${residentId.value}")
        // For 1-4 residents, synchronized is fine. No contention.
        synchronized(rt) {
            return rt.onObservation(obs)
        }
    }

    /**
     * Tick all runtimes (sweep). Uses wall clock time.
     * Each resident is ticked under its own lock.
     */
    fun tickAll(now: Instant): Map<ResidentId, Outbound> {
        val results = mutableMapOf<ResidentId, Outbound>()
        for ((id, rt) in runtimes) {
            synchronized(rt) {
                results[id] = rt.onTick(now)
            }
        }
        return results
    }

    fun recalibrate(residentId: ResidentId, calibrations: EngineCalibrations) {
        val rt = runtimes[residentId]
            ?: error("No runtime registered for resident ${residentId.value}")
        synchronized(rt) {
            rt.recalibrate(calibrations)
        }
        log.info("Recalibrated runtime for resident {}", residentId.value)
    }
}
