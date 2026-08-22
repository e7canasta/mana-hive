package com.manahive.scene

import com.manahive.contracts.policy.buildPolicyCalibration
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.DiscardCause.CONFIDENCE_TOO_LOW
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * SE-16 · SceneInterpreter por residente
 *
 * Patron: Factory Pattern (Fowler) — SceneInterpreterImpl(calibration)
 * TDD: Red-Green-Refactor (Beck)
 *
 * BDD: cada residente tiene su propio SceneInterpreter con su SceneCalibration.
 * María exige confianza 0.9, José acepta 0.7. Una observación con confianza 0.8
 * pasa para José pero no para María.
 */
class SceneInterpreterPerResidentSpec : BehaviorSpec({

    Given("dos PolicyCalibrations con diferentes confianzas") {
        val mariaPolicy = buildPolicyCalibration {
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

        val josePolicy = buildPolicyCalibration {
            resident(ResidentId("jose"))
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

        And("ambos convertidos a SceneCalibration") {
            val mariaCalibration = mariaPolicy.toSceneCalibration()
            val joseCalibration = josePolicy.toSceneCalibration()

            And("SceneInterpreters separados") {
                val mariaInterpreter = SceneInterpreterImpl(mariaCalibration)
                val joseInterpreter = SceneInterpreterImpl(joseCalibration)

                And("ambos gemelos en LYING") {
                    val mariaTwin = SceneTestDsl.bed(3)
                        .occupiedBy(SceneTestDsl.maria)
                        .at(StateKind.LYING)
                        .since(SceneTestDsl.time03_00_00)

                    val joseTwin = SceneTestDsl.bed(4)
                        .occupiedBy(SceneTestDsl.jose)
                        .at(StateKind.LYING)
                        .since(SceneTestDsl.time03_00_00)

                    When("llega BED_EDGE con confianza 0.8") {
                        val obs = SceneTestDsl.obs(ObservationKind.BED_EDGE, 0.8)
                            .at(SceneTestDsl.time03_00_02)

                        Then("el interprete de María descarta (0.8 < 0.9)") {
                            val result = mariaInterpreter.interpret(mariaTwin, obs, SceneTestDsl.time03_00_02)
                            result.discards shouldHaveSize 1
                            result.discards[0].cause shouldBe CONFIDENCE_TOO_LOW
                        }

                        Then("el interprete de José acepta (0.8 >= 0.7)") {
                            val result = joseInterpreter.interpret(joseTwin, obs, SceneTestDsl.time03_00_02)
                            result.discards shouldBe emptyList()
                            result.value.twin.state shouldBe com.manahive.contracts.scene.PersonState.BedEdge
                        }
                    }
                }
            }
        }
    }
})
