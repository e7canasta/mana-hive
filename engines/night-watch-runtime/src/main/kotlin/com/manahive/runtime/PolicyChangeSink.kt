package com.manahive.runtime

import com.manahive.contracts.policy.PolicyChangeDetected

/**
 * Receives policy changes from any source.
 */
interface PolicyChangeSink {
    fun onPolicyChange(change: PolicyChangeDetected)
}
