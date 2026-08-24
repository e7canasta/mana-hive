package com.manahive.harbor

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.Engine
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.time.Instant

/**
 * The harbor engine: receives Sentinel signals, produces Notice commands.
 *
 * Same philosophy as SentinelEvaluator:
 * - Created with calibration (HarborCalibration) — immutable
 * - State flows through (NoticeRegistry in, HarborVerdict out)
 * - Pure function: same input → same output
 * - Now is injected, never Instant.now()
 *
 * Responsible for:
 * - Converting SentinelSignal → NoticeCommand
 * - Managing notice lifecycle based on signal type
 * - Determining channels based on severity
 *
 * NOT responsible for:
 * - Delivery (external adapters)
 * - Confirmation tracking (NoticeLifecycle)
 * - Escalation timing (NoticeRouter)
 */
public interface HarborEngine : Engine {
    public fun evaluate(
        signal: SentinelSignal,
        state: HarborState,
        now: Instant,
    ): Explained<HarborVerdict>
}

/**
 * Factory function for creating [HarborEngine] instances.
 */
public fun createHarborEngine(calibration: HarborCalibration): HarborEngine =
    HarborEngineImpl(calibration)

/**
 * The state carried through Harbor evaluations.
 * Bundles notice registry (value class) with delivery budget.
 */
public data class HarborState(
    public val registry: NoticeRegistry = NoticeRegistry(),
    public val budget: NotificationBudget = NotificationBudget(),
) {
    /** Track a dispatched notification for budget purposes. */
    public fun withFatigueTrack(severity: Severity): HarborState =
        copy(budget = budget.track(severity))
}

/**
 * The output of one evaluation: commands to execute + next state.
 */
public data class HarborVerdict(
    val commands: List<NoticeCommand>,
    val state: HarborState,
)

/**
 * Calibration for the harbor engine.
 *
 * Same pattern as SentinelCalibration:
 * - Created per resident (one calibration per bed/night)
 * - Immutable for the engine's lifetime
 * - If rules change, create a new engine with new calibration
 *
 * Defines:
 * - Which channels to use per severity
 * - Escalation timeouts
 * - Confirmation behavior
 */
public data class HarborCalibration(
    public val residentId: ResidentId,
    public val defaultChannels: Map<Severity, Set<Channel>>,
    public val escalationTimeouts: Map<Severity, Duration>,
    /** Channels for confirmation alerts (non-reversible auto-recovery). */
    public val confirmationChannels: Set<Channel>,
    /** Delivery budget: max notifications per severity per shift. */
    public val budget: NotificationBudget = NotificationBudget(),
    /** Calibration fingerprint for reproducibility. */
    public val fingerprint: String,
) {
    /** Get channels for a severity level. */
    public fun channelsFor(severity: Severity): Set<Channel> =
        defaultChannels[severity] ?: emptySet()

    /** Get escalation timeout for a severity level. */
    public fun escalationTimeoutFor(severity: Severity): Duration =
        escalationTimeouts[severity] ?: Duration.ZERO

    public companion object {
        /** Default calibration for standard care facility. */
        public fun default(residentId: ResidentId = ResidentId("default")): HarborCalibration =
            harborCalibration {
                resident(residentId)

                notice {
                    channels = setOf(Channel.CONSOLE)
                    escalationTimeout = 30.minutes
                }
                alert {
                    channels = setOf(Channel.PUSH, Channel.TABLET)
                    escalationTimeout = 5.minutes
                }
                incident {
                    channels = setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE)
                    escalationTimeout = 0.seconds
                }
            }
    }
}

/**
 * In-memory registry of active notices.
 *
 * Fowler: "Replace Data Value Object with Object" — but this is truly a value.
 * The registry is immutable and copied on every change.
 */
@JvmInline
public value class NoticeRegistry(
    public val active: Map<com.manahive.kernel.EpisodeId, Notice> = emptyMap(),
) {
    public fun add(notice: Notice): NoticeRegistry =
        NoticeRegistry(active + (notice.episode to notice))

    public fun remove(episodeId: com.manahive.kernel.EpisodeId): NoticeRegistry =
        NoticeRegistry(active - episodeId)

    public fun get(episodeId: com.manahive.kernel.EpisodeId): Notice? =
        active[episodeId]
}
