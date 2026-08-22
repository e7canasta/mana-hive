package com.manahive.harbor

import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.NoticeId
import com.manahive.kernel.StaffId
import java.time.Instant

/**
 * The notice lifecycle as a pure Decider.
 *
 * Two invariants:
 * 1. One notice per episode (the key IS the identity)
 * 2. RESOLVED IS ABSORBING — no command revives a resolved notice
 *
 * Fowler: "Decider Pattern" — command → state → events.
 * Vernon: "Aggregate Root" — the lifecycle guards its own invariants.
 */
public interface NoticeLifecycle {
    public fun decide(command: NoticeCommand, state: LifecycleState?): NoticeResult
}

/**
 * Commands that can be issued to a notice.
 */
public sealed interface NoticeCommand {
    public data class Create(val signal: SentinelSignal.EpisodeOpened) : NoticeCommand
    public data class Dispatch(val id: NoticeId, val channels: Set<Channel>) : NoticeCommand
    public data class MarkSeen(val id: NoticeId, val by: StaffId, val at: Instant) : NoticeCommand
    public data class Acknowledge(val id: NoticeId, val by: StaffId, val at: Instant) : NoticeCommand
    public data class Escalate(val id: NoticeId, val at: Instant) : NoticeCommand
    public data class Cancel(val id: NoticeId, val at: Instant, val reason: String) : NoticeCommand
    public data class Resolve(val id: NoticeId, val resolution: Resolution, val at: Instant) : NoticeCommand
}

/**
 * Events produced by notice lifecycle transitions.
 */
public sealed interface NoticeEvent {
    public val id: NoticeId
    public val at: Instant

    public data class Created(override val id: NoticeId, override val at: Instant, val notice: Notice) : NoticeEvent
    public data class Dispatched(override val id: NoticeId, override val at: Instant, val channels: Set<Channel>) : NoticeEvent
    public data class Seen(override val id: NoticeId, override val at: Instant, val by: StaffId) : NoticeEvent
    public data class Acknowledged(override val id: NoticeId, override val at: Instant, val by: StaffId) : NoticeEvent
    public data class Escalated(override val id: NoticeId, override val at: Instant) : NoticeEvent
    public data class Cancelled(override val id: NoticeId, override val at: Instant, val reason: String) : NoticeEvent
    public data class Resolved(override val id: NoticeId, override val at: Instant, val resolution: Resolution) : NoticeEvent
}

/**
 * Result of a decide operation: new state + events produced.
 */
public data class NoticeResult(
    public val state: LifecycleState,
    public val events: List<NoticeEvent>,
)

/**
 * Notice state for the Decider.
 */
public data class LifecycleState(
    public val id: NoticeId,
    public val notice: Notice,
    public val phase: NoticePhase = NoticePhase.NONE,
) {
    public enum class NoticePhase {
        NONE,
        CREATED,
        DISPATCHED,
        SEEN,
        ACKNOWLEDGED,
        ESCALATED,
        CANCELLED,
        RESOLVED,
    }
}

/**
 * Rejection reasons for notice commands.
 */
public enum class NoticeRejection(public val code: String) {
    ALREADY_EXISTS("notice.already-exists"),
    ALREADY_RESOLVED("notice.already-resolved"),
    UNKNOWN_NOTICE("notice.unknown"),
    ILLEGAL_PHASE("notice.illegal-phase"),
}

/**
 * Fowler: "Move伴にメソッド" — the command knows how to create its own event.
 * This eliminates Feature Envy in batch apps and keeps domain logic together.
 */
public fun NoticeCommand.toEvent(now: java.time.Instant): NoticeEvent? = when (this) {
    is NoticeCommand.Create -> NoticeEvent.Created(
        id = NoticeId.fromEpisode(signal.episode),
        at = signal.at,
        notice = Notice.from(signal),
    )
    is NoticeCommand.Dispatch -> NoticeEvent.Dispatched(
        id = id,
        at = now,
        channels = channels,
    )
    is NoticeCommand.MarkSeen -> NoticeEvent.Seen(
        id = id,
        at = at,
        by = by,
    )
    is NoticeCommand.Acknowledge -> NoticeEvent.Acknowledged(
        id = id,
        at = at,
        by = by,
    )
    is NoticeCommand.Escalate -> NoticeEvent.Escalated(
        id = id,
        at = at,
    )
    is NoticeCommand.Cancel -> NoticeEvent.Cancelled(
        id = id,
        at = at,
        reason = reason,
    )
    is NoticeCommand.Resolve -> NoticeEvent.Resolved(
        id = id,
        at = at,
        resolution = resolution,
    )
}

/**
 * Fowler: "Move伴にメソッド" — ClosureCause knows how to map to Resolution.
 * Eliminates Feature Envy in HarborEngineImpl.
 */
public fun ClosureCause.toResolution(): Resolution = when (this) {
    ClosureCause.STAFF_AND_SAFE -> Resolution.STAFF_PRESENT
    ClosureCause.AUTO_RECOVERY -> Resolution.AUTO_RECOVERY
}

/**
 * Fowler: "Move伴にメソッド" — each event knows how to serialize itself.
 * Eliminates Feature Envy in batch apps.
 */
public fun NoticeEvent.toJson(): Map<String, Any?> {
    val base = linkedMapOf<String, Any?>()
    base["type"] = this::class.simpleName
    base["id"] = id.value
    base["at"] = at.toString()

    when (this) {
        is NoticeEvent.Created -> {
            base["notice"] = mapOf(
                "episode" to notice.episode.value,
                "bed" to notice.bed.value,
                "severity" to notice.severity.name,
            )
        }
        is NoticeEvent.Dispatched -> {
            base["channels"] = channels.map { it.name }
        }
        is NoticeEvent.Seen -> {
            base["by"] = by.value
        }
        is NoticeEvent.Acknowledged -> {
            base["by"] = by.value
        }
        is NoticeEvent.Escalated -> {
            // no additional data
        }
        is NoticeEvent.Cancelled -> {
            base["reason"] = reason
        }
        is NoticeEvent.Resolved -> {
            base["resolution"] = resolution.name
        }
    }

    return base
}
