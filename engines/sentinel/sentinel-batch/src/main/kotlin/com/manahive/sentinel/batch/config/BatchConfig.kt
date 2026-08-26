package com.manahive.sentinel.batch.config

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import com.manahive.sentinel.SentinelCalibration
import java.time.Duration
import java.time.Instant

/**
 * Root configuration for a sentinel-batch run.
 *
 * Rich Domain Model (Evans): the config knows how to create its own
 * domain objects — SentinelCalibration — without leaking framework details.
 *
 * Self-Validating Entity (Vernon): validates invariants on creation.
 *
 * NOTE: NotificationBudgetConfig is kept for backward compatibility but is NOT used
 * by Sentinel. Fatigue is a delivery concern handled by Harbor.
 */
data class BatchConfig(
    val resident: ResidentConfig,
    val rules: List<RuleConfig>,
    val fatigue: NotificationBudgetConfig,
    val events: EventsConfig,
) {
    init {
        require(resident.id.isNotBlank()) { "resident.id must not be blank" }
        require(resident.bed.isNotBlank()) { "resident.bed must not be blank" }
        require(resident.night.isNotBlank()) { "resident.night must not be blank" }
        require(rules.isNotEmpty()) { "at least one rule must be defined" }
        require(events.source.isNotBlank()) { "events.source must not be blank" }
    }

    // ── Convenience Accessors ───────────────────────────────────────────────

    val residentId: ResidentId get() = ResidentId(resident.id)
    val bedId: BedId get() = BedId(resident.bed)
    val nightId: NightId get() = NightId(resident.night)
    val startTime: Instant get() = events.start ?: Instant.now()

    // ── Domain Object Creation ──────────────────────────────────────────────

    /** Creates a [SentinelCalibration] from this config. */
    fun toSentinelCalibration(): SentinelCalibration {
        val alertRules = rules.map { it.toAlertRule() }
        val nonComeBack = alertRules.filter { it.triggerOn != TriggerOn.COME_BACK }
        val comeBack = alertRules.filter { it.triggerOn == TriggerOn.COME_BACK }
        return SentinelCalibration(
            residentId = residentId,
            rulesByState = nonComeBack.groupBy { it.trigger },
            transitionRules = alertRules
                .filter { it.triggerOn == TriggerOn.ENTRY }
                .associateBy { it.trigger },
            dwellRules = alertRules
                .filter { it.triggerOn == TriggerOn.DWELL }
                .associateBy { it.trigger },
            comeBackRules = comeBack.associateBy { it.trigger },
            sceneStateRules = emptyMap(),
            ruleIds = alertRules.map { it.id }.toSet(),
            fingerprint = alertRules.joinToString(",") { it.id.value },
        )
    }
}

data class ResidentConfig(
    val id: String,
    val bed: String,
    val night: String,
)

data class RuleConfig(
    val id: String,
    val trigger: StateKind,
    val triggerOn: TriggerOn = TriggerOn.DWELL,
    val severity: Severity = Severity.WARNING,
    val closure: ClosureCondition = ClosureCondition.SAFE_ONLY,
    val reversible: Boolean = true,
    val nvr: Boolean = false,
    val confirmation: Boolean = false,
    val confirmationWindow: Duration? = null,
    val umbrella: Set<StateKind> = emptySet(),
) {
    fun toAlertRule(): AlertRule = AlertRule(
        id = RuleId(id),
        trigger = trigger,
        triggerOn = triggerOn,
        severity = severity,
        closureCondition = closure,
        reversible = reversible,
        requiresConfirmation = confirmation,
        requiresNvr = nvr,
        confirmationWindow = confirmationWindow,
        umbrellaEvents = umbrella,
    )
}

/**
 * Fatigue configuration.
 *
 * NOTE: This is kept in sentinel-batch for YAML backward compatibility.
 * Sentinel itself does NOT use fatigue. This config will be moved to
 * harbor-batch in a future refactor.
 */
data class NotificationBudgetConfig(
    val maxPerShift: Int = 5,
)

data class EventsConfig(
    val source: String,
    val output: String = "output",
    val start: Instant? = null,
)
