package com.manahive.contracts.alarm

import com.manahive.contracts.policy.Severity
import com.manahive.kernel.AlertId
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.EventRef
import com.manahive.kernel.RuleId
import com.manahive.kernel.StaffId
import java.time.Instant

/**
 * The alert lifecycle, owned by the vigia. Published on `alarm.event.v1.<alert>`
 * and ingested by the hub as system of record. One alert per (bed, rule,
 * episode): the key IS the identity — dedupe by construction.
 */
public sealed interface AlarmEvent {
    public val alert: AlertId
    public val at: Instant

    public data class AlertRaised(
        override val alert: AlertId, override val at: Instant,
        public val key: AlertKey,
        public val severity: Severity,
        public val origin: EventRef,
    ) : AlarmEvent

    public data class DeliveryOrdered(
        override val alert: AlertId, override val at: Instant,
        public val step: Int,
        public val channel: Channel,
        public val recipients: List<StaffId>,
    ) : AlarmEvent

    public data class Delivered(override val alert: AlertId, override val at: Instant, public val step: Int) : AlarmEvent
    public data class Seen(override val alert: AlertId, override val at: Instant, public val by: StaffId) : AlarmEvent
    public data class Acknowledged(override val alert: AlertId, override val at: Instant, public val by: StaffId) : AlarmEvent

    public data class Escalated(
        override val alert: AlertId, override val at: Instant,
        public val toStep: Int,
        public val cause: EscalationCause,
    ) : AlarmEvent

    /** Silencing without an expiry is forbidden by type. */
    public data class Silenced(
        override val alert: AlertId, override val at: Instant,
        public val until: Instant,
        public val by: StaffId,
        public val reason: String,
    ) : AlarmEvent

    /** Closing the loop: staff physically in the room. Measured, not declared. */
    public data class ResolvedByPresence(
        override val alert: AlertId, override val at: Instant,
        public val presence: EventRef,
        public val secondsToStaff: Long,
    ) : AlarmEvent

    public data class ResolvedManually(
        override val alert: AlertId, override val at: Instant,
        public val by: StaffId,
        public val cause: String,
    ) : AlarmEvent
}

public data class AlertKey(public val bed: BedId, public val rule: RuleId, public val episode: EpisodeId)

public enum class Channel { PUSH, TABLET, WARD_BOARD, CONSOLE }

public enum class EscalationCause { NO_DELIVERY, NOT_SEEN, NOT_ACKNOWLEDGED, SILENCE_EXPIRED }
