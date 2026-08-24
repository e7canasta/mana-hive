package com.manahive.harbor

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.kernel.BedId
import com.manahive.kernel.Engine
import com.manahive.kernel.EngineVersion
import com.manahive.kernel.Explained
import com.manahive.kernel.ExplanationStep
import com.manahive.kernel.NoticeId
import com.manahive.kernel.StaffId
import java.time.Duration
import java.time.Instant

/**
 * The notice router: determines who gets notified, through which channel,
 * and when to escalate.
 *
 * Fowler: "Strategy Pattern" — routing logic is pluggable.
 * Vernon: "Domain Service" — coordinates across aggregates.
 *
 * Responsible for:
 * - Channel selection based on severity
 * - Recipient selection based on coverage
 * - Escalation ladder computation
 * - Timeout management
 *
 * NOT responsible for:
 * - Delivery execution (external adapters)
 * - Notice lifecycle (NoticeLifecycle)
 */
public interface NoticeRouter : Engine {
    public fun route(
        notice: Notice,
        coverage: CoverageSnapshot,
        now: Instant,
    ): Explained<RoutingPlan>
}

/**
 * The routing plan for a notice.
 */
public data class RoutingPlan(
    public val noticeId: NoticeId,
    public val steps: List<RoutingStep>,
    public val escalationTimeout: Duration?,
)

/**
 * A single step in the routing plan.
 */
public data class RoutingStep(
    public val recipients: List<StaffId>,
    public val channel: Channel,
    public val timeout: Duration,
    public val reason: String,
)

/**
 * Who is on duty, where, with what recent load.
 */
public data class CoverageSnapshot(
    public val at: Instant,
    public val onDuty: List<OnDuty>,
)

/**
 * A staff member on duty.
 */
public data class OnDuty(
    public val staff: StaffId,
    public val role: StaffRole,
    public val ward: String,
    public val recentDeliveries: Int,
)

/**
 * Staff roles for routing.
 */
public enum class StaffRole {
    WARD_NURSE,
    SHIFT_NURSE,
    ON_CALL_MANAGER,
    WARD_BOARD,
}

/**
 * Default implementation of NoticeRouter.
 *
 * Simple routing: severity determines channels, first available staff gets it.
 */
public class DefaultNoticeRouter : NoticeRouter {

    override val version: EngineVersion = EngineVersion(
        name = "notice-router",
        semver = "0.1.0",
        buildFingerprint = "local-dev",
    )

    override fun route(
        notice: Notice,
        coverage: CoverageSnapshot,
        now: Instant,
    ): Explained<RoutingPlan> {
        val steps = mutableListOf<RoutingStep>()
        val explanation = mutableListOf<ExplanationStep>()

        // Route based on severity
        when (notice.severity) {
            Severity.INFO -> {
                steps.add(RoutingStep(
                    recipients = findWardNurses(coverage),
                    channel = Channel.CONSOLE,
                    timeout = Duration.ofMinutes(30),
                    reason = "informational, console only",
                ))
                explanation.add(ExplanationStep(
                    rule = "info-routing",
                    observed = "severity=INFO",
                    conclusion = "console only, no urgency",
                ))
            }
            Severity.WARNING -> {
                steps.add(RoutingStep(
                    recipients = findWardNurses(coverage),
                    channel = Channel.PUSH,
                    timeout = Duration.ofMinutes(5),
                    reason = "warning, push notification",
                ))
                steps.add(RoutingStep(
                    recipients = findShiftNurses(coverage),
                    channel = Channel.TABLET,
                    timeout = Duration.ofMinutes(10),
                    reason = "warning escalation, tablet",
                ))
                explanation.add(ExplanationStep(
                    rule = "warning-routing",
                    observed = "severity=WARNING",
                    conclusion = "push to ward nurse, tablet to shift nurse",
                ))
            }
            Severity.CRITICAL -> {
                steps.add(RoutingStep(
                    recipients = findWardNurses(coverage),
                    channel = Channel.PUSH,
                    timeout = Duration.ZERO,
                    reason = "critical, immediate push",
                ))
                steps.add(RoutingStep(
                    recipients = findShiftNurses(coverage),
                    channel = Channel.TABLET,
                    timeout = Duration.ofSeconds(30),
                    reason = "critical, tablet backup",
                ))
                steps.add(RoutingStep(
                    recipients = findOnCallManager(coverage),
                    channel = Channel.WARD_BOARD,
                    timeout = Duration.ofMinutes(1),
                    reason = "critical, ward board alert",
                ))
                explanation.add(ExplanationStep(
                    rule = "critical-routing",
                    observed = "severity=CRITICAL",
                    conclusion = "all channels, immediate delivery",
                ))
            }
        }

        val escalationTimeout = when (notice.severity) {
            Severity.INFO -> null
            Severity.WARNING -> Duration.ofMinutes(5)
            Severity.CRITICAL -> Duration.ZERO
        }

        return Explained(
            value = RoutingPlan(
                noticeId = notice.id,
                steps = steps,
                escalationTimeout = escalationTimeout,
            ),
            explanation = explanation,
        )
    }

    private fun findWardNurses(coverage: CoverageSnapshot): List<StaffId> =
        coverage.onDuty.filter { it.role == StaffRole.WARD_NURSE }.map { it.staff }

    private fun findShiftNurses(coverage: CoverageSnapshot): List<StaffId> =
        coverage.onDuty.filter { it.role == StaffRole.SHIFT_NURSE }.map { it.staff }

    private fun findOnCallManager(coverage: CoverageSnapshot): List<StaffId> =
        coverage.onDuty.filter { it.role == StaffRole.ON_CALL_MANAGER }.map { it.staff }
}
