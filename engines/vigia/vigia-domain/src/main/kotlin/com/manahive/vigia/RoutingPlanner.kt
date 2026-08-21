package com.manahive.vigia

import com.manahive.contracts.alarm.AlertKey
import com.manahive.contracts.alarm.Channel
import com.manahive.contracts.sentinel.Severity
import com.manahive.kernel.AlertId
import com.manahive.kernel.BedId
import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import com.manahive.kernel.StaffId
import java.time.Duration
import java.time.Instant

/**
 * The dispatcher: the right alert to the right person through the right
 * channel, with the whole escalation ladder computed UP FRONT — so the
 * lifecycle process only executes, and the full plan is inspectable before
 * the first delivery.
 *
 * Responsible for: suitability (active shift, ward covered, role allowed for
 * severity); load fairness (recent load comes in the coverage snapshot);
 * channel by severity (CRITICAL interrupts through every channel); and the
 * TERMINAL step — every ladder ends where it cannot fail silently, because
 * an alert with no suitable recipient is itself an alarm.
 */
public interface RoutingPlanner : Engine {
    public fun plan(
        alert: AlertToRoute,
        coverage: CoverageSnapshot,
        presence: PresenceSnapshot,
        ladder: EscalationLadder,
    ): Explained<DeliveryPlan>
}

public data class AlertToRoute(
    public val alert: AlertId,
    public val key: AlertKey,
    public val severity: Severity,
)

/** Non-empty by construction; the terminal step is required by type, not by review. */
public data class DeliveryPlan(public val steps: List<DeliveryStep>) {
    init { require(steps.isNotEmpty()) { "a delivery plan always ends in a terminal step" } }
}

public data class DeliveryStep(
    public val recipients: List<StaffId>,
    public val channel: Channel,
    public val timeout: Duration,
    public val whyChosen: String,
)

public data class EscalationLadder(
    public val steps: List<LadderStep>,
    public val terminal: LadderStep,
)

public data class LadderStep(
    public val role: StaffRole,
    public val channel: Channel,
    public val timeout: Duration,
)

public enum class StaffRole { WARD_NURSE, SHIFT_NURSE, ON_CALL_MANAGER, WARD_BOARD }

/** Who is on duty, where, with what recent load. Provided by the hub. */
public data class CoverageSnapshot(
    public val at: Instant,
    public val onDuty: List<OnDuty>,
)

public data class OnDuty(
    public val staff: StaffId,
    public val role: StaffRole,
    public val ward: String,
    public val recentDeliveries: Int,
)

/** Who is physically in which room right now. Derived from scene facts. */
public data class PresenceSnapshot(
    public val at: Instant,
    public val byBed: Map<BedId, StaffId>,
)
