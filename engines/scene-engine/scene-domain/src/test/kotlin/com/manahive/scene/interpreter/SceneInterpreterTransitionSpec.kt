package com.manahive.scene.interpreter

import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent.TransitionDetected
import com.manahive.contracts.scene.StateKind.BED_EDGE as STATE_BED_EDGE
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.bed3
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.night1
import com.manahive.scene.support.SceneTestDsl.obs
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_00_02
import com.manahive.scene.calibration.dsl.calibration
import com.manahive.scene.core.TransitionTable
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
        val interpreter = createInterpreter(calibration)

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
                    val fact = result.value.facts.first() as TransitionDetected
                    fact.copy(twinSnapshot = null) shouldBe TransitionDetected(
                        bed = bed3,
                        night = night1,
                        at = time03_00_02,
                        from = PersonState.Lying,
                        to = PersonState.BedEdge,
                    )
                    // TwinSnapshot must be present (Fowler VO) — verifies bugfix
                    assert(fact.twinSnapshot != null) { "twinSnapshot must not be null" }
                    assert(fact.twinSnapshot!!.state == PersonState.BedEdge)
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
