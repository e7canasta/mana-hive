package com.manahive.scene.interpreter

import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.StateKind.BED_EDGE as STATE_BED_EDGE
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.kernel.DiscardCause.HYSTERESIS_NOT_MET
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.bed3
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.night1
import com.manahive.scene.support.SceneTestDsl.obs
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_00_01
import com.manahive.scene.support.SceneTestDsl.time03_00_02
import com.manahive.scene.calibration.dsl.calibration
import com.manahive.scene.core.TransitionTable
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * SE-6 · El Interprete aplica histeresis
 *
 * Patron: Temporal Specification (Fowler)
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterHysteresisSpec : BehaviorSpec({

    Given("un interprete") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(STATE_BED_EDGE) min 0.8
        }
        val interpreter = createInterpreter(calibration)

        And("un gemelo en LYING desde hace 1s") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega BED_EDGE (histeresis = 1500ms)") {
                val obs = obs(BED_EDGE, 0.9) at time03_00_01
                val result = interpreter.interpret(twin, obs, time03_00_01)

                Then("descarta por HYSTERESIS_NOT_MET") {
                    result.discards.map { it.cause } shouldContain HYSTERESIS_NOT_MET
                }

                Then("el gemelo no cambia") {
                    result.value.twin shouldBe twin
                }
            }
        }

        And("un gemelo en LYING desde hace 2s") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega BED_EDGE (histeresis = 1500ms)") {
                val obs = obs(BED_EDGE, 0.9) at time03_00_02
                val result = interpreter.interpret(twin, obs, time03_00_02)

                Then("no hay discards") {
                    result.discards shouldBe emptyList()
                }

                Then("el estado cambia a BedEdge") {
                    result.value.twin.state shouldBe PersonState.BedEdge
                }

                Then("se emite TransitionDetected") {
                    result.value.facts shouldHaveSize 1
                    result.value.facts.first() shouldBe TransitionDetected(
                        bed = bed3,
                        night = night1,
                        at = time03_00_02,
                        from = PersonState.Lying,
                        to = PersonState.BedEdge,
                    )
                }
            }
        }
    }
})
