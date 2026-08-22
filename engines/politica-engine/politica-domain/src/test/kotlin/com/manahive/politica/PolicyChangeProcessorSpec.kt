package com.manahive.politica

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.CalibrationChanged
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.MobilityAid
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.PolicyMode
import com.manahive.contracts.policy.RiskLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * PE-1 · PolicyChangeProcessor observa cambios
 *
 * Patron: Message Router + Content Enricher (Hohpe & Woolf)
 * TDD: Red-Green-Refactor (Beck)
 */
class PolicyChangeProcessorSpec : BehaviorSpec({

    Given("un PolicyChangeProcessor") {
        val processor = DefaultPolicyChangeProcessor()

        And("un evento PolicyChangeDetected para María") {
            val event = PolicyChangeDetected(
                residentId = ResidentId("maria"),
                at = Instant.parse("2026-08-21T03:00:00Z"),
                snapshot = AlarmProfile(
                    residentId = ResidentId("maria"),
                    riskLevel = RiskLevel.HIGH,
                    mobilityAid = MobilityAid.WALKER,
                    autopilot = false,
                    mode = PolicyMode.PRESET,
                    templateId = null,
                    overrides = emptyMap(),
                    catalogVersion = CatalogVersion("1.0.0"),
                    validFrom = Instant.parse("2026-08-21T03:00:00Z"),
                ),
            )

            When("proceso el evento") {
                val result = processor.process(event, Instant.parse("2026-08-21T03:00:01Z"))

                Then("emite CalibrationChanged") {
                    result.emittedEvents shouldHaveSize 1
                    result.emittedEvents[0].shouldBeInstanceOf<CalibrationChanged>()
                }

                Then("la calibración tiene dwell para STANDING") {
                    result.calibration.dwellThresholds.containsKey(StateKind.STANDING) shouldBe true
                }

                Then("el residentId es correcto") {
                    result.residentId shouldBe ResidentId("maria")
                }

                Then("la calibración no es null") {
                    result.calibration shouldNotBe null
                }
            }
        }

        And("un evento PolicyChangeDetected para José") {
            val event = PolicyChangeDetected(
                residentId = ResidentId("jose"),
                at = Instant.parse("2026-08-21T03:00:00Z"),
                snapshot = AlarmProfile(
                    residentId = ResidentId("jose"),
                    riskLevel = RiskLevel.LOW,
                    mobilityAid = MobilityAid.NONE,
                    autopilot = true,
                    mode = PolicyMode.PRESET,
                    templateId = null,
                    overrides = emptyMap(),
                    catalogVersion = CatalogVersion("1.0.0"),
                    validFrom = Instant.parse("2026-08-21T03:00:00Z"),
                ),
            )

            When("proceso el evento") {
                val result = processor.process(event, Instant.parse("2026-08-21T03:00:01Z"))

                Then("emite CalibrationChanged") {
                    result.emittedEvents shouldHaveSize 1
                    result.emittedEvents[0].shouldBeInstanceOf<CalibrationChanged>()
                }

                Then("el residentId es correcto") {
                    result.residentId shouldBe ResidentId("jose")
                }
            }
        }
    }
})
