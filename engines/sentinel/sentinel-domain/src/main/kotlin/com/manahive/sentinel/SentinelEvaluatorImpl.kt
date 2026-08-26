package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
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
                val result = evaluateDwellExceeded(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneEvent.DwellWarning -> {
                val result = evaluateDwellWarning(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
            }
            else -> { /* not triggers for sentinel */ }
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
        // for this state escalates when its deadline elapses, in evaluateDwellExceeded
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

    // ── Dwell exceeded ─────────────────────────────────────────────────

    private fun evaluateDwellExceeded(
        fact: SceneEvent.DwellExceeded,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val state = fact.state.kind
        val open = episodes.openForBed(fact.bed)

        if (open == null) {
            // DwellExceeded → look for DWELL rules only
            val rule = calibration.dwellRuleFor(state)
                ?: return EvalResult(episodes = episodes)
            return openEpisode(fact.bed, rule, now, episodes)
        }

        // The deadline elapsed, so a timed rule may now escalate — this is the
        // moment the transition path deliberately refused to act on. Without it
        // a DWELL rule could never raise the severity of an open episode, and
        // escalation would be reachable only through immediate rules.
        val dwellRule = calibration.dwellRuleFor(state)
        if (dwellRule != null && dwellRule.severity.ordinal > open.severity.ordinal) {
            return handleEscalation(fact.bed, state, dwellRule, open, episodes, now)
        }

        val notifiable = calibration.notifiableStatesFor(open.trigger)
        val isNotifiable = state in notifiable || calibration.isWatched(state)
        if (isNotifiable) {
            val signal = SentinelSignal.UmbrellaEvent(
                bed = fact.bed,
                resident = calibration.residentId,
                at = now,
                rulesFingerprint = calibration.fingerprint,
                episode = open.id,
                state = state,
                originalSeverity = open.severity,
            )
            return EvalResult(episodes = episodes, signals = listOf(signal))
        }

        return EvalResult(episodes = episodes)
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

        val signal = SentinelSignal.EpisodeOpened(
            bed = bed,
            resident = calibration.residentId,
            at = now,
            rulesFingerprint = calibration.fingerprint,
            episode = open.id,
            rule = newRule.id,
            trigger = state,
            severity = newRule.severity,
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

    private fun suppressedResult(
        rule: AlertRule,
        state: StateKind,
        reason: String,
        episodes: EpisodeLedger,
    ): EvalResult = EvalResult(
        episodes = episodes,
        explanation = listOf(
            ExplanationStep(
                rule = rule.id.value,
                observed = "transition to $state",
                conclusion = reason,
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
