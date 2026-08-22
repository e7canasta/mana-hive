package com.manahive.politica

import com.manahive.contracts.policy.PolicySource
import com.manahive.contracts.policy.buildPolicyCalibration
import com.manahive.contracts.shared.seconds
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration

/**
 * PE-3 · CalibrationProvider es el port para Scene Engine
 *
 * Patron: Port + Adapter (Vernon)
 * TDD: Red-Green-Refactor (Beck)
 */
class CalibrationProviderSpec : BehaviorSpec({

    Given("un CalibrationProvider con calibraciones") {
        val provider = InMemoryCalibrationProvider()

        And("una calibración para María") {
            val calibration = buildPolicyCalibration {
                resident(ResidentId("maria"))
                hysteresis {
                    from(StateKind.LYING) { to(StateKind.BED_EDGE) after Duration.ofMillis(1500) }
                }
                dwell {
                    StateKind.STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
                }
                confidence {
                    StateKind.BED_EDGE min 0.9
                }
                heartbeat {
                    timeout to 90.seconds
                }
            }
            provider.register(ResidentId("maria"), calibration)

            When("consulto la calibración de María") {
                val result = provider.getCalibration(ResidentId("maria"))

                Then("obtengo la calibración correcta") {
                    result shouldNotBe null
                    result.value shouldNotBe null
                }

                Then("la calibración tiene el residentId correcto") {
                    result.value?.residentId shouldBe ResidentId("maria")
                }

                Then("la calibración tiene histeresis") {
                    result.value?.hysteresis?.isNotEmpty() shouldBe true
                }

                Then("la calibración tiene dwell thresholds") {
                    result.value?.dwellThresholds?.isNotEmpty() shouldBe true
                }

                Then("la calibración tiene confidence config") {
                    result.value?.confidence shouldNotBe null
                }
            }
        }

        When("consulto un residente que no existe") {
            val result = provider.getCalibration(ResidentId("jose"))

            Then("obtengo null") {
                result.value shouldBe null
            }
        }
    }

    Given("un CalibrationProvider vacío") {
        val provider = InMemoryCalibrationProvider()

        When("consulto un residente") {
            val result = provider.getCalibration(ResidentId("anyone"))

            Then("obtengo null") {
                result.value shouldBe null
            }
        }
    }
})
