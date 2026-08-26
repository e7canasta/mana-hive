package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * NIVEL 0: STANDARD — baseline, solo observación, sin alertas.
 * "No hagas nada, solo registra qué pasó"
 */
public val STANDARD_CATALOG: DagCatalog = buildDagCatalog {
    version("2.1.0")
    resident {
        lying { }
        sitting { }
        bedEdge { }
        standing { }
        bathroom { }
        absent { }
    }
    room { }
    transitions {
        from(StateKind.LYING) {
            to(StateKind.SITTING_IN_BED) { hysteresis(Duration.ofMillis(1500)) }
            to(StateKind.BED_EDGE) { hysteresis(Duration.ofMillis(1500)) }
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(2000)) }
        }
        from(StateKind.SITTING_IN_BED) {
            to(StateKind.LYING) { hysteresis(Duration.ofMillis(1000)) }
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(1500)) }
        }
        from(StateKind.BED_EDGE) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(1500)) }
            to(StateKind.LYING) { hysteresis(Duration.ofMillis(1000)) }
        }
        from(StateKind.STANDING) {
            to(StateKind.IN_BATHROOM) { hysteresis(Duration.ofMillis(2000)) }
            to(StateKind.IN_ROOM) { hysteresis(Duration.ofMillis(2000)) }
            to(StateKind.ABSENT) { hysteresis(Duration.ofMillis(3000)) }
        }
        from(StateKind.IN_BATHROOM) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(2000)) }
            to(StateKind.IN_ROOM) { hysteresis(Duration.ofMillis(2000)) }
        }
        from(StateKind.IN_ROOM) {
            to(StateKind.LYING) { hysteresis(Duration.ofMillis(3000)) }
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(2000)) }
            to(StateKind.IN_BATHROOM) { hysteresis(Duration.ofMillis(2000)) }
        }
    }
}

/**
 * NIVEL 1: NIGHT-WANDERING — alertas básicas para nocturno.
 * "Si se despierta de noche, avísenme"
 */
public val NIGHT_WANDERING_CATALOG: DagCatalog = buildDagCatalog {
    version("2.1.0")
    resident {
        lying { }
        sitting {
            warningAfter(Duration.ofMinutes(20))
            alertAfter(Duration.ofMinutes(30))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
        bedEdge {
            warningAfter(Duration.ofMinutes(3))
            alertAfter(Duration.ofMinutes(5))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
        standing {
            warningAfter(Duration.ofMinutes(10))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        bathroom {
            warningAfter(Duration.ofMinutes(15))
            alertAfter(Duration.ofMinutes(25))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        absent {
            warningAfter(Duration.ofMinutes(5))
            alertAfter(Duration.ofMinutes(10))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
    }
    room {
        staffEnters { closeEpisode() }
    }
    transitions {
        from(StateKind.LYING) {
            to(StateKind.SITTING_IN_BED) { hysteresis(Duration.ofMillis(1000)) }
            to(StateKind.BED_EDGE) { hysteresis(Duration.ofMillis(1000)) }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(1000))
                record(before = Duration.ofMinutes(2), after = Duration.ofMinutes(5))
            }
        }
        from(StateKind.SITTING_IN_BED) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(1000)) }
        }
        from(StateKind.STANDING) {
            to(StateKind.IN_BATHROOM) { hysteresis(Duration.ofMillis(1000)) }
            to(StateKind.ABSENT) { hysteresis(Duration.ofMillis(2000)) }
        }
    }
}

/**
 * NIVEL 2: FALL-RISK — alertas intensivas para riesgo de caída.
 * "Si se mueve, avísenme rápido"
 */
public val FALL_RISK_CATALOG: DagCatalog = buildDagCatalog {
    version("2.1.0")
    resident {
        lying { }
        sitting {
            warningAfter(Duration.ofMinutes(15))
            alertAfter(Duration.ofMinutes(20))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
        bedEdge {
            warningAfter(Duration.ofMinutes(1))
            alertAfter(Duration.ofMinutes(2))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
        standing {
            warningAfter(Duration.ofMinutes(2))
            alertAfter(Duration.ofMinutes(3))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        bathroom {
            warningAfter(Duration.ofMinutes(10))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.WARNING)
            closure(ClosureCondition.SAFE_ONLY)
        }
        absent {
            warningAfter(Duration.ofMinutes(5))
            alertAfter(Duration.ofMinutes(10))
            severity(Severity.WARNING)
            closure(ClosureCondition.STAFF_OR_SAFE)
        }
    }
    room {
        staffEnters { closeEpisode() }
    }
    transitions {
        from(StateKind.LYING) {
            to(StateKind.SITTING_IN_BED) { hysteresis(Duration.ofMillis(2000)) }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(3000))
                record(before = Duration.ofMinutes(2), after = Duration.ofMinutes(5))
            }
        }
        from(StateKind.SITTING_IN_BED) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(2000)) }
        }
    }
}

/**
 * NIVEL 3: CRITICAL — alertas inmediatas para residentes críticos.
 * "Alerta inmediata en cualquier movimiento"
 */
public val CRITICAL_CATALOG: DagCatalog = buildDagCatalog {
    version("2.1.0")
    resident {
        lying { }
        sitting {
            warningAfter(Duration.ofMinutes(10))
            alertAfter(Duration.ofMinutes(15))
            severity(Severity.CRITICAL)
            closure(ClosureCondition.STAFF_AND_SAFE)
        }
        bedEdge {
            warningAfter(Duration.ofMinutes(1))
            alertAfter(Duration.ofMinutes(2))
            severity(Severity.CRITICAL)
            closure(ClosureCondition.STAFF_AND_SAFE)
        }
        standing {
            warningAfter(Duration.ofMinutes(2))
            alertAfter(Duration.ofMinutes(3))
            severity(Severity.CRITICAL)
            closure(ClosureCondition.STAFF_AND_SAFE)
        }
        bathroom {
            warningAfter(Duration.ofMinutes(5))
            alertAfter(Duration.ofMinutes(10))
            severity(Severity.CRITICAL)
            closure(ClosureCondition.STAFF_AND_SAFE)
        }
        absent {
            warningAfter(Duration.ofMinutes(2))
            alertAfter(Duration.ofMinutes(5))
            severity(Severity.CRITICAL)
            closure(ClosureCondition.STAFF_AND_SAFE)
        }
    }
    room {
        staffEnters { closeEpisode() }
    }
    transitions {
        from(StateKind.LYING) {
            to(StateKind.SITTING_IN_BED) { hysteresis(Duration.ofMillis(1000)) }
            to(StateKind.STANDING) {
                hysteresis(Duration.ofMillis(1000))
                record(before = Duration.ofMinutes(5), after = Duration.ofMinutes(10))
            }
        }
        from(StateKind.SITTING_IN_BED) {
            to(StateKind.STANDING) { hysteresis(Duration.ofMillis(1000)) }
        }
    }
}

/**
 * Los cuatro niveles del director, indexados por [WatchLevel].
 *
 * Es el índice que traduce la decisión clínica ("José es riesgo de caída") al
 * catálogo que fija sus tiempos. Un nivel sin catálogo es un residente sin
 * reglas, así que la cobertura total está cubierta por test.
 */
public val CATALOG_BY_LEVEL: Map<WatchLevel, DagCatalog> = mapOf(
    WatchLevel.STANDARD to STANDARD_CATALOG,
    WatchLevel.NIGHT_WANDERING to NIGHT_WANDERING_CATALOG,
    WatchLevel.FALL_RISK to FALL_RISK_CATALOG,
    WatchLevel.CRITICAL to CRITICAL_CATALOG,
)

/** El catálogo del nivel. Nunca null: [CATALOG_BY_LEVEL] cubre el enum entero. */
public fun catalogFor(level: WatchLevel): DagCatalog =
    CATALOG_BY_LEVEL.getValue(level)
