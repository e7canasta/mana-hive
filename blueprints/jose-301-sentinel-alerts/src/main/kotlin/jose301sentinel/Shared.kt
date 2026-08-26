package jose301sentinel

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.bdd.SentinelContext
import com.manahive.sentinel.sentinelCalibration
import java.time.Instant

// ── Constants ───────────────────────────────────────────────────────────────

val BED_4 = BedId("bed-4")
val NIGHT = NightId("night-jose-301")
val JOSE = ResidentId("jose")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── BDD Context ─────────────────────────────────────────────────────────────

fun sentinelCtx(cal: SentinelCalibration) = SentinelContext(
    bed = BED_4,
    resident = JOSE,
    night = NIGHT,
    calibration = cal,
    start = START,
)

// ── Calibrations ────────────────────────────────────────────────────────────

val calBasica = sentinelCalibration {
    resident("jose")

    rule("r-sitting") {
        trigger = StateKind.SITTING_IN_BED
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
    }

    rule("r-standing") {
        trigger = StateKind.STANDING
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
        umbrellaEvents(StateKind.SITTING_IN_BED)
    }
}

val calCritica = sentinelCalibration {
    resident("jose")

    rule("r-bed-edge") {
        trigger = StateKind.BED_EDGE
        severity = com.manahive.contracts.policy.Severity.CRITICAL
        closureCondition = com.manahive.contracts.policy.ClosureCondition.STAFF_AND_SAFE
        reversible = false
        requiresNvr = true
        umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
    }

    rule("r-standing") {
        trigger = StateKind.STANDING
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
        umbrellaEvents(StateKind.SITTING_IN_BED)
    }
}
