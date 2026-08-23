package com.manahive.contracts.policy

import java.time.Duration

/**
 * Escalation configuration.
 * Defines how alerts escalate over time and staff assist rules.
 *
 * Used by Harbor Engine to manage alarm escalation.
 */
public data class EscalationConfig(
    /** Delay before escalating to next level. */
    public val escalationDelay: Duration,
    /** Staff assist mode: virtual, optional, or obligatory. */
    public val staffAssist: StaffAssistMode,
    /** Maximum escalation level. */
    public val maxLevel: Int,
)

/**
 * Staff assist modes.
 *
 * NONE: no staff assist
 * VIRTUAL: virtual notification only
 * OPTIONAL: staff can choose to assist
 * OBLIGATORY: staff must assist
 */
public enum class StaffAssistMode {
    NONE,
    VIRTUAL,
    OPTIONAL,
    OBLIGATORY,
}
