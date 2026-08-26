package com.manahive.politica

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.policy.buildResidentProfile
import com.manahive.contracts.scene.StateKind
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration

/**
 * SPEC-05: the come-back chain, at the resolver boundary.
 *
 * These cover the two places where a come-back rule shares a key with a
 * dwell rule for the same state — the identity of the rule, and the
 * precedence of a per-resident override.
 */
class ComeBackResolutionSpec : BehaviorSpec({

    fun profileOf(build: com.manahive.contracts.policy.ResidentProfileBuilder.() -> Unit = {}) =
        buildResidentProfile("jose") {
            risk(RiskLevel.LOW)
            mobility(MobilityAid.NONE)
            level(WatchLevel.STANDARD)
            build()
        }.profile

    // ── Rule identity ────────────────────────────────────────────────────

    Given("a catalog that watches time IN lying and time AWAY FROM lying") {
        val catalog = buildDagCatalog {
            resident {
                lying {
                    warningAfter(Duration.ofMinutes(50))
                    alertAfter(Duration.ofMinutes(60))
                    severity(Severity.INFO)
                    closure(ClosureCondition.SAFE_ONLY)
                }
                comeBackTo(StateKind.LYING) {
                    warningAfter(Duration.ofMinutes(12))
                    alertAfter(Duration.ofMinutes(15))
                    severity(Severity.WARNING)
                    closure(ClosureCondition.STAFF_OR_SAFE)
                }
            }
        }

        When("resolved") {
            val sentinel = PolicyResolver.resolve(catalog, profileOf()).value.sentinel

            Then("both rules survive resolution") {
                sentinel.alertRules[StateKind.LYING] shouldNotBe null
                sentinel.comeBackRules[StateKind.LYING] shouldNotBe null
            }

            Then("they are two DIFFERENT rules, with different ids") {
                // Two questions — "has he been in bed too long" and "has he not
                // come back to bed" — are two rules. Sharing one RuleId means
                // any consumer keyed by rule id keeps only one of them.
                val dwellId = sentinel.alertRules.getValue(StateKind.LYING).id
                val comeBackId = sentinel.comeBackRules.getValue(StateKind.LYING).id
                dwellId shouldNotBe comeBackId
            }

            Then("each carries its own trigger family") {
                sentinel.alertRules.getValue(StateKind.LYING).triggerOn shouldBe TriggerOn.DWELL
                sentinel.comeBackRules.getValue(StateKind.LYING).triggerOn shouldBe TriggerOn.COME_BACK
            }
        }
    }

    // ── Override precedence ──────────────────────────────────────────────

    Given("a catalog come-back rule and a per-resident override of it") {
        val catalog = buildDagCatalog {
            resident {
                comeBackTo(StateKind.LYING) {
                    warningAfter(Duration.ofMinutes(12))
                    alertAfter(Duration.ofMinutes(15))
                    severity(Severity.WARNING)
                    closure(ClosureCondition.STAFF_OR_SAFE)
                }
            }
        }
        val profile = profileOf {
            comeBack(StateKind.LYING) {
                warningAfter(Duration.ofMinutes(20))
                alertAfter(Duration.ofMinutes(30))
                severity(Severity.CRITICAL)
                closure(ClosureCondition.STAFF_AND_SAFE)
            }
        }

        When("resolved") {
            val calibration = PolicyResolver.resolve(catalog, profile).value

            Then("the override's threshold wins") {
                val threshold = calibration.scene.comeBackThresholds.getValue(StateKind.LYING)
                threshold.exceeded shouldBe Duration.ofMinutes(30)
                threshold.warning shouldBe Duration.ofMinutes(20)
            }

            Then("the override's severity and closure win too") {
                // Precedence is a property of the override, not of the field.
                // Taking the timing from the override and the severity from the
                // catalog applies half of what the director asked for.
                val rule = calibration.sentinel.comeBackRules.getValue(StateKind.LYING)
                rule.severity shouldBe Severity.CRITICAL
                rule.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
            }
        }
    }
})
