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
        // La caida no tiene tratamiento especial en el motor: es un estado mas,
        // y su gravedad se declara aca como la de cualquier otro. Viene puesta en
        // la plantilla para que ningun perfil nazca sin una respuesta a la caida
        // — pero nace editable, que es la diferencia entre un default y una
        // constante escondida en el codigo.
        onFloor {
            severity(Severity.CRITICAL)
            alertOnEntry()
            closure(ClosureCondition.STAFF_AND_SAFE)
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
        // Uno se cae desde cualquier posicion, asi que la caida es alcanzable
        // desde todas. Sin estas aristas el estado ON_FLOOR era inalcanzable: la
        // observacion llegaba y el interprete la descartaba por transicion
        // ilegal. Histeresis corta —una caida hay que creerla rapido— pero no
        // cero, para no morder ruido del sensor.
        from(StateKind.LYING) { to(StateKind.ON_FLOOR) { hysteresis(Duration.ofMillis(800)) } }
        from(StateKind.SITTING_IN_BED) { to(StateKind.ON_FLOOR) { hysteresis(Duration.ofMillis(800)) } }
        from(StateKind.BED_EDGE) { to(StateKind.ON_FLOOR) { hysteresis(Duration.ofMillis(800)) } }
        from(StateKind.STANDING) { to(StateKind.ON_FLOOR) { hysteresis(Duration.ofMillis(800)) } }
        // Y se sale del piso: alguien lo levanta o se levanta solo.
        from(StateKind.ON_FLOOR) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(1500)) }
            to(StateKind.LYING) { hysteresis(Duration.ofMillis(1500)) }
        }
    }
}
