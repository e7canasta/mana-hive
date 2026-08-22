package com.manahive.scene.calibration

import com.manahive.contracts.policy.buildPolicyCalibration
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.DiscardCause.CONFIDENCE_TOO_LOW
import com.manahive.kernel.ResidentId
import com.manahive.scene.adapter.toSceneCalibration
import com.manahive.scene.interpreter.createInterpreter
import com.manahive.scene.support.SceneTestDsl
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * SE-14 · SceneCalibration se recibe de Politica
 *
 * Patron: Observer Pattern (Fowler) — SceneEngine observa CalibrationChanged
 * TDD: Red-Green-Refactor (Beck)
 *
 * BDD: cuando Politica cambia las reglas, SceneEngine las recibe y
 * actualiza su interprete. La calibracion low risk acepta confianza 0.7,
 * la high risk exige 0.9. Unbservacion con confianza 0.8 pasa con la
 * primera pero no con la segunda.
 */
class SceneCalibrationReceivedSpec : BehaviorSpec({

    Given("un PolicyCalibration low risk para María") {
        val lowRiskPolicy = buildPolicyCalibration {
            resident(ResidentId("maria"))
            hysteresis {
                from(StateKind.LYING) { to(StateKind.BED_EDGE) after Duration.ofMillis(1500) }
            }
            confidence {
                StateKind.BED_EDGE min 0.7
            }
            heartbeat {
                timeout to Duration.ofSeconds(90)
            }
        }

        And("un PolicyCalibration high risk para María") {
            val highRiskPolicy = buildPolicyCalibration {
                resident(ResidentId("maria"))
                hysteresis {
                    from(StateKind.LYING) { to(StateKind.BED_EDGE) after Duration.ofMillis(1500) }
                }
                confidence {
                    StateKind.BED_EDGE min 0.9
                }
                heartbeat {
                    timeout to Duration.ofSeconds(90)
                }
            }

            And("ambos convertidos a SceneCalibration via adaptador") {
                val lowRiskCalibration = lowRiskPolicy.toSceneCalibration()
                val highRiskCalibration = highRiskPolicy.toSceneCalibration()

                And("un gemelo en LYING") {
                    val twin = SceneTestDsl.bed(3)
                        .occupiedBy(SceneTestDsl.maria)
                        .at(StateKind.LYING)
                        .since(SceneTestDsl.time03_00_00)

                    When("el interprete low risk recibe BED_EDGE con confianza 0.8") {
                        val lowRiskInterpreter = createInterpreter(lowRiskCalibration)
                        val obs = SceneTestDsl.obs(
                            com.manahive.contracts.perception.ObservationKind.BED_EDGE,
                            0.8,
                        ).at(SceneTestDsl.time03_00_02)
                        val result = lowRiskInterpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)

                        Then("acepta la transicion (0.8 >= 0.7)") {
                            result.discards shouldBe emptyList()
                            result.value.twin.state shouldBe com.manahive.contracts.scene.PersonState.BedEdge
                        }
                    }

                    When("el interprete high risk recibe BED_EDGE con confianza 0.8") {
                        val highRiskInterpreter = createInterpreter(highRiskCalibration)
                        val obs = SceneTestDsl.obs(
                            com.manahive.contracts.perception.ObservationKind.BED_EDGE,
                            0.8,
                        ).at(SceneTestDsl.time03_00_02)
                        val result = highRiskInterpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)

                        Then("descarta la transicion (0.8 < 0.9)") {
                            result.discards shouldHaveSize 1
                            result.discards[0].cause shouldBe CONFIDENCE_TOO_LOW
                        }
                    }
                }
            }
        }
    }
})
