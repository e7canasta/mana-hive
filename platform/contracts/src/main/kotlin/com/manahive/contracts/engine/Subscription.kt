package com.manahive.contracts.engine

/**
 * Represents an active subscription to configuration changes.
 *
 * Callers can cancel the subscription to stop receiving updates.
 *
 * Fowler: "Introduce Parameter Object" — encapsulates subscription lifecycle.
 *
 * Vernon: Value Object — no identity, compared by value.
 */
public interface Subscription {
    /**
     * Cancel this subscription.
     *
     * After cancellation, the callback will no longer be invoked.
     * Idempotent — calling cancel() multiple times is safe.
     */
    public fun cancel()

    /**
     * Check if this subscription is still active.
     *
     * A subscription is active until cancel() is called.
     */
    public val isActive: Boolean
}

/**
 * A subscription that tracks individual callbacks.
 *
 * Used by local mode adapters to allow granular unsubscription.
 *
 * @property onCancel Callback invoked when cancel() is called
 */
public class CallbackSubscription(
    private val onCancel: () -> Unit,
) : Subscription {
    @Volatile
    private var _active = true

    override val isActive: Boolean get() = _active

    override fun cancel() {
        if (_active) {
            _active = false
            onCancel()
        }
    }
}

/**
 * A subscription that does nothing on cancel.
 *
 * Useful for cases where subscription management is handled externally.
 */
public object NoOpSubscription : Subscription {
    override val isActive: Boolean = true
    override fun cancel() { /* no-op */ }
}
