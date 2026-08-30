package com.manahive.contracts.policy

import com.manahive.contracts.common.Channel
import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * The calibration that Politica Engine produces for all downstream engines.
 * This is the CONTRACT between engines — lives in platform/contracts.
 *
 * Vernon's ACL: this is the public interface of Politica engine.
 * Downstream engines only know resolved rules — NOT
 * RiskLevel, MobilityAid, AlarmProfile.
 *
 * Each engine's data is grouped into its own value object (Fowler: Extract Class).
 * The adapters extract the relevant section for each engine.
 */
public data class PolicyCalibration(
    public val residentId: ResidentId,
    public val scene: ScenePolicy,
    public val sentinel: SentinelPolicy,
    public val harbor: HarborPolicy,
    public val recorder: RecorderPolicy,
    /**
     * Huella de las reglas que produjeron esta calibración.
     *
     * Es lo que hace reproducible una decisión: un motor la cita en cada
     * veredicto, y con ella se puede volver a correr la noche y obtener lo
     * mismo. Nace acá, donde nace la decisión — calcularla más tarde, en el
     * borde, la desconecta de lo que realmente se resolvió.
     *
     * Dos catálogos de versión distinta deben dar huellas distintas aunque las
     * reglas resultantes coincidan: la pregunta del inspector es "con qué
     * reglas se decidió", no "qué reglas se parecen a éstas".
     */
    public val fingerprint: Fingerprint,
)

/**
 * Resolved rules for Scene Engine.
 * Contains dwell thresholds, come-back thresholds, hysteresis, and confidence filtering.
 */
public data class ScenePolicy(
    val hysteresis: Map<TransitionKey, Duration>,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
    val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
    val confidence: ConfidenceConfig,
    /**
     * Debounce por campo de escena, indexado por `sujeto.aspecto`.
     *
     * `SceneCalibration.sceneHysteresis` existe desde hace rato y
     * `SceneInterpreterImpl` ya lo consulta por campo — pero llegaba vacio
     * siempre, porque [PolicyCalibration] no tenia donde transportarlo. La
     * canieria estaba puesta y desconectada del tanque.
     */
    val sceneHysteresis: Map<String, Duration> = emptyMap(),
    /**
     * Permanencia por campo de escena, indexada por `sujeto.aspecto`.
     *
     * Es lo que hace calculable "la baranda lleva un minuto abajo". Sin esto, el
     * reloj por campo que se agrego en la fase 1 mide un tiempo que nadie
     * compara contra ningun umbral.
     */
    val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
)

/**
 * Resolved rules for Sentinel Engine.
 * Contains alert rules derived from ResidentStateRule in catalog,
 * and come-back rules derived from ComeBackRule.
 */
public data class SentinelPolicy(
    val alertRules: Map<StateKind, AlertRule>,
    val comeBackRules: Map<StateKind, AlertRule> = emptyMap(),
    /**
     * Reglas sobre campos de escena, indexadas por `sujeto.aspecto`.
     *
     * `SentinelCalibration.sceneStateRules` y su accessor `sceneStateRuleFor`
     * existen, pero las tres construcciones les pasaban `emptyMap()` y nadie los
     * llamaba: era un stub, no una via funcionando. Este es el transporte que
     * les faltaba para dejar de serlo.
     */
    val sceneStateRules: Map<String, SceneFieldRule> = emptyMap(),
    /**
     * Los estados cuya entrada **cierra** los episodios abiertos, como
     * `sujeto.aspecto.estado` — p.ej. `staff.presence.PRESENT`.
     *
     * Es lo que le da referente real a las condiciones de cierre que mencionan
     * al personal. Sin esto, `STAFF_AND_SAFE` es una promesa sin mecanismo: el
     * sistema dice "cierra cuando llegue el personal" y no tiene forma de
     * enterarse de que llego.
     *
     * Es un conjunto y no una bandera sobre el personal a proposito. Hoy el
     * unico que cierra es `staff.presence.PRESENT`, y eso estaba **cableado en
     * el motor**: cerraba porque el codigo decia que cerraba, no porque lo
     * dijera la politica. Si mañana el director decide que la baranda subida
     * tambien cierra, es una edicion del perfil y no un release.
     */
    val closingStates: Set<String> = emptySet(),
)

/**
 * Resolved rules for Harbor Engine.
 * Contains notification channels and escalation timeouts per severity.
 *
 * Channel is a shared type definition (like a C header) from contracts/common.
 * No circular dependency — harbor-domain already depends on contracts.
 */
public data class HarborPolicy(
    val defaultChannels: Map<Severity, Set<Channel>>,
    val escalationTimeouts: Map<Severity, Duration>,
)

/**
 * Resolved rules for Recorder Engine.
 * Contains recording windows for specific transitions.
 */
public data class RecorderPolicy(
    /** Recording windows keyed by transition (from → to). */
    val transitionWindows: Map<TransitionKey, TransitionWindow>,
    /**
     * Ventanas de video pedidas por una **regla**, indexadas por su id.
     *
     * Una regla puede pedir video sin que lo pida la transicion que la precede:
     * el borde de la cama de Elena graba al entrar, y la baranda baja podria
     * grabar sin que ninguna transicion de postura la acompañe. Antes solo
     * existian ventanas por transicion, asi que de una regla con `record`
     * sobrevivia `requiresNvr` —"hay que grabar"— y se perdia el cuanto y el
     * con que calidad, que es justamente la orden.
     *
     * Lleva [RecordWindow] y no [TransitionWindow] porque incluye la calidad, y
     * la calidad la decide el perfil. El adapter la deducia de la severidad.
     */
    val ruleWindows: Map<RuleId, RecordWindow> = emptyMap(),
)

/**
 * Recording window for a specific transition.
 * Derived from DagTransitionRule.recordBefore/recordAfter.
 *
 * Value Object (Vernon): no identity, compared by value.
 */
public data class TransitionWindow(
    val before: Duration,
    val after: Duration,
)

/**
 * Confidence filtering rules for a resident.
 * Groups minConfidence and heartbeatTimeout — they always travel together.
 *
 * Fowler's "Extract Class": instead of two separate fields in PolicyCalibration,
 * we group them into a cohesive value object.
 *
 * Value Object (Vernon): no identity, compared by value.
 */
public data class ConfidenceConfig(
    public val minConfidence: Map<StateKind, Double>,
    public val heartbeatTimeout: Duration,
) {
    init {
        require(heartbeatTimeout >= Duration.ZERO) {
            "heartbeatTimeout must not be negative"
        }
        minConfidence.values.forEach { value ->
            require(value in 0.0..1.0) {
                "confidence must be in 0.0..1.0, got $value"
            }
        }
    }
}

/**
 * Key for hysteresis transitions: from → to.
 * Used as map key in AlarmCatalog and PolicyCalibration.
 *
 * Data class gives us stable hashCode/equals for map keys.
 */
public data class TransitionKey(
    public val from: StateKind,
    public val to: StateKind,
)

/**
 * Dwell thresholds for a state: warning and exceeded.
 * Warning fires first, exceeded fires later.
 *
 * Value Object (Vernon): no identity, compared by value.
 * Invariant: warning must be less than exceeded.
 */
public data class DwellThreshold(
    val warning: Duration,
    val exceeded: Duration,
) {
    init {
        require(warning < exceeded) {
            "warning ($warning) must be less than exceeded ($exceeded)"
        }
    }

    public companion object {
        /**
         * Build a threshold from what the director actually said.
         *
         * When he gives only a deadline — "avísenme a los quince minutos" — the
         * silent pre-warning lands at half of it. That default lived in three
         * places (dwell resolution, come-back resolution, and the profile DSL)
         * and had to agree in all three; here it is one decision.
         *
         * Defaulting [warningAfter] to [exceeded] instead would violate the
         * `warning < exceeded` invariant above and fail the resolver.
         */
        public fun of(warningAfter: Duration?, exceeded: Duration): DwellThreshold =
            DwellThreshold(warning = warningAfter ?: exceeded.dividedBy(2), exceeded = exceeded)
    }
}
