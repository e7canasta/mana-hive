package com.manahive.vigia

import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.contracts.alarm.AlertKey
import com.manahive.contracts.alarm.Channel
import com.manahive.contracts.sentinel.Severity
import com.manahive.kernel.Decider
import com.manahive.kernel.EventRef
import com.manahive.kernel.RejectionReason
import com.manahive.kernel.StaffId
import java.time.Instant

/**
 * The alert as a pure Decider over the published AlarmEvent language.
 * Two invariants with an owner: one alert per AlertKey (the key is the
 * aggregate identity), and RESOLVED IS ABSORBING — no command revives a
 * resolved alert; if the world insists, it is a new episode.
 *
 * Implementation is Sprint-1 story S6 — TDD from the given/when/then DSL.
 */
public interface AlertLifecycle : Decider<AlertCommand, AlertState, AlarmEvent>

public sealed interface AlertCommand {
    public data class Raise(public val key: AlertKey, public val severity: Severity, public val origin: EventRef) : AlertCommand
    public data class OrderDelivery(public val step: Int, public val channel: Channel, public val recipients: List<StaffId>) : AlertCommand
    public data class RecordDelivery(public val step: Int) : AlertCommand
    public data class MarkSeen(public val by: StaffId) : AlertCommand
    public data class Acknowledge(public val by: StaffId) : AlertCommand
    public data class Escalate(public val toStep: Int, public val cause: com.manahive.contracts.alarm.EscalationCause) : AlertCommand
    public data class Silence(public val until: Instant, public val by: StaffId, public val reason: String) : AlertCommand
    public data class ResolveByPresence(public val presence: EventRef, public val secondsToStaff: Long) : AlertCommand
    public data class ResolveManually(public val by: StaffId, public val cause: String) : AlertCommand
}

public data class AlertState(
    public val phase: Phase = Phase.NONE,
    public val key: AlertKey? = null,
    public val currentStep: Int = 0,
    public val silencedUntil: Instant? = null,
) {
    public enum class Phase { NONE, RAISED, ROUTED, DELIVERED, SEEN, ACKNOWLEDGED, SILENCED, RESOLVED }
}

public enum class AlertRejection(override val code: String) : RejectionReason {
    ALREADY_EXISTS("alert.already-exists"),
    ALREADY_RESOLVED("alert.already-resolved"),
    UNKNOWN_ALERT("alert.unknown"),
    ILLEGAL_PHASE("alert.illegal-phase"),
}
