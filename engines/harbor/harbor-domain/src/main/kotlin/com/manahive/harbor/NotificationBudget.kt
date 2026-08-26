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

    /** El tope configurado para esta severidad. Sin entrada = sin tope. */
    public fun maxFor(severity: Severity): Int =
        budgets[severity]?.maxPerShift ?: Int.MAX_VALUE

    /**
     * Registra un aviso despachado.
     *
     * [maxPerShift] viene de la calibración, porque el estado arranca con el
     * mapa vacío y hay que sembrar la entrada la primera vez. Antes esto hacía
     * `budgets[severity] ?: return this`: como el estado siempre empezaba
     * vacío, **el contador no se movía nunca** y el presupuesto no suprimía
     * nada. La fatiga —que es la razón de ser de Harbor— estaba inerte.
     */
    public fun track(severity: Severity, maxPerShift: Int): NotificationBudget {
        if (severity == Severity.CRITICAL) return this
        val entry = budgets[severity] ?: BudgetEntry(dispatched = 0, maxPerShift = maxPerShift)
        return copy(budgets = budgets + (severity to entry.increment()))
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
