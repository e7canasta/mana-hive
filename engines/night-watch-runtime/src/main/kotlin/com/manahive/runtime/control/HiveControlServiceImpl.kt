package com.manahive.runtime.control

import com.manahive.kernel.BedId
import com.manahive.kernel.ResidentId
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Service implementation — full cold reload with control notification.
 * Fowler: Service Layer orchestrates domain, publishes Domain Event.
 * Thread-safe: delegates to synchronized calibrator/runtime.
 */
class HiveControlServiceImpl(
    private val calibrator: ProfileCalibrator,
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val profileFetcher: ProfileFetcher,
    private val controlPublisher: ControlEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) : HiveControlService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun reload(cmd: HiveCommand): HiveControlEvent {
        val rid = requireNotNull(cmd.residentId?.let { ResidentId(it) }) { "residentId required for reload" }
        val bedId = cmd.bedId?.let { BedId(it) }

        val oldVersion = calibrator.current(rid)?.version
        val oldFp = calibrator.fingerprint(rid)
        log.info("HiveControl reload {} bed={} oldVersion={} oldFp={} -> cold fetch", rid.value, bedId?.value ?: "*", oldVersion, oldFp)

        // Cold fetch first (before evict) — if hub deleted profile, fallback to resetFull with current calibrations
        val dto = try {
            profileFetcher.fetch(rid)
        } catch (e: Exception) {
            log.warn("HiveControl reload fetch failed for {}: {} -> fallback resetFull", rid.value, e.message)
            runtime.get(rid)?.let { rt -> synchronized(rt) { rt.resetFull() } }
            val evFallback = HiveControlEvent(
                type = "HiveResetFullFallback",
                residentId = rid.value,
                bedId = bedId?.value ?: census.bedFor(rid)?.bed?.value ?: "unknown",
                at = clock.instant(),
                oldVersion = oldVersion,
                newVersion = oldVersion,
                fingerprint = oldFp,
                twinState = "Unknown(SIGNAL_LOST)",
                message = "fetch failed, fallback resetFull",
            )
            controlPublisher.publish(evFallback)
            log.info("HiveControl fallback resetFull for {} with oldCals {}", rid.value, oldFp)
            return evFallback
        }

        // Fetch ok -> full evict then accept
        val removed = evictFully(rid, bedId)
        log.info("HiveControl evicted {} (runtime removed={})", rid.value, removed)

        val accepted = calibrator.accept(dto)
        val newVersion = if (accepted) dto.version else calibrator.current(rid)?.version
        val newFp = calibrator.fingerprint(rid)
        val twinState = runtime.get(rid)?.let { rt ->
            // access via reflection of twin state kind if needed, fallback to simple
            try {
                val f = rt.javaClass.getDeclaredField("twin")
                f.isAccessible = true
                val twin = f.get(rt)
                twin.toString().take(120)
            } catch (_: Exception) { "recreated" }
        } ?: "recreated"

        val ev = HiveControlEvent(
            type = "HiveReloaded",
            residentId = rid.value,
            bedId = dto.residentId.let { census.bedFor(rid)?.bed?.value ?: bedId?.value ?: "unknown" },
            at = clock.instant(),
            oldVersion = oldVersion,
            newVersion = newVersion,
            fingerprint = newFp,
            twinState = twinState,
            message = if (accepted) "full cold reload ok" else "calibrator rejected",
        )
        log.info("HiveControl reloaded {} {}->{} fp={} twin={} accepted={}", rid.value, oldVersion, newVersion, newFp, twinState, accepted)
        controlPublisher.publish(ev)
        return ev
    }

    override fun reset(cmd: HiveCommand): HiveControlEvent {
        val rid = cmd.residentId?.let { ResidentId(it) }
        val bed = cmd.bedId?.let { BedId(it) }
        var count = 0
        if (rid != null) {
            runtime.get(rid)?.let { rt -> synchronized(rt) { rt.reset() }; count = 1 }
        } else if (bed != null) {
            runtime.residents().forEach { (_, rt) -> if (rt.bed == bed) { synchronized(rt) { rt.reset() }; count++ } }
        } else {
            runtime.residents().forEach { (_, rt) -> synchronized(rt) { rt.reset() }; count++ }
        }
        val ev = HiveControlEvent(
            type = "HiveReset",
            residentId = rid?.value ?: "*",
            bedId = bed?.value ?: "*",
            at = clock.instant(),
            oldVersion = rid?.let { calibrator.current(it)?.version },
            newVersion = rid?.let { calibrator.current(it)?.version },
            fingerprint = rid?.let { calibrator.fingerprint(it) },
            twinState = "partial reset",
            message = "reset $count runtime(s) partial (kept twin)",
        )
        log.info("HiveControl reset partial count={} {}", count, ev)
        controlPublisher.publish(ev)
        return ev
    }

    override fun resetFull(cmd: HiveCommand): HiveControlEvent {
        val rid = requireNotNull(cmd.residentId?.let { ResidentId(it) }) { "residentId required" }
        val bedId = cmd.bedId?.let { BedId(it) } ?: census.bedFor(rid)?.bed
        val oldVersion = calibrator.current(rid)?.version
        val oldFp = calibrator.fingerprint(rid)
        // Fowler: Aggregate recreation via domain method, not unregister/register dance
        runtime.get(rid)?.let { rt -> synchronized(rt) { rt.resetFull() } }
        val ev = HiveControlEvent(
            type = "HiveResetFull",
            residentId = rid.value,
            bedId = bedId?.value ?: "unknown",
            at = clock.instant(),
            oldVersion = oldVersion,
            newVersion = oldVersion,
            fingerprint = calibrator.fingerprint(rid) ?: oldFp,
            twinState = "Unknown(SIGNAL_LOST)",
            message = "full twin+ledger reset",
        )
        log.info("HiveControl resetFull {} twin->Unknown oldFp={}", rid.value, oldFp)
        controlPublisher.publish(ev)
        return ev
    }

    private fun evictFully(rid: ResidentId, bedId: BedId?): Boolean {
        // Remove from runtime first (holds twin+ledger)
        var removed = false
        runtime.get(rid)?.let {
            runtime.unregister(rid)
            removed = true
        }
        // Fallback bed match
        if (!removed && bedId != null) {
            runtime.residents().forEach { (id, rt) -> if (rt.bed == bedId) { runtime.unregister(id); removed = true } }
        }
        calibrator.evict(rid)
        return removed
    }
}
