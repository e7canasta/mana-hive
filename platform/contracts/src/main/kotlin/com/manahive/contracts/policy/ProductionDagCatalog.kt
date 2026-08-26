package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Production catalog: DAG-centric rules for all residents.
 *
 * This is the "bible" of monitoring rules. Loaded at application startup.
 * The director selects a template; the Politica Engine resolves it.
 *
 * Vernon: "Shared Kernel" — this catalog is shared across all engines.
 * Fowler: "Intention-Revealing Interfaces" — rules speak the domain's language.
 */
public val PRODUCTION_DAG_CATALOG: DagCatalog = buildDagCatalog {
    version("2.1.0")

    resident {
        lying {
            // "If resident lies for 30 minutes, it's normal — no alert"
            // (lying is the safe state, no alert needed)
        }

        sitting {
            warningAfter(Duration.ofMinutes(30))
            alertAfter(Duration.ofMinutes(45))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }

        bedEdge {
            warningAfter(Duration.ofMinutes(5))
            alertAfter(Duration.ofMinutes(10))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }

        standing {
            warningAfter(Duration.ofMinutes(15))
            alertAfter(Duration.ofMinutes(20))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }

        bathroom {
            warningAfter(Duration.ofMinutes(20))
            alertAfter(Duration.ofMinutes(30))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }

        absent {
            warningAfter(Duration.ofMinutes(10))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
    }

    room {
        staffEnters {
            closeEpisode()  // "When staff enters, close the episode"
        }

        staffLeaves {
            // "When staff leaves, re-evaluate but don't open new episode"
        }
    }

    transitions {
        from(StateKind.LYING) {
            to(StateKind.SITTING_IN_BED) {
                hysteresis(Duration.ofMillis(1500))
            }
            to(StateKind.BED_EDGE) {
                hysteresis(Duration.ofMillis(1500))
            }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(2000))
                record(before = Duration.ofMinutes(2), after = Duration.ofMinutes(5))
            }
        }

        from(StateKind.SITTING_IN_BED) {
            to(StateKind.LYING) {
                hysteresis(Duration.ofMillis(1000))
            }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(1500))
            }
        }

        from(StateKind.BED_EDGE) {
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(1500))
            }
            to(StateKind.LYING) {
                hysteresis(Duration.ofMillis(1000))
            }
        }

        from(StateKind.STANDING) {
            to(StateKind.IN_BATHROOM) {
                hysteresis(Duration.ofMillis(2000))
            }
            to(StateKind.IN_ROOM) {
                hysteresis(Duration.ofMillis(2000))
            }
            to(StateKind.ABSENT) {
                hysteresis(Duration.ofMillis(3000))
            }
        }

        from(StateKind.IN_BATHROOM) {
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(2000))
            }
            to(StateKind.IN_ROOM) {
                hysteresis(Duration.ofMillis(2000))
            }
        }

        from(StateKind.IN_ROOM) {
            to(StateKind.LYING) {
                hysteresis(Duration.ofMillis(3000))
            }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(2000))
            }
            to(StateKind.IN_BATHROOM) {
                hysteresis(Duration.ofMillis(2000))
            }
        }
    }
}
