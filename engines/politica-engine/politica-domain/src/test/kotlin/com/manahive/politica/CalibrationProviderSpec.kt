package com.manahive.politica

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.TransitionKey
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

        And("una calibracion para Maria") {
            val calibration = PolicyCalibration(
                residentId = ResidentId("maria"),
                scene = ScenePolicy(
                    hysteresis = mapOf(
                        TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                    ),
                    dwellThresholds = mapOf(
                        StateKind.STANDING to DwellThreshold(
                            warning = Duration.ofMinutes(4),
                            exceeded = Duration.ofMinutes(5),
                        ),
                    ),
                    confidence = ConfidenceConfig(
                        minConfidence = mapOf(StateKind.BED_EDGE to 0.9),
                        heartbeatTimeout = Duration.ofSeconds(90),
                    ),
                ),
                sentinel = SentinelPolicy(alertRules = emptyMap()),
                harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
                recorder = RecorderPolicy(transitionWindows = emptyMap()),
                fingerprint = Fingerprint("test-fixture"),
            )
            provider.register(ResidentId("maria"), calibration)

            When("consulto la calibracion de Maria") {
                val result = provider.getCalibration(ResidentId("maria"))

                Then("obtengo la calibracion correcta") {
                    result shouldNotBe null
                    result.value shouldNotBe null
                }

                Then("la calibracion tiene el residentId correcto") {
                    result.value?.residentId shouldBe ResidentId("maria")
                }

                Then("la calibracion tiene histeresis") {
                    result.value?.scene?.hysteresis?.isNotEmpty() shouldBe true
                }

                Then("la calibracion tiene dwell thresholds") {
                    result.value?.scene?.dwellThresholds?.isNotEmpty() shouldBe true
                }

                Then("la calibracion tiene confidence config") {
                    result.value?.scene?.confidence shouldNotBe null
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

    Given("un CalibrationProvider vacio") {
        val provider = InMemoryCalibrationProvider()

        When("consulto un residente") {
            val result = provider.getCalibration(ResidentId("anyone"))

            Then("obtengo null") {
                result.value shouldBe null
            }
        }
    }
})
