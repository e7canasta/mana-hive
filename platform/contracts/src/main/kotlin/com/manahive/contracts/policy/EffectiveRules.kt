package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * The resolved rules for one resident: what happens when a scene fact arrives.
 *
 * Politica Engine produces this from catalog + profile + overrides.
 * Sentinel consults it to decide what action to take.
 *
 * Fowler's "Introduce Parameter Object": instead of passing catalog, profile,
 * and overrides separately, we pass one resolved object.
 * Vernon's "Published Interface": this is the ACL between Politica and Sentinel.
 */
public data class EffectiveRules(
    public val residentId: ResidentId,
    public val rules: List<AlertRule>,
    public val fingerprint: String,
)

/**
 * One alert rule: a trigger state, the severity it implies, and how the
 * episode closes. The director configures these per resident via the
 * "self-service menu" analogy.
 *
 * The rule is contextual: severity depends on the trigger, risk level,
 * time of day, and staff presence — all resolved by Politica Engine.
 */
public data class AlertRule(
    public val id: RuleId,
    public val trigger: StateKind,
    public val triggerOn: TriggerOn,
    public val severity: Severity,
    public val closureCondition: ClosureCondition,
    public val reversible: Boolean,
    public val requiresConfirmation: Boolean,
    public val requiresNvr: Boolean,
    public val confirmationWindow: Duration?,
    /** Events that are notifiable under this episode's umbrella. */
    public val umbrellaEvents: Set<StateKind> = emptySet(),
)

/**
 * Que tan grave es lo que paso, en el idioma de quien tiene que responder.
 *
 * Los niveles no son etiquetas: cada uno responde dos preguntas distintas
 * —quien se entera, y si alguien tiene que ir— y esa es toda su semantica.
 *
 * | Nivel | Se entera | Hay que ir |
 * |---|---|---|
 * | [INFO] | nadie, queda en el registro | no |
 * | [WARNING] | el personal de turno | no |
 * | [HIGH] | el personal de turno | **si**, sin urgencia |
 * | [CRITICAL] | todos, ya | **si**, ahora |
 *
 * [HIGH] se agrego porque entre "te aviso" y "es una emergencia" no habia nada,
 * y ahi vive la mayor parte del trabajo nocturno de un geriatrico: la baranda
 * que quedo baja, el andador fuera de alcance, el bano que se esta estirando.
 * Sin ese escalon, todo eso o se subestimaba como aviso o se inflaba a critico
 * — y un sistema que grita siempre deja de escucharse.
 *
 * La severidad es ademas **el mecanismo de composicion de episodios**: cuando ya
 * hay un episodio abierto, un evento de nivel menor entra como neutro y no
 * notifica, uno del mismo nivel es parte del mismo episodio, y uno mayor lo
 * eleva. Por eso el orden importa y esta explicito en [rank].
 */
public enum class Severity {
    INFO,
    WARNING,
    HIGH,
    CRITICAL,
    ;

    /** Orden de gravedad. Lo usa la composicion de episodios. */
    public val rank: Int get() = ordinal

    /** Si este nivel espera que alguien vaya a la habitacion. */
    public val requiresAttendance: Boolean get() = this == HIGH || this == CRITICAL
}

/**
 * How a rule is triggered. Determines which SceneEvent opens the episode:
 * - ENTRY: opens on TransitionDetected (for states that need no wait, e.g. BED_EDGE in CRITICAL)
 * - DWELL: opens on DwellExceeded (for states with a time threshold)
 * - COME_BACK: opens on ComeBackExceeded (time away from baseline, e.g. not returning to bed)
 */
public enum class TriggerOn {
    ENTRY,
    DWELL,
    COME_BACK,
}

/**
 * How an episode closes. The director configures this per resident.
 *
 * SAFE_ONLY: closes when resident returns to safe state (alert)
 * STAFF_AND_SAFE: closes when staff assists AND resident is safe (incident)
 * STAFF_OR_SAFE: closes when staff assists OR resident is safe (flexible)
 */
public enum class ClosureCondition { SAFE_ONLY, STAFF_AND_SAFE, STAFF_OR_SAFE }
