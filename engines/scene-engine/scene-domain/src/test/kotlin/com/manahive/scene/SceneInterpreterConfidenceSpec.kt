package com.manahive.scene

import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.scene.StateKind.BED_EDGE as STATE_BED_EDGE
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.kernel.DiscardCause.CONFIDENCE_TOO_LOW
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.obs
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.time03_00_00
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * SE-3 · El Interprete aplica Specification — confianza
 *
 * Patron: Specification Pattern (Vernon)
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterConfidenceSpec : BehaviorSpec({

    Given("un interprete con minConfidence BED_EDGE = 0.8") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(STATE_BED_EDGE) min 0.8
        }
        val interpreter = SceneInterpreterImpl(calibration)

        And("un gemelo en LYING") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega BED_EDGE con confianza 0.7") {
                val obs = obs(BED_EDGE, 0.7) at time03_00_00
                val result = interpreter.interpret(twin, obs, time03_00_00)

                Then("descarta por CONFIDENCE_TOO_LOW") {
                    result.discards.map { it.cause } shouldContain CONFIDENCE_TOO_LOW
                }

                Then("el gemelo no cambia") {
                    result.value.twin shouldBe twin
                }

                Then("no hay hechos") {
                    result.value.facts shouldBe emptyList()
                }
            }
        }

        And("un gemelo en LYING") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega BED_EDGE con confianza 0.9") {
                val obs = obs(BED_EDGE, 0.9) at time03_00_00
                val result = interpreter.interpret(twin, obs, time03_00_00)

                Then("no hay discards por confianza") {
                    result.discards.map { it.cause } shouldNotContain CONFIDENCE_TOO_LOW
                }
            }
        }
    }
})
