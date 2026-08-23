package com.manahive.sentinel.config

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Duration

class SentinelConfigSpec : DescribeSpec({

    describe("SentinelConfig") {
        it("creates with valid fields") {
            val config = SentinelConfig(
                residentId = "maria",
                rules = listOf(
                    AlertRule(
                        id = RuleId("r-fall"),
                        trigger = StateKind.BED_EDGE,
                        severity = Severity.CRITICAL,
                        closureCondition = ClosureCondition.STAFF_AND_SAFE,
                        reversible = false,
                        requiresConfirmation = true,
                        requiresNvr = true,
                        confirmationWindow = Duration.ofSeconds(30),
                        umbrellaEvents = setOf(StateKind.STANDING, StateKind.ATTEMPTING_EXIT),
                    )
                ),
                maxAlertsPerShift = 5,
                fingerprint = "abc123",
            )

            config.residentId shouldBe "maria"
            config.rules.size shouldBe 1
            config.maxAlertsPerShift shouldBe 5
            config.fingerprint shouldBe "abc123"
        }

        it("rejects blank residentId") {
            shouldThrow<IllegalArgumentException> {
                SentinelConfig(residentId = "")
            }
        }

        it("rejects zero maxAlertsPerShift") {
            shouldThrow<IllegalArgumentException> {
                SentinelConfig(residentId = "maria", maxAlertsPerShift = 0)
            }
        }

        it("rejects negative maxAlertsPerShift") {
            shouldThrow<IllegalArgumentException> {
                SentinelConfig(residentId = "maria", maxAlertsPerShift = -1)
            }
        }

        it("uses default values") {
            val config = SentinelConfig(residentId = "maria")

            config.rules shouldBe emptyList()
            config.maxAlertsPerShift shouldBe 5
            config.fingerprint shouldBe ""
        }

        it("indexes rules by trigger") {
            val config = SentinelConfig(
                residentId = "maria",
                rules = listOf(
                    AlertRule(
                        id = RuleId("r-fall"),
                        trigger = StateKind.BED_EDGE,
                        severity = Severity.CRITICAL,
                        closureCondition = ClosureCondition.STAFF_AND_SAFE,
                        reversible = false,
                        requiresConfirmation = false,
                        requiresNvr = false,
                        confirmationWindow = null,
                        umbrellaEvents = emptySet(),
                    ),
                    AlertRule(
                        id = RuleId("r-sit"),
                        trigger = StateKind.SITTING_IN_BED,
                        severity = Severity.WARNING,
                        closureCondition = ClosureCondition.SAFE_ONLY,
                        reversible = true,
                        requiresConfirmation = false,
                        requiresNvr = false,
                        confirmationWindow = null,
                        umbrellaEvents = emptySet(),
                    ),
                ),
            )

            config.rulesByTrigger[StateKind.BED_EDGE]?.id shouldBe RuleId("r-fall")
            config.rulesByTrigger[StateKind.SITTING_IN_BED]?.id shouldBe RuleId("r-sit")
        }

        it("finds rule by trigger") {
            val config = SentinelConfig(
                residentId = "maria",
                rules = listOf(
                    AlertRule(
                        id = RuleId("r-fall"),
                        trigger = StateKind.BED_EDGE,
                        severity = Severity.CRITICAL,
                        closureCondition = ClosureCondition.STAFF_AND_SAFE,
                        reversible = false,
                        requiresConfirmation = false,
                        requiresNvr = false,
                        confirmationWindow = null,
                        umbrellaEvents = setOf(StateKind.STANDING),
                    ),
                ),
            )

            config.ruleFor(StateKind.BED_EDGE)?.id shouldBe RuleId("r-fall")
            config.ruleFor(StateKind.STANDING) shouldBe null
        }

        it("gets notifiable states for trigger") {
            val config = SentinelConfig(
                residentId = "maria",
                rules = listOf(
                    AlertRule(
                        id = RuleId("r-fall"),
                        trigger = StateKind.BED_EDGE,
                        severity = Severity.CRITICAL,
                        closureCondition = ClosureCondition.STAFF_AND_SAFE,
                        reversible = false,
                        requiresConfirmation = false,
                        requiresNvr = false,
                        confirmationWindow = null,
                        umbrellaEvents = setOf(StateKind.STANDING, StateKind.ATTEMPTING_EXIT),
                    ),
                ),
            )

            config.notifiableStatesFor(StateKind.BED_EDGE) shouldBe setOf(
                StateKind.STANDING,
                StateKind.ATTEMPTING_EXIT,
            )
        }

        it("returns empty set for unknown trigger") {
            val config = SentinelConfig(residentId = "maria")

            config.notifiableStatesFor(StateKind.BED_EDGE) shouldBe emptySet()
        }
    }
})
