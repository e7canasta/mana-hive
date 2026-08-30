package com.manahive.contracts.notice

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.NoticeId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.kernel.StaffId
import java.time.Duration
import java.time.Instant

/**
 * Notice lifecycle events.
 *
 * Published on `notice.event.v1.<notice>`. The full lifecycle:
 *   Dispatch → Sent → Delivered → Seen → Confirmed → Resolved
 *                                                ↘ Escalated
 *                                                ↘ Expired
 *
 * Any subscriber (sender, app, dashboard) can emit these events.
 * Hive doesn't know who the subscribers are — the bus is the contract.
 */
public sealed interface NoticeEvent {
    public val noticeId: NoticeId
    public val at: Instant

    /**
     * Harbor dispatches a notice to staff.
     *
     * This is the "go" signal. The sender reads this and sends
     * PUSH/TABLET/WARD_BOARD notifications.
     */
    public data class Dispatch(
        override val noticeId: NoticeId,
        override val at: Instant,
        val bed: BedId,
        val episode: EpisodeId,
        val resident: ResidentId?,
        val severity: Severity,
        val channels: Set<Channel>,
        val recipients: List<StaffId>,
        val message: String,
        val context: NoticeContext,
    ) : NoticeEvent

    /**
     * Sender confirms the notification was sent to the channel.
     *
     * Emitted by: sender/NVR after sending PUSH/TABLET.
     */
    public data class Sent(
        override val noticeId: NoticeId,
        override val at: Instant,
        val channel: Channel,
        val recipientCount: Int,
    ) : NoticeEvent

    /**
     * Push provider confirms the device received the notification.
     *
     * Emitted by: push provider (FCM/APNS callback).
     */
    public data class Delivered(
        override val noticeId: NoticeId,
        override val at: Instant,
        val channel: Channel,
    ) : NoticeEvent

    /**
     * Staff saw the notification (opened the app, tapped the alert).
     *
     * Emitted by: app when user opens the notification.
     */
    public data class Seen(
        override val noticeId: NoticeId,
        override val at: Instant,
        val by: StaffId,
    ) : NoticeEvent

    /**
     * Staff confirmed they are handling it.
     *
     * Emitted by: app when user taps "Confirm" / "I'm on it".
     */
    public data class Confirmed(
        override val noticeId: NoticeId,
        override val at: Instant,
        val by: StaffId,
        val message: String? = null,
    ) : NoticeEvent

    /**
     * Notice was escalated because no one responded in time.
     *
     * Emitted by: scheduler when confirmationWindow expires.
     */
    public data class Escalated(
        override val noticeId: NoticeId,
        override val at: Instant,
        val cause: EscalationCause,
        val escalatedTo: List<StaffId>,
    ) : NoticeEvent

    /**
     * Notice expired without any response.
     *
     * Emitted by: scheduler when maxRetryCount is reached.
     */
    public data class Expired(
        override val noticeId: NoticeId,
        override val at: Instant,
        val lastAttemptAt: Instant?,
    ) : NoticeEvent

    /**
     * Notice resolved — episode closed.
     *
     * Emitted by: harbor when episode is resolved.
     */
    public data class Resolved(
        override val noticeId: NoticeId,
        override val at: Instant,
        val resolution: NoticeResolution,
        val resolvedBy: StaffId?,
        val duration: Duration,
    ) : NoticeEvent
}

/**
 * Context for the notice — what happened, why.
 *
 * The sender uses this to compose the notification message.
 */
public data class NoticeContext(
    val episode: EpisodeId,
    val rule: RuleId,
    val baseline: String,
    val trigger: String,
    val duration: Duration,
    val description: String,
)

/**
 * How the notice was resolved.
 */
public enum class NoticeResolution {
    STAFF_CONFIRMED,    // staff confirmed they're handling it
    STAFF_PRESENT,      // staff went to the room
    AUTO_RECOVERY,      // resident recovered on their own
    SUPERSEDED,         // replaced by a higher-severity notice
    CANCELLED,          // cancelled by operator
}

/**
 * Why the notice was escalated.
 */
public enum class EscalationCause {
    NOT_SENT,           // sender couldn't deliver
    NOT_DELIVERED,      // device didn't receive
    NOT_SEEN,           // staff didn't open it
    NOT_CONFIRMED,      // staff didn't confirm in time
    EXPIRED,            // max retries reached
}
