package jose301

import com.manahive.contracts.scene.StateKind
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
