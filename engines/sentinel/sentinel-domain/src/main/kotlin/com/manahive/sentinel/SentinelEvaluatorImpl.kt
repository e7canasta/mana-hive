package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.SceneFieldRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.kind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.kernel.BedId
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import java.time.Instant

/**
 * Pure implementation of [SentinelEvaluator].
 *
 * Created with [SentinelCalibration] (one per resident).
 * State flows through [EpisodeLedger] — the shell persists it, this engine never does.
 */
internal class SentinelEvaluatorImpl(
    private val calibration: SentinelCalibration,
) : SentinelEvaluator {

    override val version: EngineVersion = EngineVersion(
        name = "sentinel-evaluator",
        semver = "0.1.0",
        buildFingerprint = "local-dev",
    )

    override fun evaluate(
        fact: SceneEvent,
        episodes: EpisodeLedger,
        now: Instant,
    ): Explained<SentinelVerdict> {
        val signals = mutableListOf<SentinelSignal>()
        val explanation = mutableListOf<ExplanationStep>()
        var state = episodes

        when (fact) {
            is SceneEvent.TransitionDetected -> {
                val result = evaluateTransition(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.StaffPresenceDetected -> {
                val result = evaluateStaffPresence(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.StaffLeftDetected -> {
                val result = evaluateStaffLeft(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.DwellExceeded -> {
                val kind = fact.state.kind
                val result = evaluateDeadline(
                    fact.bed, kind, calibration.dwellRuleFor(kind), TriggerOn.DWELL, state, now,
                )
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.DwellWarning -> {
                val result = evaluateDwellWarning(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
            }
            is SceneEvent.ComeBackExceeded -> {
                val baseline = fact.baseline.kind
                val result = evaluateDeadline(
                    fact.bed, baseline, calibration.comeBackRuleFor(baseline), TriggerOn.COME_BACK, state, now,
                )
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.ComeBackWarning -> {
                val result = evaluateComeBackWarning(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
            }

            // ── No-ops: these facts do not open or affect episodes ─────
            is SceneEvent.NightOpened -> noOp(fact, "night lifecycle — not an episode trigger", state)
            is SceneEvent.SceneStateChanged -> {
                val result = evaluateSceneChange(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.SceneDwellWarning -> {
                val result = evaluateSceneDwellWarning(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
            }
            is SceneEvent.SceneDwellExceeded -> {
                val result = evaluateSceneField(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.SignalLost -> noOp(fact, "sensor silence — See SPEC-06: plausibly an episode, not yet implemented", state)
            is SceneEvent.SignalRecovered -> noOp(fact, "sensor recovered — no action needed", state)
            is SceneEvent.NightClosed -> noOp(fact, "night lifecycle — not an episode trigger", state)
        }

        return Explained(
            value = SentinelVerdict(signals = signals, episodes = state),
            explanation = explanation,
        )
    }

    // ── Transition handling ────────────────────────────────────────────

    private fun evaluateTransition(
        fact: SceneEvent.TransitionDetected,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val state = fact.to.kind
        val open = episodes.openForBed(fact.bed)

        return when {
            open == null -> evaluateNewEpisode(fact.bed, state, episodes, now)
            else -> evaluateUnderUmbrella(fact, state, open, episodes, now)
        }
    }

    private fun evaluateNewEpisode(
        bed: BedId,
        state: StateKind,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        // TransitionDetected → look for ENTRY rules only
        val rule = calibration.transitionRuleFor(state)
            ?: return noRuleResult(state, episodes)

        // Sentinel ALWAYS opens episodes — is notification budget Harbor's concern
        return openEpisode(bed, rule, now, episodes)
    }

    private fun evaluateUnderUmbrella(
        fact: SceneEvent.TransitionDetected,
        state: StateKind,
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        if (state == StateKind.LYING) {
            return handleSafeState(fact.bed, open, episodes, now)
        }

        // Escalating is acting, so it obeys the same rule as opening: only a rule
        // the director marked as immediate may fire on a transition. A timed rule
        // for this state escalates when its deadline elapses, in evaluateDeadline
        // — walking into the bathroom is not yet "spending too long in the bathroom".
        val entryRule = calibration.transitionRuleFor(state)
        if (entryRule != null && entryRule.severity.ordinal > open.severity.ordinal) {
            return handleEscalation(fact.bed, state, entryRule, open, episodes, now)
        }

        // Notifiability is a different question from escalation: a state with a
        // timed rule is still worth reporting under the umbrella, it just is not
        // yet grounds to raise the severity.
        // For reporting we take the entry rule if there is one, else any rule
        // watching the state — the umbrella event only needs to know the state is
        // watched and at what severity it would have been reported.
        val reportedRule = entryRule ?: calibration.rulesForState(state).firstOrNull()
        return handleUmbrellaEvent(fact.bed, state, reportedRule, open, episodes, now)
    }

    // ── Staff presence ─────────────────────────────────────────────────

    private fun evaluateStaffPresence(
        fact: SceneEvent.StaffPresenceDetected,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val open = episodes.openForBed(fact.bed) ?: return EvalResult(episodes = episodes)

        val updated = open.withStaffPresent(now)

        if (updated.canClose()) {
            val cause = when (updated.closureCondition) {
                ClosureCondition.STAFF_OR_SAFE -> ClosureCause.STAFF_PRESENT
                else -> ClosureCause.STAFF_AND_SAFE
            }
            return handleClose(updated, episodes, now, cause)
        }

        return EvalResult(
            episodes = episodes.open(updated),
            explanation = listOf(
                ExplanationStep(
                    rule = "staff-presence",
                    observed = "staff present at ${fact.bed.value}",
                    conclusion = "staff marked present, episode remains open",
                ),
            ),
        )
    }

    private fun evaluateStaffLeft(
        fact: SceneEvent.StaffLeftDetected,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val open = episodes.openForBed(fact.bed) ?: return EvalResult(episodes = episodes)

        val updated = open.withStaffAbsent(now)

        return EvalResult(
            episodes = episodes.open(updated),
            explanation = listOf(
                ExplanationStep(
                    rule = "staff-left",
                    observed = "staff left ${fact.bed.value}",
                    conclusion = "staff marked absent, episode remains open",
                ),
            ),
        )
    }

    // ── Elapsed deadlines: dwell and come-back ─────────────────────────

    /**
     * A deadline the director set has elapsed for [state].
     *
     * Dwell ("lleva mucho en el baño") and come-back ("no volvió a la cama")
     * ask opposite questions of the clock, but once the deadline passes they
     * are judged identically: open an episode if none is running, escalate if
     * this rule is louder than the one running, otherwise report under the
     * umbrella. [rule] is the only thing that differs — which family of the
     * calibration was consulted — so it arrives already looked up.
     */
    private fun evaluateDeadline(
        bed: BedId,
        state: StateKind,
        rule: AlertRule?,
        triggerOn: TriggerOn,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val open = episodes.openForBed(bed)
            ?: return rule
                ?.let { openEpisode(bed, it, now, episodes) }
                ?: EvalResult(episodes = episodes)

        // The deadline elapsed, so a timed rule may now escalate — this is the
        // moment the transition path deliberately refused to act on. Without it
        // a timed rule could never raise the severity of an open episode, and
        // escalation would be reachable only through immediate rules.
        if (rule != null && rule.severity.ordinal > open.severity.ordinal) {
            return handleEscalation(bed, state, rule, open, episodes, now)
        }

        // `rule != null` is what makes come-back reportable at all: isWatched()
        // deliberately excludes come-back rules (they watch an absence, not the
        // state), so without this clause a come-back only surfaced under an
        // umbrella when some unrelated dwell rule happened to watch the same
        // state. For dwell the clause is a no-op — a dwell rule is always
        // watched — so this widens nothing but come-back.
        val isNotifiable = rule != null ||
            state in calibration.notifiableStatesFor(open.trigger) ||
            calibration.isWatched(state)
        if (!isNotifiable) return EvalResult(episodes = episodes)

        val signal = SentinelSignal.UmbrellaEvent(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            state = state,
            triggerOn = triggerOn,
            originalSeverity = open.severity,
        )
        return EvalResult(episodes = episodes, signals = listOf(signal))
    }

    // ── Campos de escena: la baranda, la silla, el andador ────────────

    /**
     * El plazo de un campo de escena venció.
     *
     * Es la contraparte de [evaluateDeadline] para lo que no es una postura. La
     * baranda que lleva un minuto abajo de noche abre episodio igual que una
     * permanencia: lo que cambia es el sujeto, no la mecanica.
     *
     * Esto era un no-op con el comentario *"not yet judged by sentinel"*. Lo era
     * porque la regla no llegaba: el slot `sceneStateRules` existia, su accessor
     * existia, y las tres construcciones le pasaban `emptyMap()`. Ahora llega.
     */
    private fun evaluateSceneField(
        fact: SceneEvent.SceneDwellExceeded,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val rule = calibration.sceneStateRuleFor(fact.field)
            ?: return EvalResult(
                episodes = episodes,
                explanation = listOf(
                    ExplanationStep(
                        rule = "scene:${fact.field}",
                        observed = "plazo vencido en ${fact.field}",
                        // Un campo sin regla se observa y no alerta. Es un valor
                        // legitimo del perfil, no un hueco.
                        conclusion = "sin regla para este campo: se observa y no alerta",
                    ),
                ),
            )

        val open = episodes.openForBed(fact.bed)
            ?: return openFieldEpisode(fact.bed, rule, now, episodes)

        // Ya hay un episodio abierto. Si la severidad es mayor, eleva silenciosamente.
        // No genera signal nuevo — el episodio ya está registrado.
        if (rule.severity.rank > open.severity.rank) {
            return EvalResult(
                episodes = episodes.open(open.escalate(rule, now)),
                signals = emptyList(),
                explanation = listOf(
                    ExplanationStep(
                        rule = rule.id.value,
                        observed = "${fact.field} = ${rule.state}",
                        conclusion = "episodio elevado: ${open.severity} -> ${rule.severity}",
                    ),
                ),
            )
        }

        // Severidad igual o menor — el evento entra bajo el paraguas, no notifica.
        return EvalResult(
            episodes = episodes,
            signals = emptyList(),
            explanation = listOf(
                ExplanationStep(
                    rule = rule.id.value,
                    observed = "${fact.field} = ${rule.state}",
                    conclusion = "bajo paraguas: severidad ${rule.severity} <= ${open.severity}",
                ),
            ),
        )

        return EvalResult(
            episodes = episodes,
            explanation = listOf(
                ExplanationStep(
                    rule = rule.id.value,
                    observed = "${fact.field} = ${rule.state}",
                    conclusion = "entra al episodio abierto (${open.severity}) sin elevarlo",
                ),
            ),
        )
    }

    /**
     * Un campo de escena cambio de valor.
     *
     * Lo unico que Sentinel juzga aca es si el estado nuevo **cierra**
     * episodios. Eso lo decide el perfil, no este codigo: `staff.presence.PRESENT`
     * cierra porque el documento dice `closesEpisodes`, y si mañana el director
     * agrega otro estado que cierre, no hace falta tocar el motor.
     *
     * Era un no-op con el comentario *"harbor's concern, not sentinel's"*. Es de
     * Harbor **avisar**, pero cerrar un episodio es del que lleva los episodios.
     */
    private fun evaluateSceneChange(
        fact: SceneEvent.SceneStateChanged,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        if (!calibration.closesEpisodes(fact.field, fact.to)) {
            return EvalResult(episodes = episodes)
        }

        val open = episodes.openForBed(fact.bed) ?: return EvalResult(episodes = episodes)
        val updated = open.withStaffPresent(now)

        if (updated.canClose()) {
            val cause = when (updated.closureCondition) {
                ClosureCondition.STAFF_OR_SAFE -> ClosureCause.STAFF_PRESENT
                else -> ClosureCause.STAFF_AND_SAFE
            }
            return handleClose(updated, episodes, now, cause)
        }

        return EvalResult(
            episodes = episodes.open(updated),
            explanation = listOf(
                ExplanationStep(
                    rule = "closing:${fact.field}.${fact.to}",
                    observed = "${fact.field} paso a ${fact.to}",
                    // STAFF_AND_SAFE exige las dos cosas: que alguien haya
                    // entrado y que la situacion sea segura. Que entre el
                    // personal cumple una mitad.
                    conclusion = "presencia registrada; el episodio sigue abierto hasta que sea seguro",
                ),
            ),
        )
    }

    /**
     * Preaviso de un campo de escena.
     *
     * La contraparte de [evaluateDwellWarning] para lo que no es una postura.
     * Solo avisa si hay regla: un campo que nadie vigila no genera preaviso de
     * algo que despues no va a pasar.
     */
    private fun evaluateSceneDwellWarning(
        fact: SceneEvent.SceneDwellWarning,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        // Un preaviso no toca episodios: se devuelve el registro tal cual llego.
        calibration.sceneStateRuleFor(fact.field) ?: return EvalResult(episodes = episodes)
        val elapsed = java.time.Duration.between(fact.since, now)
        return EvalResult(
            episodes = episodes,
            signals = listOf(
                SentinelSignal.DwellPreWarning(
                    bed = fact.bed,
                    resident = calibration.residentId,
                    at = now,
                    rulesFingerprint = calibration.fingerprint,
                    state = null,
                    field = fact.field,
                    elapsed = elapsed,
                    threshold = fact.threshold,
                ),
            ),
            explanation = listOf(
                ExplanationStep(
                    rule = "scene-dwell-warning",
                    observed = "${fact.field} lleva $elapsed (umbral: ${fact.threshold})",
                    conclusion = "preaviso: el campo se acerca a su plazo",
                ),
            ),
        )
    }

    private fun openFieldEpisode(
        bed: BedId,
        rule: SceneFieldRule,
        now: Instant,
        episodes: EpisodeLedger,
    ): EvalResult {
        val episode = Episode.openForField(
            bed = bed,
            residentId = calibration.residentId,
            at = now,
            rule = rule,
        )

        val signal = SentinelSignal.EpisodeOpened(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = episode.id,
            rule = rule.id,
            trigger = null,
            field = rule.field,
            severity = rule.severity,
            reversible = true,
            requiresNvr = rule.requiresNvr,
            confirmationWindow = rule.confirmationWindow,
        )

        return EvalResult(
            episodes = episodes.open(episode),
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = rule.id.value,
                    observed = "${rule.field} = ${rule.state}",
                    conclusion = "episodio abierto: ${rule.severity}",
                ),
            ),
        )
    }

    // ── Dwell warning (informational, no episode) ─────────────────────

    private fun evaluateDwellWarning(
        fact: SceneEvent.DwellWarning,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val elapsed = java.time.Duration.between(fact.since, now)
        val signal = SentinelSignal.DwellPreWarning(
            bed = fact.bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            state = fact.state.kind,
            elapsed = elapsed,
            threshold = fact.threshold,
        )
        return EvalResult(
            episodes = episodes,
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = "dwell-warning",
                    observed = "dwell ${fact.state.kind} for $elapsed (threshold: ${fact.threshold})",
                    conclusion = "pre-warning: resident may be approaching threshold",
                ),
            ),
        )
    }

    // ── ComeBack warning (informational, no episode) ─────────────────

    private fun evaluateComeBackWarning(
        fact: SceneEvent.ComeBackWarning,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val elapsed = java.time.Duration.between(fact.since, now)
        val signal = SentinelSignal.ComeBackPreWarning(
            bed = fact.bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            baseline = fact.baseline.kind,
            elapsed = elapsed,
            threshold = fact.threshold,
        )
        return EvalResult(
            episodes = episodes,
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = "comeback-warning",
                    observed = "away from ${fact.baseline.kind} for $elapsed (threshold: ${fact.threshold})",
                    conclusion = "pre-warning: resident may not return to baseline",
                ),
            ),
        )
    }

    // ── No-op helper ────────────────────────────────────────────────

    private fun noOp(fact: SceneEvent, reason: String, episodes: EpisodeLedger): EvalResult = EvalResult(
        episodes = episodes,
        explanation = listOf(
            ExplanationStep(
                rule = "no-op",
                observed = "${fact::class.simpleName} at ${fact.bed.value}",
                conclusion = reason,
            ),
        ),
    )

    // ── Safe state / Close / Recover ───────────────────────────────────

    private fun handleSafeState(
        bed: BedId,
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val updated = open.withSafeState(now)

        if (updated.canClose()) {
            val cause = when {
                updated.closureCondition == ClosureCondition.SAFE_ONLY -> ClosureCause.AUTO_RECOVERY
                updated.closureCondition == ClosureCondition.STAFF_OR_SAFE -> ClosureCause.AUTO_RECOVERY
                updated.staffPresent -> ClosureCause.STAFF_AND_SAFE
                else -> null
            }
            if (cause != null) {
                return handleClose(updated, episodes, now, cause)
            }
        }

        if (!updated.reversible && updated.closureCondition == ClosureCondition.STAFF_AND_SAFE) {
            val signal = SentinelSignal.AutoRecovery(
                bed = bed,
                resident = calibration.residentId,
                at = now,
                rulesFingerprint = calibration.fingerprint,
                episode = open.id,
                reversible = false,
                requiresConfirmation = true,
            )
            return EvalResult(
                episodes = episodes.open(updated),
                signals = listOf(signal),
                explanation = listOf(
                    ExplanationStep(
                        rule = "auto-recovery",
                        observed = "safe state reached without staff",
                        conclusion = "non-reversible, confirmation required",
                    ),
                ),
            )
        }

        if (updated.reversible) {
            return handleClose(updated, episodes, now, ClosureCause.AUTO_RECOVERY)
        }

        return EvalResult(
            episodes = episodes.open(updated),
            explanation = listOf(
                ExplanationStep(
                    rule = "safe-state",
                    observed = "safe state reached, waiting for staff",
                    conclusion = "episode remains open, waiting for staff",
                ),
            ),
        )
    }

    private fun handleClose(
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
        cause: ClosureCause,
    ): EvalResult {
        val gap = open.gapDuration(now)
        val signal = SentinelSignal.EpisodeClosed(
            bed = open.bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            cause = cause,
            gapDuration = gap.takeIf { it > java.time.Duration.ZERO },
        )

        return EvalResult(
            episodes = episodes.close(open.bed),
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = "closure",
                    observed = "episode ${open.id.value}",
                    conclusion = "closed: $cause, gap=${gap}",
                ),
            ),
        )
    }

    // ── Escalation ─────────────────────────────────────────────────────

    private fun handleEscalation(
        bed: BedId,
        state: StateKind,
        newRule: AlertRule,
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val updated = open.escalate(newRule, now)

        val signal = SentinelSignal.EpisodeComplicated(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            rule = newRule.id,
            trigger = state,
            severity = newRule.severity,
            previousSeverity = open.severity,
            reversible = newRule.reversible,
            requiresNvr = newRule.requiresNvr,
            confirmationWindow = newRule.confirmationWindow,
        )

        return EvalResult(
            episodes = episodes.open(updated),
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = newRule.id.value,
                    observed = "escalation from ${open.severity} to ${newRule.severity}",
                    conclusion = "episode escalated",
                ),
            ),
        )
    }

    // ── Umbrella event ─────────────────────────────────────────────────

    private fun handleUmbrellaEvent(
        bed: BedId,
        state: StateKind,
        newRule: AlertRule?,
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val notifiable = calibration.notifiableStatesFor(open.trigger)
        val isNotifiable = newRule != null || state in notifiable

        if (!isNotifiable) {
            return EvalResult(
                episodes = episodes,
                explanation = listOf(
                    ExplanationStep(
                        rule = "umbrella",
                        observed = "transition to $state under episode ${open.id.value}",
                        conclusion = "not notifiable under umbrella, no action",
                    ),
                ),
            )
        }

        val originalSeverity = newRule?.severity ?: open.severity
        val signal = SentinelSignal.UmbrellaEvent(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            state = state,
            // A transition put him IN this state — that is the ENTRY reading.
            triggerOn = TriggerOn.ENTRY,
            originalSeverity = originalSeverity,
        )

        val event = EpisodeEvent.UmbrellaEvent(
            episodeId = open.id,
            state = state,
            matchedRule = newRule?.id,
            originalSeverity = newRule?.severity ?: open.severity,
            at = now,
        )

        return EvalResult(
            episodes = episodes.open(open.withEvent(event)),
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = "umbrella",
                    observed = "transition to $state under episode ${open.id.value}",
                    conclusion = "umbrella event: $originalSeverity",
                ),
            ),
        )
    }

    // ── Shared: open episode ───────────────────────────────────────────
    // Fowler: "Extract Method" — one place to open episodes, no duplication.

    private fun openEpisode(
        bed: BedId,
        rule: AlertRule,
        now: Instant,
        episodes: EpisodeLedger,
    ): EvalResult {
        val episode = Episode.open(
            bed = bed,
            residentId = calibration.residentId,
            at = now,
            rule = rule,
        )

        val signal = SentinelSignal.EpisodeOpened(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = episode.id,
            rule = rule.id,
            trigger = rule.trigger,
            severity = rule.severity,
            reversible = rule.reversible,
            requiresNvr = rule.requiresNvr,
            confirmationWindow = rule.confirmationWindow,
        )

        return EvalResult(
            episodes = episodes.open(episode),
            signals = listOf(signal),
            explanation = listOf(
                ExplanationStep(
                    rule = rule.id.value,
                    observed = "trigger ${rule.trigger}",
                    conclusion = "episode opened: ${rule.severity}",
                ),
            ),
        )
    }

    // ── Shared: no rule / suppressed ───────────────────────────────────

    private fun noRuleResult(state: StateKind, episodes: EpisodeLedger): EvalResult = EvalResult(
        episodes = episodes,
        explanation = listOf(
            ExplanationStep(
                rule = "no-rule",
                observed = "transition to $state",
                conclusion = "no matching rule, no action",
            ),
        ),
    )

    // ── Helpers ────────────────────────────────────────────────────────

    private data class EvalResult(
        val episodes: EpisodeLedger,
        val signals: List<SentinelSignal> = emptyList(),
        val explanation: List<ExplanationStep> = emptyList(),
    )
}
