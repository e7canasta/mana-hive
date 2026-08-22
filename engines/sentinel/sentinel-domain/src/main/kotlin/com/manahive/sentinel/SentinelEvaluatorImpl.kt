package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.scene.SceneFact
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
        fact: SceneFact,
        episodes: EpisodeLedger,
        now: Instant,
    ): Explained<SentinelVerdict> {
        val signals = mutableListOf<SentinelSignal>()
        val explanation = mutableListOf<ExplanationStep>()
        var state = episodes

        when (fact) {
            is SceneFact.TransitionDetected -> {
                val result = evaluateTransition(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneFact.StaffPresenceDetected -> {
                val result = evaluateStaffPresence(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
            }
            is SceneFact.DwellExceeded -> {
                val result = evaluateDwellExceeded(fact, state, now)
                signals.addAll(result.signals)
                explanation.addAll(result.explanation)
                state = result.episodes
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
        fact: SceneFact.TransitionDetected,
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
        val rule = calibration.ruleFor(state)
            ?: return noRuleResult(state, episodes)

        if (episodes.fatigue.exceeded) {
            return suppressedResult(rule, state, "fatigue budget exceeded", episodes)
        }

        return openEpisode(bed, rule, now, episodes)
    }

    private fun evaluateUnderUmbrella(
        fact: SceneFact.TransitionDetected,
        state: StateKind,
        open: Episode,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        if (state == StateKind.LYING) {
            return handleSafeState(fact.bed, open, episodes, now)
        }

        val newRule = calibration.ruleFor(state)
        if (newRule != null && newRule.severity.ordinal > open.severity.ordinal) {
            return handleEscalation(fact.bed, state, newRule, open, episodes, now)
        }

        return handleUmbrellaEvent(fact.bed, state, newRule, open, episodes, now)
    }

    // ── Staff presence ─────────────────────────────────────────────────

    private fun evaluateStaffPresence(
        fact: SceneFact.StaffPresenceDetected,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val open = episodes.openForBed(fact.bed) ?: return EvalResult(episodes = episodes)

        val updated = open.withStaffPresent()

        if (updated.canClose()) {
            return handleClose(updated, episodes, now, ClosureCause.STAFF_AND_SAFE)
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

    // ── Dwell exceeded ─────────────────────────────────────────────────

    private fun evaluateDwellExceeded(
        fact: SceneFact.DwellExceeded,
        episodes: EpisodeLedger,
        now: Instant,
    ): EvalResult {
        val state = fact.state.kind
        val open = episodes.openForBed(fact.bed)

        if (open == null) {
            val rule = calibration.ruleFor(state)
                ?: return EvalResult(episodes = episodes)
            return openEpisode(fact.bed, rule, now, episodes)
        }

        val notifiable = calibration.notifiableStatesFor(open.trigger)
        val isNotifiable = state in notifiable || calibration.ruleFor(state) != null
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
        val event = EpisodeEvent(
            state = state,
            at = now,
            matchedRule = newRule.id,
            originalSeverity = newRule.severity,
        )
        val updated = open.escalate(newRule).withEvent(event)

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

        val event = EpisodeEvent(
            state = state,
            at = now,
            matchedRule = newRule?.id,
            originalSeverity = newRule?.severity ?: open.severity,
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
            episodes = episodes.open(episode).withFatigueIncrement(),
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
