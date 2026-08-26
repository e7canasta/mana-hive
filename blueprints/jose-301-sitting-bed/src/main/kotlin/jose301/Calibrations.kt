package jose301

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.policy.buildResidentProfile
import com.manahive.contracts.scene.StateKind
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.scene.calibration.sceneCalibration
import com.manahive.scene.core.TransitionTable
import java.time.Duration

// ── Config A: comeBack 12/15, sin dwell ─────────────────────────────────────

val configBasica = sceneCalibration {
    table = TransitionTable.RELEASE_2
    confidence { StateKind.SITTING_IN_BED min 0.8 }
    comeBack {
        LYING warning Duration.ofMinutes(12) exceeded Duration.ofMinutes(15)
    }
    heartbeatTimeout = Duration.ofSeconds(90)
}

// ── Config B: comeBack 20/25 + dwell SITTING_IN_BED 10/15 ──────────────────

val configConDwell = sceneCalibration {
    table = TransitionTable.RELEASE_2
    confidence { StateKind.SITTING_IN_BED min 0.8 }
    dwell {
        SITTING_IN_BED warning Duration.ofMinutes(10) exceeded Duration.ofMinutes(15)
    }
    comeBack {
        LYING warning Duration.ofMinutes(20) exceeded Duration.ofMinutes(25)
    }
    heartbeatTimeout = Duration.ofSeconds(90)
}

// ── Config via Policy: DAG catalog + profile → PolicyResolver → Scene ───────
// SPEC-05: ComeBack configured at catalog level, resolved through Politica Engine.

val catalogComeBack = buildDagCatalog {
    resident {
        comeBackTo(StateKind.LYING) {
            warningAfter(Duration.ofMinutes(12))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
    }
}

val profileJose = buildResidentProfile("jose") {
    risk(RiskLevel.LOW)
    mobility(MobilityAid.NONE)
    level(WatchLevel.STANDARD)
}

// El adapter ya arma la tabla desde la hysteresis del catalogo; el .copy() que
// habia aca la pisaba con RELEASE_2 y tapaba que el adapter la estaba perdiendo.
val configViaPolicy = PolicyResolver.resolve(catalogComeBack, profileJose.profile).value
    .toSceneCalibration()

// ── Config via Policy: comeBack 20/25 + dwell 10/15 ─────────────────────────

val catalogComeBackAndDwell = buildDagCatalog {
    resident {
        sitting {
            warningAfter(Duration.ofMinutes(10))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        comeBackTo(StateKind.LYING) {
            warningAfter(Duration.ofMinutes(20))
            alertAfter(Duration.ofMinutes(25))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
    }
}

val configViaPolicyConDwell = PolicyResolver.resolve(catalogComeBackAndDwell, profileJose.profile).value
    .toSceneCalibration()
