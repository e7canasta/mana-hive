package jose301sentinel

import com.manahive.contracts.policy.TriggerOn
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
//
// Todas ENTRY: este blueprint sólo alimenta TransitionDetected y cada escenario
// afirma que el episodio abre EN la transición ("Sentarse abre episode WARNING").
// Sin declararlo tomaban el default DWELL del builder, buscaban una regla de
// entrada que no existía, y no abría ningún episodio — el blueprint crasheaba y
// nadie se enteraba porque `check` no corre blueprints.

val calBasica = sentinelCalibration {
    resident("jose")

    rule("r-sitting", StateKind.SITTING_IN_BED, TriggerOn.ENTRY) {
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
    }

    rule("r-standing", StateKind.STANDING, TriggerOn.ENTRY) {
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
        umbrellaEvents(StateKind.SITTING_IN_BED)
    }
}

val calCritica = sentinelCalibration {
    resident("jose")

    rule("r-bed-edge", StateKind.BED_EDGE, TriggerOn.ENTRY) {
        severity = com.manahive.contracts.policy.Severity.CRITICAL
        closureCondition = com.manahive.contracts.policy.ClosureCondition.STAFF_AND_SAFE
        reversible = false
        requiresNvr = true
        umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
    }

    rule("r-standing", StateKind.STANDING, TriggerOn.ENTRY) {
        severity = com.manahive.contracts.policy.Severity.WARNING
        closureCondition = com.manahive.contracts.policy.ClosureCondition.SAFE_ONLY
        reversible = true
        umbrellaEvents(StateKind.SITTING_IN_BED)
    }
}
