package com.manahive.hub.policy

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import java.time.Instant
import java.time.LocalTime

// ── Domain events ──────────────────────────────────────────────────────────
//
// Cada cambio de política es historia clínica, no un UPDATE. Los cuatro
// eventos cubren las cuatro capas de PolicyLayers. `motivo` es texto libre
// y es obligatorio en los tres que representan una decisión humana.
//
// Vernon: "Event Sourcing" — el estado vigente es un fold, no una tabla.

/**
 * Evento: se asigna un nivel de vigilancia a un residente.
 *
 * Es la decisión de mayor alcance — el nivel determina qué catálogo de
 * reglas se aplica. Un cambio de nivel es un cambio de interpreter completo.
 */
public data class WatchLevelAssigned(
    override val residentId: ResidentId,
    public val level: WatchLevel,
    public val actor: StaffId,
    override val at: Instant,
    public val reason: String,
) : PolicyEvent {
    init {
        require(reason.isNotBlank()) {
            "WatchLevelAssigned has no reason — a level change nobody can explain"
        }
    }
}

/**
 * Evento: se agrega un ajuste manual sobre un estado concreto.
 *
 * En el idioma del director: *"Para José, sentado, avisen a los quince minutos."*
 *
 * El [adjustmentId] es estable: se usa para referenciarlo en un eventual
 * [ManualAdjustmentRevoked].
 */
public data class ManualAdjustmentAdded(
    override val residentId: ResidentId,
    public val adjustmentId: String,
    public val state: StateKind,
    public val threshold: DwellThreshold,
    public val actor: StaffId,
    override val at: Instant,
    public val reason: String,
) : PolicyEvent {
    init {
        require(reason.isNotBlank()) {
            "ManualAdjustmentAdded has no reason — a threshold change nobody can explain"
        }
    }
}

/**
 * Evento: se revoca un ajuste manual previo.
 *
 * No lleva motivo porque la revocación restaura el estado anterior — el
 * motivo de la revocación queda en eljuicio que la originó, no en este
 * evento de infraestructura.
 */
public data class ManualAdjustmentRevoked(
    override val residentId: ResidentId,
    public val adjustmentId: String,
    public val actor: StaffId,
    override val at: Instant,
) : PolicyEvent

/**
 * Evento: se define (o redefine) una ventana horaria para un residente.
 *
 * Si ya existe una ventana con el mismo [windowId], se reemplaza. Las
 * ventanas que cruzan medianoche son el caso normal — la noche es el turno
 * que este sistema vigila.
 */
public data class TimeWindowDefined(
    override val residentId: ResidentId,
    public val windowId: String,
    public val from: LocalTime,
    public val to: LocalTime,
    public val adjustments: List<StateAdjustment>,
    public val actor: StaffId,
    override val at: Instant,
) : PolicyEvent

// ── Fold ───────────────────────────────────────────────────────────────────
//
// Reconstruye PolicyLayers desde una secuencia de eventos para un residente.
// Función pura: mismos eventos → mismo resultado. Sin I/O.
//
// El fold NO valida que exista un WatchLevelAssigned primero — si el stream
// está vacío o solo tiene ajustes, devuelve un nivel por defecto. La validación
// de que el residente tenga capas live en PolicyService.layersFor().

/**
 * Pliega una secuencia de eventos de política en [PolicyLayers].
 *
 * Los eventos deben llegar ordenados por [Instant] ascendente. El fold es
 * idempotente: repliar los mismos eventos produce las mismas capas.
 */
public fun foldPolicyLayers(events: List<PolicyEvent>): PolicyLayers {
    var level: WatchLevel = WatchLevel.STANDARD
    var template: LevelTemplate = LevelTemplate(id = "default", level = WatchLevel.STANDARD)
    val adjustments = mutableListOf<ManualAdjustment>()
    val windows = mutableListOf<TimeWindow>()

    for (event in events) {
        when (event) {
            is WatchLevelAssigned -> {
                level = event.level
                template = LevelTemplate(id = event.level.label, level = event.level)
            }

            is ManualAdjustmentAdded -> {
                adjustments.removeIf { it.id == event.adjustmentId }
                adjustments += ManualAdjustment(
                    id = event.adjustmentId,
                    state = event.state,
                    threshold = event.threshold,
                    actor = event.actor,
                    at = event.at,
                    reason = event.reason,
                )
            }

            is ManualAdjustmentRevoked -> {
                adjustments.removeIf { it.id == event.adjustmentId }
            }

            is TimeWindowDefined -> {
                windows.removeIf { it.id == event.windowId }
                windows += TimeWindow(
                    id = event.windowId,
                    from = event.from,
                    to = event.to,
                    adjustments = event.adjustments,
                )
            }
        }
    }

    return PolicyLayers(
        level = level,
        template = template,
        adjustments = adjustments,
        windows = windows,
    )
}

/** Interfaz sellada para los cuatro eventos de política. */
public sealed interface PolicyEvent {
    public val residentId: ResidentId
    public val at: Instant
}
