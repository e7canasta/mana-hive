package com.manahive.harbor

import com.manahive.contracts.policy.Severity

/**
 * Delivery budget: controls how many notifications per shift by severity.
 *
 * This is a delivery concern, NOT a clinical judgment concern.
 * Sentinel always opens episodes (facts are facts).
 * Harbor decides whether to dispatch notifications based on budget.
 *
 * CRITICAL episodes are NEVER suppressed — life-safety always wins.
 */
public data class NotificationBudget(
    /** Maximum notifications per severity per shift. CRITICAL is excluded (always deliver). */
    public val budgets: Map<Severity, BudgetEntry> = emptyMap(),
) {
    /** Check if we can deliver a notification for this severity. */
    public fun canDeliver(severity: Severity): Boolean {
        if (severity == Severity.CRITICAL) return true
        return budgets[severity]?.exceeded != true
    }

    /** Track a dispatched notification. Returns a new instance with incremented counter. */
    public fun track(severity: Severity): NotificationBudget {
        if (severity == Severity.CRITICAL) return this
        val budget = budgets[severity] ?: return this
        return copy(budgets = budgets + (severity to budget.increment()))
    }
}

/**
 * Fatigue budget for a single severity level.
 *
 * Tracks dispatched notifications vs maximum allowed per shift.
 */
public data class BudgetEntry(
    public val dispatched: Int = 0,
    public val maxPerShift: Int = 5,
) {
    public val exceeded: Boolean get() = dispatched >= maxPerShift

    public fun increment(): BudgetEntry = copy(dispatched = dispatched + 1)
}
