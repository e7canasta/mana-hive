package com.manahive.politica.batch

import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.politica.DefaultPolicyChangeProcessor
import com.manahive.politica.PolicyChangeResult

/**
 * Stateless batch processor for politica-engine.
 *
 * Each event is independent — no state accumulation, no time progression.
 * Input: PolicyChangeDetected → Output: CalibrationChanged
 */
object PolicyBatchProcessor {

    fun run(events: List<PolicyChangeDetected>): List<PolicyChangeResult> {
        val processor = DefaultPolicyChangeProcessor()
        return events.map { processor.process(it, it.at) }
    }
}
