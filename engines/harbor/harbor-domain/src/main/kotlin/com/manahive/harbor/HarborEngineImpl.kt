package com.manahive.harbor

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
 * State flows through [NoticeRegistry] — the shell persists it, this engine never does.
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
        registry: NoticeRegistry,
        now: Instant,
    ): Explained<HarborVerdict> {
        val result = when (signal) {
            is SentinelSignal.EpisodeOpened -> handleEpisodeOpened(signal, registry, now)
            is SentinelSignal.EpisodeClosed -> handleEpisodeClosed(signal, registry, now)
            is SentinelSignal.AutoRecovery -> handleAutoRecovery(signal, registry, now)
            is SentinelSignal.UmbrellaEvent -> handleUmbrellaEvent(signal, registry)
            is SentinelSignal.SuppressedWithRecord -> handleSuppressed(signal, registry)
        }

        return Explained(
            value = HarborVerdict(commands = result.commands, registry = result.registry),
            explanation = result.explanation,
        )
    }

    // ── Signal Handlers ──────────────────────────────────────────────────────

    private fun handleEpisodeOpened(
        signal: SentinelSignal.EpisodeOpened,
        registry: NoticeRegistry,
        now: Instant,
    ): EvalResult {
        // Check if notice already exists for this episode
        val existing = registry.get(signal.episode)
        if (existing != null) {
            return EvalResult(
                registry = registry,
                explanation = listOf(ExplanationStep(
                    rule = "duplicate",
                    observed = "notice already exists for episode ${signal.episode.value}",
                    conclusion = "no new notice created",
                )),
            )
        }

        // Create new notice
        val notice = Notice.from(signal)
        val channels = calibration.channelsFor(signal.severity)

        val dispatchCommand = NoticeCommand.Dispatch(
            id = notice.id,
            channels = channels,
        )

        return EvalResult(
            registry = registry.add(notice),
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
        registry: NoticeRegistry,
        now: Instant,
    ): EvalResult {
        val notice = registry.get(signal.episode)
            ?: return EvalResult(
                registry = registry,
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
            registry = registry.remove(signal.episode),
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
        registry: NoticeRegistry,
        now: Instant,
    ): EvalResult {
        val notice = registry.get(signal.episode)
            ?: return EvalResult(
                registry = registry,
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
                registry = registry.remove(signal.episode),
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
            registry = registry,
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
        registry: NoticeRegistry,
    ): EvalResult = EvalResult(
        registry = registry,
        explanation = listOf(ExplanationStep(
            rule = "umbrella",
            observed = "umbrella event for ${signal.episode.value}",
            conclusion = "no new notice, episode already open",
        )),
    )

    private fun handleSuppressed(
        signal: SentinelSignal.SuppressedWithRecord,
        registry: NoticeRegistry,
    ): EvalResult = EvalResult(
        registry = registry,
        explanation = listOf(ExplanationStep(
            rule = "suppressed",
            observed = "signal suppressed: ${signal.cause}",
            conclusion = "no notice created",
        )),
    )

    // ── Internal Types ───────────────────────────────────────────────────────

    private data class EvalResult(
        val registry: NoticeRegistry,
        val commands: List<NoticeCommand> = emptyList(),
        val explanation: List<ExplanationStep> = emptyList(),
    )
}
