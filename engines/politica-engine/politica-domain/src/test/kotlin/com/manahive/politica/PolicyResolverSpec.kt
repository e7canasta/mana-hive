package com.manahive.politica

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.PolicyOverride
import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant

/**
 * PolicyResolver — DAG-centric resolution.
 *
 * Tests the canonical path: DagCatalog + AlarmProfile → PolicyCalibration.
 * The legacy AlarmCatalog overload has been retired (SPEC-03).
 */
class PolicyResolverSpec : BehaviorSpec({

    Given("a DAG catalog with resident states and transitions") {
        val catalog = buildDagCatalog {
            version("1.0.0")
            resident {
                sitting {
                    warningAfter(Duration.ofMinutes(3))
                    alertAfter(Duration.ofMinutes(5))
                    severity(Severity.WARNING)
                    closure(ClosureCondition.STAFF_OR_SAFE)
                }
                bedEdge {
                    alertOnEntry()
                    severity(Severity.CRITICAL)
                    closure(ClosureCondition.STAFF_AND_SAFE)
                }
            }
            transitions {
                from(StateKind.LYING) {
                    to(StateKind.BED_EDGE) { hysteresis(Duration.ofMillis(1500)) }
                    to(StateKind.STANDING) { hysteresis(Duration.ofMillis(2000)) }
                }
                from(StateKind.STANDING) {
                    to(StateKind.IN_BATHROOM) {
                        hysteresis(Duration.ofMillis(2000))
                        record(before = Duration.ofMinutes(2), after = Duration.ofMinutes(5))
                    }
                }
            }
        }

        And("a profile with overrides") {
            val profile = AlarmProfile(
                residentId = ResidentId("maria"),
                riskLevel = RiskLevel.HIGH,
                mobilityAid = MobilityAid.WALKER,
                autopilot = false,
                mode = PolicyMode.CUSTOM,
                templateId = null,
                overrides = mapOf(
                    RuleId("dwell-SITTING_IN_BED") to PolicyOverride.DwellOverride(
                        ruleId = RuleId("dwell-SITTING_IN_BED"),
                        state = StateKind.SITTING_IN_BED,
                        value = DwellThreshold(
                            warning = Duration.ofMinutes(1),
                            exceeded = Duration.ofMinutes(2),
                        ),
                    ),
                ),
                catalogVersion = com.manahive.contracts.policy.CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resolved") {
                val result = PolicyResolver.resolve(catalog, profile)
                val calibration = result.value

                Then("the calibration is explained") {
                    result.explanation shouldNotBe emptyList<com.manahive.kernel.ExplanationStep>()
                }

                Then("the residentId is correct") {
                    calibration.residentId shouldBe ResidentId("maria")
                }

                Then("dwell SITTING_IN_BED comes from the override (1 min warning, 2 min exceeded)") {
                    calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]?.warning shouldBe Duration.ofMinutes(1)
                    calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]?.exceeded shouldBe Duration.ofMinutes(2)
                }

                Then("hysteresis LYING → BED_EDGE comes from the catalog (1.5s)") {
                    calibration.scene.hysteresis[TransitionKey(StateKind.LYING, StateKind.BED_EDGE)] shouldBe Duration.ofMillis(1500)
                }

                Then("transition window LYING → STANDING is recorded") {
                    val window = calibration.recorder.transitionWindows[TransitionKey(StateKind.STANDING, StateKind.IN_BATHROOM)]
                    window shouldNotBe null
                    window!!.before shouldBe Duration.ofMinutes(2)
                    window.after shouldBe Duration.ofMinutes(5)
                }

                Then("alert rule for SITTING_IN_BED has DWELL trigger") {
                    val rule = calibration.sentinel.alertRules[StateKind.SITTING_IN_BED]
                    rule shouldNotBe null
                    rule!!.triggerOn shouldBe TriggerOn.DWELL
                    rule.severity shouldBe Severity.WARNING
                }

                Then("alert rule for BED_EDGE has ENTRY trigger") {
                    val rule = calibration.sentinel.alertRules[StateKind.BED_EDGE]
                    rule shouldNotBe null
                    rule!!.triggerOn shouldBe TriggerOn.ENTRY
                    rule.severity shouldBe Severity.CRITICAL
                }

                Then("source is OVERRIDE") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.OVERRIDE
                }
            }
        }

        And("a profile without overrides") {
            val profile = AlarmProfile(
                residentId = ResidentId("jose"),
                riskLevel = RiskLevel.LOW,
                mobilityAid = MobilityAid.NONE,
                autopilot = true,
                mode = PolicyMode.PRESET,
                templateId = null,
                overrides = emptyMap(),
                catalogVersion = com.manahive.contracts.policy.CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resolved") {
                val calibration = PolicyResolver.resolve(catalog, profile).value

                Then("dwell SITTING_IN_BED comes from the catalog (3 min warning, 5 min exceeded)") {
                    calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]?.warning shouldBe Duration.ofMinutes(3)
                    calibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]?.exceeded shouldBe Duration.ofMinutes(5)
                }

                Then("source is CATALOG") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.CATALOG
                }
            }
        }
    }

    Given("an empty DAG catalog") {
        val catalog = buildDagCatalog {
            version("1.0.0")
            resident { }
            transitions { }
        }

        And("a profile without overrides") {
            val profile = AlarmProfile(
                residentId = ResidentId("empty"),
                riskLevel = RiskLevel.LOW,
                mobilityAid = MobilityAid.NONE,
                autopilot = true,
                mode = PolicyMode.PRESET,
                templateId = null,
                overrides = emptyMap(),
                catalogVersion = com.manahive.contracts.policy.CatalogVersion("1.0.0"),
                validFrom = Instant.parse("2026-08-21T03:00:00Z"),
            )

            When("resolved") {
                val calibration = PolicyResolver.resolve(catalog, profile).value

                Then("hysteresis is empty") {
                    calibration.scene.hysteresis.isEmpty() shouldBe true
                }

                Then("dwell thresholds are empty") {
                    calibration.scene.dwellThresholds.isEmpty() shouldBe true
                }

                Then("source is CATALOG") {
                    PolicyResolver.resolveSource(profile) shouldBe PolicySource.CATALOG
                }
            }
        }
    }
})
