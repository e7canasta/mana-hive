package com.manahive.scene

import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.StateKind.BED_EDGE as STATE_BED_EDGE
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.bed3
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.night1
import com.manahive.scene.SceneTestDsl.obs
import com.manahive.scene.SceneTestDsl.time03_00_00
import com.manahive.scene.SceneTestDsl.time03_00_02
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * SE-7 · El Interprete produce transicion valida
 *
 * Patron: Domain Event (Vernon)
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterTransitionSpec : BehaviorSpec({

    Given("un interprete") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(STATE_BED_EDGE) min 0.8
        }
        val interpreter = SceneInterpreterImpl(calibration)

        And("un gemelo en LYING desde 03:00:00") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega BED_EDGE con confianza 0.9 a las 03:00:02") {
                val obs = obs(BED_EDGE, 0.9) at time03_00_02
                val result = interpreter.interpret(twin, obs, time03_00_02)

                Then("el estado es BED_EDGE") {
                    result.value.twin.state shouldBe PersonState.BedEdge
                }

                Then("stateSince es 03:00:02") {
                    result.value.twin.stateSince shouldBe time03_00_02
                }

                Then("se emite TransitionDetected(LYING, BED_EDGE)") {
                    result.value.facts shouldHaveSize 1
                    result.value.facts.first() shouldBe TransitionDetected(
                        bed = bed3,
                        night = night1,
                        at = time03_00_02,
                        from = PersonState.Lying,
                        to = PersonState.BedEdge,
                    )
                }

                Then("la explicacion contiene transition-table") {
                    result.explanation shouldHaveSize 1
                    result.explanation.first().rule shouldBe "transition-table"
                }

                Then("no hay discards") {
                    result.discards shouldBe emptyList()
                }
            }
        }
    }
})
