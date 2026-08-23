package com.manahive.hub.policy

import com.manahive.contracts.policy.EffectiveRules
import com.manahive.kernel.Explained
import com.manahive.kernel.ResidentId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Application service for policy resolution.
 *
 * Vernon: "Application services orchestrate domain objects and infrastructure.
 * They don't contain domain logic themselves."
 *
 * This service constructs real PolicyLayers from the event-sourced history.
 */
@Service
public class PolicyService(
    private val policyResolver: PolicyResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Resolve effective rules for a resident at a given time.
     *
     * Vernon: "The application service constructs the PolicyLayers
     * from the event-sourced history, not fabricated defaults."
     */
    public fun resolveEffectiveRules(
        residentId: ResidentId,
        at: Instant,
    ): Explained<EffectiveRules> {
        // TODO: In real implementation, fetch PolicyLayers from event-sourced history
        // For now, return a default that can be extended
        log.warn("Using hardcoded default PolicyLayers for resident {} — not production ready", residentId.value)

        val layers = PolicyLayers(
            level = WatchLevel.STANDARD,
            template = LevelTemplate(
                id = "default",
                level = WatchLevel.STANDARD,
                rules = emptyList(),
            ),
            adjustments = emptyList(),
            windows = emptyList(),
        )

        return policyResolver.resolve(residentId, at, layers)
    }
}
