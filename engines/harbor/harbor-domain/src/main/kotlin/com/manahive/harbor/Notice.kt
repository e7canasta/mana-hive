package com.manahive.harbor

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.NoticeId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import java.time.Duration
import java.time.Instant

/**
 * The unified notification concept.
 *
 * "Aviso" in business language. One type, three severity levels:
 * - NOTICE: informational, no action required
 * - ALERT: requires confirmation from staff
 * - INCIDENT: requires immediate action
 *
 * Fowler: "Replace Conditional with Polymorphism" — severity is data, not types.
 * Vernon: "Ubiquitous Language" — staff says "I got a notice" (me llegó un aviso).
 */
public data class Notice(
    val id: NoticeId,
    val episode: EpisodeId,
    val bed: BedId,
    val resident: ResidentId?,
    val severity: Severity,
    val state: NoticeState,
    val channels: Set<Channel>,
    val createdAt: Instant,
    val seenAt: Instant?,
    val acknowledgedAt: Instant?,
    val escalatedAt: Instant?,
    val resolvedAt: Instant?,
    val resolution: Resolution?,
) {
    /** Does this notice require staff action? */
    val requiresAction: Boolean get() = severity != Severity.INFO

    /** Does this notice require immediate action? */
    val isUrgent: Boolean get() = severity == Severity.CRITICAL

    /**
     * Si este aviso espera que alguien vaya a la habitacion.
     *
     * Es distinto de [isUrgent]: HIGH manda a alguien, pero no interrumpe todo.
     */
    val requiresAttendance: Boolean get() = severity.requiresAttendance

    /** Confirmation window based on severity. */
    public val confirmationWindow: Duration? get() = when (severity) {
        Severity.INFO -> null
        Severity.WARNING -> Duration.ofMinutes(5)
        // Se espera menos que en un aviso porque alguien tiene que ir; pero no
        // es cero, porque no es una emergencia y el turno puede estar ocupado.
        Severity.HIGH -> Duration.ofMinutes(2)
        Severity.CRITICAL -> Duration.ZERO
    }

    public companion object {
        /** Create a new notice from a Sentinel signal. */
        public fun from(signal: SentinelSignal.EpisodeOpened): Notice = Notice(
            id = NoticeId("${signal.episode.value}-notice"),
            episode = signal.episode,
            bed = signal.bed,
            resident = signal.resident,
            severity = signal.severity,
            state = NoticeState.CREATED,
            channels = emptySet(),
            createdAt = signal.at,
            seenAt = null,
            acknowledgedAt = null,
            escalatedAt = null,
            resolvedAt = null,
            resolution = null,
        )
    }
}

/**
 * Notice lifecycle states.
 *
 * Fowler: "State Pattern" — transitions are enforced by type.
 * One path: CREATED → DISPATCHED → SEEN → ACKNOWLEDGED → RESOLVED
 * Alternate paths: → ESCALATED (timeout), → CANCELLED (superseded)
 */
public enum class NoticeState {
    CREATED,      // created, pending dispatch
    DISPATCHED,   // sent to staff
    SEEN,         // staff saw it
    ACKNOWLEDGED, // staff confirmed
    ESCALATED,    // escalated (no confirmation)
    CANCELLED,    // cancelled (superseded by another notice)
    RESOLVED,     // resolved (staff assisted)
}

/**
 * How a notice was resolved.
 */
public enum class Resolution {
    STAFF_PRESENT,    // staff went and assisted
    AUTO_RECOVERY,    // resident recovered on their own
    SUPERSEDED,       // another notice of higher severity replaced this
}
