package com.manahive.contracts.policy

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.StaffId
import java.time.Instant
import java.time.LocalTime

/**
 * Las capas de política de un residente, tal como el hub las almacena.
 *
 * El hub NO resuelve política — es System of Record. Guarda qué decidió cada
 * persona, cuándo y por qué, y proyecta esas capas a un perfil que Política
 * resuelve contra el catálogo del nivel. Ver `docs/adr/ADR-001`.
 *
 * Precedencia, de menor a mayor:
 *
 *     nivel → plantilla → ajuste manual → ventana horaria
 *
 * Cada cambio de estas capas es historia clínica, no un UPDATE: se escribe como
 * evento con su actor y su motivo. Lo que se guarda acá es el resultado de
 * plegar esa historia hasta un instante.
 */
public data class PolicyLayers(
    public val level: WatchLevel,
    public val template: LevelTemplate,
    public val adjustments: List<ManualAdjustment>,
    public val windows: List<TimeWindow>,
)

/**
 * La plantilla elegida para este residente.
 *
 * Guarda la **referencia**, no el contenido: los tiempos viven en el catálogo
 * del nivel, que está versionado. Copiarlos acá crearía una segunda fuente de
 * verdad que se desincroniza en silencio.
 */
public data class LevelTemplate(
    public val id: String,
    public val level: WatchLevel,
)

/**
 * Un ajuste firmado sobre un estado concreto.
 *
 * En el idioma del director: *"Para José, sentado, avisen a los quince minutos."*
 *
 * Lleva actor y motivo porque es lo que hace contestable la pregunta del
 * inspector — de dónde salió ese número y quién lo puso. [reason] no es
 * decorativo: un cambio de vigilancia sin motivo es un cambio que nadie puede
 * explicar seis meses después.
 */
public data class ManualAdjustment(
    public val id: String,
    public val state: StateKind,
    public val threshold: DwellThreshold,
    public val actor: StaffId,
    public val at: Instant,
    public val reason: String,
) {
    init {
        require(reason.isNotBlank()) {
            "manual adjustment $id has no reason — a change of watch nobody can explain"
        }
    }
}

/**
 * Una ventana horaria que endurece reglas mientras está vigente.
 *
 * El caso típico es la noche, 22:00–07:00. Una ventana que cruza medianoche es
 * normal, no un caso raro: la noche es el turno que este sistema vigila.
 */
public data class TimeWindow(
    public val id: String,
    public val from: LocalTime,
    public val to: LocalTime,
    public val adjustments: List<StateAdjustment>,
)

/** Un umbral por estado, sin firma — la firma la lleva la ventana que lo contiene. */
public data class StateAdjustment(
    public val state: StateKind,
    public val threshold: DwellThreshold,
)
