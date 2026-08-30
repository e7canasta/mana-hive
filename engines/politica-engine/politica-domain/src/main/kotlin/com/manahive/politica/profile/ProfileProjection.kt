package com.manahive.politica.profile

import com.manahive.contracts.common.Channel
import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.common.buildFingerprint
import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.Aspect
import com.manahive.contracts.policy.AspectKind
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.PolicyDefaults
import com.manahive.contracts.policy.ProfileRule
import com.manahive.contracts.policy.ProfileStateRule
import com.manahive.contracts.policy.RecordWindow
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.SceneFieldRule
import com.manahive.contracts.policy.ResidentProfile
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.TransitionWindow
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.comeBackRuleId
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.RuleId
import java.time.Duration

/**
 * Algo que el perfil dice y la calibracion de hoy no sabe transportar.
 *
 * Existe porque perder cosas en silencio es el defecto que este rediseno vino a
 * cerrar, y la proyeccion inevitablemente pierde: [PolicyCalibration] esta
 * indexada por [StateKind], un enum cerrado sobre el residente, mientras que el
 * perfil habla de sujetos y estados con identidad abierta. La cama, la silla y
 * el andador **no tienen** donde entrar todavia.
 *
 * Esta lista es, literalmente, la lista de tareas de la fase 4. Mientras no este
 * vacia, el perfil dice mas de lo que el motor escucha — y conviene que eso se
 * pueda leer, no deducir.
 */
public data class Unrepresentable(
    public val path: String,
    public val reason: String,
)

/**
 * Proyecta el perfil de un residente sobre la calibracion que consumen los motores.
 *
 * ## Proyeccion, no resolucion
 *
 * `PolicyResolver.resolve()` **mergea**: toma un catalogo, le aplica una
 * plantilla, le aplica parches, y el resultado depende del orden. Esto no. Acá
 * hay un solo documento y la unica pregunta es como se escribe lo que ya dice en
 * el vocabulario de cada motor. No hay precedencia, no hay defaults inventados y
 * no hay una capa que pueda saltearse una invariante: si algo no esta en el
 * perfil, no rige.
 *
 * ## Las ventanas horarias tampoco son precedencia
 *
 * Un estado puede declarar una regla para `always` y otra para `night`. A las
 * 22:00 **se elige la de `night` entera** —plazo, severidad, cierre y
 * notificacion— y se descarta la otra. No se mezclan campos. Por eso la
 * calibracion se emite de nuevo en cada borde horario en vez de llevar las dos
 * adentro: el cambio de regimen es un hecho clinico y queda en el log.
 *
 * ## Lo que todavia no entra
 *
 * Ver [Unrepresentable]. La proyeccion lo reporta, no lo esconde.
 */
public object ProfileProjection {

    /**
     * La calibracion vigente durante [window].
     *
     * @param window la ventana activa. [ResidentProfile.ALWAYS] es el regimen
     *   normal; el nombre de una ventana declarada es el regimen de ese tramo.
     */
    public fun project(
        profile: ResidentProfile,
        window: String = ResidentProfile.ALWAYS,
    ): Explained<PolicyCalibration> {
        val resident = profile.subjects[ResidentProfile.RESIDENT]
        val aspects = resident?.aspects.orEmpty()

        val states = residentStates(aspects, window)
        val perdido = unrepresentable(profile, window)

        val calibration = PolicyCalibration(
            residentId = profile.residentId,
            scene = ScenePolicy(
                hysteresis = hysteresis(aspects),
                dwellThresholds = states.thresholdsOf { it.dwell },
                comeBackThresholds = states.thresholdsOf { it.comeBack },
                sceneHysteresis = sceneHysteresis(profile),
                sceneThresholds = sceneThresholds(profile, window),
                confidence = ConfidenceConfig(
                    minConfidence = confidence(aspects),
                    // El perfil no habla de latido: `unknownAfter` se le parece
                    // pero no es lo mismo — uno avisa a mantenimiento que un
                    // aspecto lleva mucho sin observarse, el otro decide cuando
                    // el motor deja de creerle a la escena. Mezclarlos seria
                    // convertir un aviso tecnico en una decision clinica.
                    heartbeatTimeout = PolicyDefaults.heartbeatTimeout,
                ),
            ),
            sentinel = SentinelPolicy(
                alertRules = states.alertRulesOf(TriggerOn.ENTRY) { it.onEntry } +
                    states.alertRulesOf(TriggerOn.DWELL) { it.dwell },
                comeBackRules = states.alertRulesOf(TriggerOn.COME_BACK) { it.comeBack },
                sceneStateRules = sceneStateRules(profile, window),
                closingStates = closingStates(profile),
            ),
            harbor = harbor(profile, window),
            recorder = RecorderPolicy(
                transitionWindows = transitionWindows(aspects),
                ruleWindows = ruleWindows(profile, states, window),
            ),
            fingerprint = fingerprintOf(profile, window),
        )

        return Explained(value = calibration, explanation = explain(profile, window, states, perdido))
    }

    /**
     * La huella de las reglas que produjeron una calibracion.
     *
     * Con perfiles inmutables esto se vuelve trivial y por eso es correcto: la
     * version **identifica** al documento, no lo resume. La huella que se
     * calculaba sobre los valores resueltos daba lo mismo para dos reglas
     * distintas que casualmente coincidian en tres numeros —cambiar una
     * severidad o una condicion de cierre no la movia— y contestaba "que reglas
     * se parecen a estas" cuando la pregunta del inspector es "con que reglas se
     * decidio".
     *
     * La ventana entra porque dos tramos del mismo perfil producen dos
     * calibraciones distintas, y dos calibraciones distintas no pueden compartir
     * huella.
     */
    private fun fingerprintOf(profile: ResidentProfile, window: String): Fingerprint =
        buildFingerprint(
            "resident" to profile.residentId.value,
            "version" to profile.version,
            "window" to window,
        )

    // ── seleccion por ventana ────────────────────────────────────────────────

    /**
     * Los estados del residente que [StateKind] sabe nombrar, con su regla ya
     * elegida para la ventana activa.
     *
     * Si dos aspectos declararan el mismo nombre de estado —un error de autoria,
     * porque los aspectos son ejes ortogonales— gana el primero en orden
     * alfabetico de aspecto y la colision se reporta en [unrepresentable]. Es
     * deterministico a proposito: un replay tiene que dar la misma calibracion.
     */
    private fun residentStates(
        aspects: Map<String, Aspect>,
        window: String,
    ): Map<StateKind, WindowedState> =
        aspects.toSortedMap().entries
            .flatMap { (aspectName, aspect) -> aspect.states.entries.map { aspectName to it } }
            .mapNotNull { (aspectName, entry) ->
                val kind = entry.key.asStateKind() ?: return@mapNotNull null
                kind to WindowedState(
                    aspect = aspectName,
                    state = entry.key,
                    onEntry = entry.value.onEntry.forWindow(window),
                    dwell = entry.value.dwell.forWindow(window),
                    comeBack = entry.value.comeBack.forWindow(window),
                )
            }
            // El primero gana: `toMap` con claves repetidas se queda con el
            // ultimo, que es lo contrario de lo documentado arriba.
            .reversed()
            .toMap()

    /**
     * La regla que rige en [window]: la propia de la ventana, o la de `always`.
     *
     * Es **eleccion de una regla entera**, no mezcla de campos. La validacion de
     * frontera garantiza que no hay dos reglas de la misma familia compitiendo
     * por la misma ventana, asi que `firstOrNull` no esconde un empate.
     */
    private fun List<ProfileRule>.forWindow(window: String): ProfileRule? =
        firstOrNull { it.window == window }
            ?: firstOrNull { it.window == ResidentProfile.ALWAYS }

    private data class WindowedState(
        val aspect: String,
        val state: String,
        val onEntry: ProfileRule?,
        val dwell: ProfileRule?,
        val comeBack: ProfileRule?,
    )

    // ── las cuatro proyecciones ──────────────────────────────────────────────

    private fun Map<StateKind, WindowedState>.thresholdsOf(
        pick: (WindowedState) -> ProfileRule?,
    ): Map<StateKind, DwellThreshold> =
        mapNotNull { (kind, state) -> pick(state)?.threshold()?.let { kind to it } }.toMap()

    private fun Map<StateKind, WindowedState>.alertRulesOf(
        triggerOn: TriggerOn,
        pick: (WindowedState) -> ProfileRule?,
    ): Map<StateKind, AlertRule> =
        mapNotNull { (kind, state) ->
            pick(state)?.let { rule -> kind to alertRule(kind, rule, triggerOn) }
        }.toMap()

    /**
     * La regla de alerta que corresponde a un estado bajo una familia de disparo.
     *
     * Todo campo sale del documento. Los dos que antes se deducian de la
     * severidad —`requiresConfirmation` y `requiresNvr`— eran justamente el
     * sintoma de que la politica de notificacion y de video no vivia en la
     * politica: hoy los contesta el propio perfil, `notify` y `record`.
     */
    private fun alertRule(state: StateKind, rule: ProfileRule, triggerOn: TriggerOn): AlertRule =
        AlertRule(
            id = ruleIdFor(state, triggerOn),
            trigger = state,
            triggerOn = triggerOn,
            severity = rule.severity,
            closureCondition = rule.closure,
            reversible = true,
            // Se espera confirmacion cuando hay a quien avisar y se le dio un
            // plazo para responder antes de escalar. `PT0S` es "esto no espera
            // a nadie", que es exactamente lo critico.
            requiresConfirmation = rule.notify?.let { it.escalateAfter > Duration.ZERO } ?: false,
            requiresNvr = rule.record != null,
            confirmationWindow = rule.notify?.escalateAfter?.takeIf { it > Duration.ZERO },
            umbrellaEvents = emptySet(),
        )

    /** El id de la regla que vigila [state] bajo la familia [triggerOn]. */
    private fun ruleIdFor(state: StateKind, triggerOn: TriggerOn): RuleId = when (triggerOn) {
        TriggerOn.COME_BACK -> comeBackRuleId(state)
        TriggerOn.ENTRY, TriggerOn.DWELL -> RuleId("alert-${state.name.lowercase()}")
    }

    private fun hysteresis(aspects: Map<String, Aspect>): Map<TransitionKey, Duration> =
        aspects.values
            .filter { it.transitions.isNotEmpty() }
            .flatMap { it.transitions }
            .mapNotNull { t ->
                val from = t.from.asStateKind() ?: return@mapNotNull null
                val to = t.to.asStateKind() ?: return@mapNotNull null
                TransitionKey(from, to) to t.stableFor
            }
            .toMap()

    private fun transitionWindows(aspects: Map<String, Aspect>): Map<TransitionKey, TransitionWindow> =
        aspects.values
            .flatMap { it.transitions }
            .mapNotNull { t ->
                val record = t.record ?: return@mapNotNull null
                val from = t.from.asStateKind() ?: return@mapNotNull null
                val to = t.to.asStateKind() ?: return@mapNotNull null
                TransitionKey(from, to) to TransitionWindow(before = record.before, after = record.after)
            }
            .toMap()

    private fun confidence(aspects: Map<String, Aspect>): Map<StateKind, Double> =
        aspects.values
            .flatMap { it.confidence.entries }
            .mapNotNull { (state, c) -> state.asStateKind()?.let { it to c } }
            .toMap()

    /**
     * Las ventanas de video que piden las reglas, por id de regla.
     *
     * Cubre las dos familias con la misma clave: las del residente —el borde de
     * la cama graba al entrar— y las de campo. La clave es el id que produjo
     * [alertRule] o [sceneStateRules], asi que el recorder puede ligar la orden
     * de grabar a la regla que la pidio sin adivinar.
     */
    private fun ruleWindows(
        profile: ResidentProfile,
        states: Map<StateKind, WindowedState>,
        window: String,
    ): Map<RuleId, RecordWindow> {
        val delResidente = states.flatMap { (kind, state) ->
            listOfNotNull(
                state.onEntry?.record?.let { ruleIdFor(kind, TriggerOn.ENTRY) to it },
                state.dwell?.record?.let { ruleIdFor(kind, TriggerOn.DWELL) to it },
                state.comeBack?.record?.let { ruleIdFor(kind, TriggerOn.COME_BACK) to it },
            )
        }

        val deCampo = sceneAspects(profile).mapNotNull { (field, _, aspect) ->
            aspect.states.entries.firstNotNullOfOrNull { (stateName, state) ->
                val rule = state.dwell.forWindow(window)
                    ?: state.onEntry.forWindow(window)
                    ?: return@firstNotNullOfOrNull null
                rule.record?.let { sceneRuleId(field, stateName) to it }
            }
        }

        return (delResidente + deCampo).toMap()
    }

    // ── campos de escena: la cama, la silla, el andador, el personal ────────

    /**
     * Los aspectos que **no** son del residente, con su identidad de campo.
     *
     * La clave es `sujeto.aspecto` y coincide exactamente con la que emite el
     * gemelo (`SceneState.BED_LEFT` es `"bed.left"`). Es una sola convencion en
     * las dos puntas y por eso no hay tabla de traduccion: el perfil dice
     * `bed.left` y el motor escucha `bed.left`. Cada traduccion que se evita es
     * un lugar menos donde perder algo.
     */
    private fun sceneAspects(profile: ResidentProfile): List<Triple<String, String, Aspect>> =
        profile.eachAspect()
            .filter { (subject, _, _) -> subject != ResidentProfile.RESIDENT }
            .map { (subject, aspect, value) -> Triple("$subject.$aspect", aspect, value) }
            .toList()

    /**
     * El debounce de cada campo.
     *
     * En un aspecto de flags la estabilidad vive **en el estado** y no en la
     * arista: la baranda esta baja o no lo esta, no viene "desde" ningun lado.
     * Cuando dos estados del mismo campo declaran debounce se toma el mas corto,
     * porque el campo tiene un solo filtro y elegir el largo dejaria pasar tarde
     * al otro.
     */
    private fun sceneHysteresis(profile: ResidentProfile): Map<String, Duration> =
        sceneAspects(profile).mapNotNull { (field, _, aspect) ->
            aspect.states.values
                .mapNotNull { it.stableFor }
                .minOrNull()
                ?.let { field to it }
        }.toMap()

    /** La permanencia de cada campo en la ventana activa. */
    private fun sceneThresholds(
        profile: ResidentProfile,
        window: String,
    ): Map<String, DwellThreshold> =
        sceneAspects(profile).mapNotNull { (field, _, aspect) ->
            aspect.states.values
                .firstNotNullOfOrNull { it.dwell.forWindow(window)?.threshold() }
                ?.let { field to it }
        }.toMap()

    /**
     * La regla que vigila cada campo en la ventana activa.
     *
     * Un campo con `observeOnly` —el personal, por ejemplo— no produce regla:
     * se observa y no alerta, que es exactamente lo que significa.
     */
    private fun sceneStateRules(
        profile: ResidentProfile,
        window: String,
    ): Map<String, SceneFieldRule> =
        sceneAspects(profile).mapNotNull { (field, _, aspect) ->
            aspect.states.entries.firstNotNullOfOrNull { (stateName, state) ->
                val rule = state.dwell.forWindow(window)
                    ?: state.onEntry.forWindow(window)
                    ?: return@firstNotNullOfOrNull null
                field to SceneFieldRule(
                    id = sceneRuleId(field, stateName),
                    field = field,
                    state = stateName,
                    severity = rule.severity,
                    closureCondition = rule.closure,
                    requiresConfirmation = rule.notify?.let { it.escalateAfter > Duration.ZERO } ?: false,
                    requiresNvr = rule.record != null,
                    confirmationWindow = rule.notify?.escalateAfter?.takeIf { it > Duration.ZERO },
                )
            }
        }.toMap()

    /**
     * Los estados que cierran episodios, como `sujeto.aspecto.estado`.
     *
     * Sale de **todos** los sujetos: cerrar no es privilegio del personal, es
     * una propiedad que el perfil le pone a un estado.
     */
    private fun closingStates(profile: ResidentProfile): Set<String> =
        profile.eachAspect()
            .flatMap { (subject, aspect, value) ->
                value.states.entries.asSequence()
                    .filter { it.value.closesEpisodes }
                    .map { "$subject.$aspect.${it.key}" }
            }
            .toSet()

    /** El id de la regla que vigila [state] en [field]. */
    private fun sceneRuleId(field: String, state: String): RuleId =
        RuleId("alert-${field.replace('.', '-')}-${state.lowercase()}")

    /**
     * A quien se avisa en cada nivel de gravedad.
     *
     * Sale de **todos** los sujetos y no solo del residente: la notificacion se
     * indexa por severidad, no por estado, asi que una baranda critica notifica
     * por el mismo canal que una caida critica. Es la unica parte del perfil que
     * hoy viaja entera.
     *
     * Los canales se unen y el plazo de escalada se toma el **mas corto**: si un
     * nivel tiene una regla que escala a los dos minutos y otra a los cinco,
     * respetar los cinco incumpliria la primera. Es la unica direccion segura.
     */
    private fun harbor(profile: ResidentProfile, window: String): HarborPolicy {
        val rules = profile.eachAspect()
            .flatMap { (_, _, aspect) -> aspect.states.values.asSequence() }
            .flatMap { it.rulesForWindow(window) }
            .toList()

        val channels = mutableMapOf<Severity, MutableSet<Channel>>()
        val timeouts = mutableMapOf<Severity, Duration>()
        rules.forEach { rule ->
            val notify = rule.notify ?: return@forEach
            channels.getOrPut(rule.severity) { mutableSetOf() } += notify.channels
            val current = timeouts[rule.severity]
            if (current == null || notify.escalateAfter < current) {
                timeouts[rule.severity] = notify.escalateAfter
            }
        }
        return HarborPolicy(
            defaultChannels = channels.mapValues { it.value.toSet() },
            escalationTimeouts = timeouts.toMap(),
        )
    }

    private fun ProfileStateRule.rulesForWindow(window: String): List<ProfileRule> =
        listOfNotNull(
            onEntry.forWindow(window),
            dwell.forWindow(window),
            comeBack.forWindow(window),
        )

    // ── lo que no entra ──────────────────────────────────────────────────────

    /**
     * Todo lo que [project] no puede transportar, con la ruta donde esta.
     *
     * Se calcula una sola vez y se usa para dos cosas: explicar la proyeccion y
     * contestarle a un test. Dos calculos distintos para la misma pregunta se
     * desincronizan.
     */
    public fun unrepresentable(
        profile: ResidentProfile,
        window: String = ResidentProfile.ALWAYS,
    ): List<Unrepresentable> {
        val perdido = mutableListOf<Unrepresentable>()

        profile.subjects.forEach { (subjectName, subject) ->
            if (subjectName != ResidentProfile.RESIDENT) {
                subject.aspects.forEach { (aspectName, aspect) ->
                    val at = "subjects.$subjectName.aspects.$aspectName"
                    if (aspect.unknownAfter != null) {
                        perdido += Unrepresentable(
                            "$at.unknownAfter",
                            "la calibracion no transporta el plazo de aviso a mantenimiento",
                        )
                    }
                    // El transporte lleva UNA regla por campo. Si dos estados del
                    // mismo aspecto declaran regla en la ventana activa, la
                    // segunda no viaja.
                    val conRegla = aspect.states.filterValues {
                        it.dwell.forWindow(window) != null || it.onEntry.forWindow(window) != null
                    }.keys
                    if (conRegla.size > 1) {
                        perdido += Unrepresentable(
                            "$at.states",
                            "el transporte lleva una regla por campo y este declara ${conRegla.size} " +
                                "en la ventana '$window': $conRegla. Solo viaja la primera",
                        )
                    }
                    if (aspect.unknownAfter == null && conRegla.isEmpty() &&
                        aspect.states.none { it.value.closesEpisodes }
                    ) {
                        // Un aspecto sin regla, sin plazo de mantenimiento y que
                        // no cierra nada no dice nada que se pueda perder.
                        return@forEach
                    }
                }
                return@forEach
            }

            val vistos = mutableMapOf<StateKind, String>()
            subject.aspects.toSortedMap().forEach { (aspectName, aspect) ->
                val at = "subjects.$subjectName.aspects.$aspectName"
                if (aspect.unknownAfter != null) {
                    perdido += Unrepresentable(
                        "$at.unknownAfter",
                        "la calibracion no transporta el plazo de aviso a mantenimiento",
                    )
                }
                if (subject.kind == AspectKind.FLAGS && aspect.transitions.isEmpty()) {
                    aspect.states.forEach { (stateName, rule) ->
                        if (rule.stableFor != null) {
                            perdido += Unrepresentable(
                                "$at.states.$stateName.stableFor",
                                "el debounce de un flag no entra: ScenePolicy.hysteresis esta " +
                                    "indexada por transicion y un flag no viene desde ningun lado",
                            )
                        }
                    }
                }
                aspect.transitions.forEach { t ->
                    if (t.from.asStateKind() == null || t.to.asStateKind() == null) {
                        perdido += Unrepresentable(
                            "$at.transitions",
                            "transicion ${t.from} -> ${t.to}: StateKind no nombra alguno de los dos extremos",
                        )
                    }
                }
                aspect.states.forEach { (stateName, rule) ->
                    val kind = stateName.asStateKind()
                    if (kind == null) {
                        if (!rule.silent) {
                            perdido += Unrepresentable(
                                "$at.states.$stateName",
                                "StateKind no nombra '$stateName'",
                            )
                        }
                        return@forEach
                    }
                    val previo = vistos.put(kind, aspectName)
                    if (previo != null) {
                        perdido += Unrepresentable(
                            "$at.states.$stateName",
                            "colision: '$previo' ya declaro '$stateName'. Los aspectos son ejes " +
                                "ortogonales, asi que un estado pertenece a uno solo; gana '$previo'",
                        )
                    }

                }
            }
        }
        return perdido
    }

    private fun explain(
        profile: ResidentProfile,
        window: String,
        states: Map<StateKind, WindowedState>,
        perdido: List<Unrepresentable>,
    ): List<ExplanationStep> {
        val steps = mutableListOf(
            ExplanationStep(
                rule = "profile",
                observed = "${profile.residentId.value} v${profile.version}, ventana '$window'",
                conclusion = "un solo documento: no hay capas que resolver ni parches que aplicar",
            ),
            ExplanationStep(
                rule = "provenance",
                observed = profile.provenance.template?.let { "nacio de ${it.value} " +
                    "${profile.provenance.templateVersion ?: ""}".trim() } ?: "sin plantilla",
                conclusion = "procedencia, no resolucion: de la plantilla no se aplica ninguna regla",
            ),
            ExplanationStep(
                rule = "window",
                observed = "ventanas declaradas: ${profile.windows.map { it.id }}",
                conclusion = "se elige la regla de '$window' entera, o la de 'always' si no hay",
            ),
            ExplanationStep(
                rule = "states",
                observed = "${states.size} estados del residente proyectados: " +
                    states.keys.map { it.name }.sorted().toString(),
                conclusion = "cada estado lleva la regla de su ventana",
            ),
        )
        if (perdido.isNotEmpty()) {
            steps += ExplanationStep(
                rule = "unrepresentable",
                observed = "${perdido.size} cosas que el perfil dice y la calibracion no transporta",
                conclusion = perdido.joinToString("; ") { it.path },
            )
        }
        return steps
    }

    private fun String.asStateKind(): StateKind? =
        StateKind.entries.firstOrNull { it.name == this }
}
