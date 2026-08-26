package com.manahive.politica.adapters

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.buildDagCatalog
import com.manahive.contracts.policy.buildResidentProfile
import com.manahive.contracts.scene.StateKind
import com.manahive.politica.PolicyResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration

/**
 * SPEC-05, last link: PolicyCalibration → engine calibrations.
 *
 * The adapters are where a resolved policy stops being data and becomes what
 * the engines actually run, so a rule lost here is lost for good — and silently.
 */
class ComeBackAdapterSpec : BehaviorSpec({

    val profile = buildResidentProfile("jose") {
        risk(RiskLevel.LOW)
        mobility(MobilityAid.NONE)
        level(WatchLevel.STANDARD)
    }.profile

    Given("a catalog watching both time IN lying and time AWAY FROM lying") {
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
        val calibration = PolicyResolver.resolve(catalog, profile).value

        When("adapted to a SentinelCalibration") {
            val sentinel = calibration.toSentinelCalibration()

            Then("the dwell rule survives") {
                // It did not: both rules resolved to the id `alert-lying`, and
                // the builder's rule map kept whichever was written last.
                val dwell = sentinel.dwellRuleFor(StateKind.LYING)
                dwell shouldNotBe null
                dwell!!.triggerOn shouldBe TriggerOn.DWELL
                dwell.severity shouldBe Severity.INFO
            }

            Then("the come-back rule survives alongside it") {
                val comeBack = sentinel.comeBackRuleFor(StateKind.LYING)
                comeBack shouldNotBe null
                comeBack!!.triggerOn shouldBe TriggerOn.COME_BACK
                comeBack.severity shouldBe Severity.WARNING
            }

            Then("come-back does not make LYING a watched state") {
                // ComeBack watches the ABSENCE of lying. Listing LYING as watched
                // would make DwellExceeded(LYING) notifiable under an umbrella
                // for a reason nobody configured.
                sentinel.rulesForState(StateKind.LYING).map { it.triggerOn } shouldBe listOf(TriggerOn.DWELL)
            }
        }

        When("adapted to a SceneCalibration") {
            val scene = calibration.toSceneCalibration()

            Then("both families of threshold reach the engine") {
                scene.dwellThresholds.getValue(StateKind.LYING).exceeded shouldBe Duration.ofMinutes(60)
                scene.comeBackThresholds.getValue(StateKind.LYING).exceeded shouldBe Duration.ofMinutes(15)
            }
        }
    }

    Given("a come-back rule with no explicit pre-warning") {
        val catalog = buildDagCatalog {
            resident {
                comeBackTo(StateKind.LYING) { alertAfter(Duration.ofMinutes(20)) }
            }
        }

        When("resolved and adapted") {
            val scene = PolicyResolver.resolve(catalog, profile).value.toSceneCalibration()

            Then("the pre-warning lands at half the deadline") {
                val threshold = scene.comeBackThresholds.getValue(StateKind.LYING)
                threshold.exceeded shouldBe Duration.ofMinutes(20)
                threshold.warning shouldBe Duration.ofMinutes(10)
            }
        }
    }
})
