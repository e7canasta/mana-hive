package com.manahive.harbor

import com.manahive.contracts.common.Channel
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.NoticeId
import java.time.Instant

/**
 * Pure implementation of [HarborEngine].
 *
 * Created with [HarborCalibration] — immutable for the engine's lifetime.
 * State flows through [HarborState] — the shell persists it, this engine never does.
 *
 * Fowler: "Extract Method" — each signal type has its own handler.
 * Vernon: "Domain Service" — coordinates across aggregates without owning them.
 */
internal class HarborEngineImpl(
    private val calibration: HarborCalibration,
) : HarborEngine {

    override val version: EngineVersion = EngineVersion(
        name = "harbor-engine",
        semver = "0.1.0",
        buildFingerprint = "local-dev",
    )

    override fun evaluate(
        signal: SentinelSignal,
        state: HarborState,
        now: Instant,
    ): Explained<HarborVerdict> {
        val result = when (signal) {
            is SentinelSignal.EpisodeOpened -> handleEpisodeOpened(signal, state, now)
            is SentinelSignal.EpisodeClosed -> handleEpisodeClosed(signal, state, now)
            is SentinelSignal.AutoRecovery -> handleAutoRecovery(signal, state, now)
            is SentinelSignal.UmbrellaEvent -> handleUmbrellaEvent(signal, state)
            is SentinelSignal.SuppressedWithRecord -> handleSuppressed(signal, state)
            is SentinelSignal.DwellPreWarning -> handleDwellPreWarning(signal, state)
            is SentinelSignal.ComeBackPreWarning -> handleComeBackPreWarning(signal, state)
        }

        return Explained(
            value = HarborVerdict(commands = result.commands, state = result.state),
            explanation = result.explanation,
        )
    }

    // ── Signal Handlers ──────────────────────────────────────────────────────

    private fun handleEpisodeOpened(
        signal: SentinelSignal.EpisodeOpened,
        state: HarborState,
        now: Instant,
    ): EvalResult {
        // Check if notice already exists for this episode
        val existing = state.registry.get(signal.episode)
        if (existing != null) {
            return EvalResult(
                state = state,
                explanation = listOf(ExplanationStep(
                    rule = "duplicate",
                    observed = "notice already exists for episode ${signal.episode.value}",
                    conclusion = "no new notice created",
                )),
            )
        }

        // Create new notice (always — facts are facts)
        val notice = Notice.from(signal)

        // Check budget before dispatch (CRITICAL is never suppressed)
        // El tope lo define la calibración; la cuenta la lleva el ESTADO. Antes se
        // preguntaba a `calibration.budget`, que tiene `dispatched = 0` fijo porque
        // es configuración: nunca podía estar excedido.
        if (!state.budget.canDeliver(signal.severity)) {
            return EvalResult(
                state = state.copy(
                    registry = state.registry.add(notice),
                ).withFatigueTrack(signal.severity, calibration.budget.maxFor(signal.severity)),
                commands = emptyList(),
                explanation = listOf(ExplanationStep(
                    rule = "budget",
                    observed = "severity=${signal.severity}, episode=${signal.episode.value}",
                    conclusion = "notice created, not dispatched (budget budget exceeded)",
                )),
            )
        }

        // Dispatch normal
        val channels = calibration.channelsFor(signal.severity)
        val dispatchCommand = NoticeCommand.Dispatch(
            id = notice.id,
            channels = channels,
        )

        return EvalResult(
            state = state.copy(
                registry = state.registry.add(notice),
            ).withFatigueTrack(signal.severity, calibration.budget.maxFor(signal.severity)),
            commands = listOf(dispatchCommand),
            explanation = listOf(ExplanationStep(
                rule = "episode-opened",
                observed = "severity=${signal.severity}, episode=${signal.episode.value}",
                conclusion = "notice created, dispatching to $channels",
            )),
        )
    }

    private fun handleEpisodeClosed(
        signal: SentinelSignal.EpisodeClosed,
        state: HarborState,
        now: Instant,
    ): EvalResult {
        val notice = state.registry.get(signal.episode)
            ?: return EvalResult(
                state = state,
                explanation = listOf(ExplanationStep(
                    rule = "no-notice",
                    observed = "episode ${signal.episode.value} closed",
                    conclusion = "no active notice to resolve",
                )),
            )

        val resolution = signal.cause.toResolution()

        val resolveCommand = NoticeCommand.Resolve(
            id = notice.id,
            resolution = resolution,
            at = now,
        )

        return EvalResult(
            state = state.copy(registry = state.registry.remove(signal.episode)),
            commands = listOf(resolveCommand),
            explanation = listOf(ExplanationStep(
                rule = "episode-closed",
                observed = "cause=${signal.cause}, episode=${signal.episode.value}",
                conclusion = "notice resolved: $resolution",
            )),
        )
    }

    private fun handleAutoRecovery(
        signal: SentinelSignal.AutoRecovery,
        state: HarborState,
        now: Instant,
    ): EvalResult {
        val notice = state.registry.get(signal.episode)
            ?: return EvalResult(
                state = state,
                explanation = listOf(ExplanationStep(
                    rule = "no-notice",
                    observed = "auto-recovery for episode ${signal.episode.value}",
                    conclusion = "no active notice",
                )),
            )

        // If reversible, resolve automatically
        if (signal.reversible) {
            val resolveCommand = NoticeCommand.Resolve(
                id = notice.id,
                resolution = Resolution.AUTO_RECOVERY,
                at = now,
            )

            return EvalResult(
                state = state.copy(registry = state.registry.remove(signal.episode)),
                commands = listOf(resolveCommand),
                explanation = listOf(ExplanationStep(
                    rule = "auto-recovery-reversible",
                    observed = "reversible, episode ${signal.episode.value}",
                    conclusion = "notice resolved automatically",
                )),
            )
        }

        // If non-reversible, send confirmation alert (staff must verify)
        val confirmChannels = calibration.confirmationChannels
        val confirmCommand = NoticeCommand.Dispatch(
            id = notice.id,
            channels = confirmChannels,
        )

        return EvalResult(
            state = state,
            commands = listOf(confirmCommand),
            explanation = listOf(ExplanationStep(
                rule = "auto-recovery-non-reversible",
                observed = "non-reversible, episode ${signal.episode.value}",
                conclusion = "confirmation alert sent, staff must verify",
            )),
        )
    }

    private fun handleUmbrellaEvent(
        signal: SentinelSignal.UmbrellaEvent,
        state: HarborState,
    ): EvalResult = EvalResult(
        state = state,
        explanation = listOf(ExplanationStep(
            rule = "umbrella",
            observed = "umbrella event for ${signal.episode.value}",
            conclusion = "no new notice, episode already open",
        )),
    )

    private fun handleSuppressed(
        signal: SentinelSignal.SuppressedWithRecord,
        state: HarborState,
    ): EvalResult = EvalResult(
        state = state,
        explanation = listOf(ExplanationStep(
            rule = "suppressed",
            observed = "signal suppressed: ${signal.cause}",
            conclusion = "no notice created",
        )),
    )

    private fun handleDwellPreWarning(
        signal: SentinelSignal.DwellPreWarning,
        state: HarborState,
    ): EvalResult = EvalResult(
        state = state,
        explanation = listOf(ExplanationStep(
            rule = "dwell-pre-warning",
            observed = "dwell ${signal.state} for ${signal.elapsed} (threshold: ${signal.threshold})",
            conclusion = "pre-warning: informational, no notice created",
        )),
    )

    /**
     * The mirror of [handleDwellPreWarning], and it must read as the mirror:
     * this one is about a resident who is NOT in the state named. Saying
     * "dwell LYING" about someone who has not come back to bed is not a
     * wording nit — it is the opposite of what happened.
     */
    private fun handleComeBackPreWarning(
        signal: SentinelSignal.ComeBackPreWarning,
        state: HarborState,
    ): EvalResult = EvalResult(
        state = state,
        explanation = listOf(ExplanationStep(
            rule = "comeback-pre-warning",
            observed = "away from ${signal.baseline} for ${signal.elapsed} (threshold: ${signal.threshold})",
            conclusion = "pre-warning: informational, no notice created",
        )),
    )

    // ── Internal Types ───────────────────────────────────────────────────────

    private data class EvalResult(
        val state: HarborState,
        val commands: List<NoticeCommand> = emptyList(),
        val explanation: List<ExplanationStep> = emptyList(),
    )
}
