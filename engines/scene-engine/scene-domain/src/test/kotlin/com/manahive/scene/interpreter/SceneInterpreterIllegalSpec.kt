package com.manahive.scene.interpreter

import com.manahive.contracts.perception.ObservationKind.OUT_OF_ROOM
import com.manahive.contracts.scene.StateKind.ABSENT
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.kernel.DiscardCause.ILLEGAL_TRANSITION
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.obs
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.calibration.dsl.calibration
import com.manahive.scene.core.TransitionTable
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * SE-5 · El Interprete rechaza transiciones ilegales
 *
 * Patron: Strategy Pattern (Gamma) — la tabla es intercambiable
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterIllegalSpec : BehaviorSpec({

    Given("un interprete con tabla RELEASE_1") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(ABSENT) min 0.8
        }
        val interpreter = createInterpreter(calibration)

        And("un gemelo en LYING") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega OUT_OF_ROOM (LYING -> ABSENT no existe)") {
                val obs = obs(OUT_OF_ROOM, 0.9) at time03_00_00
                val result = interpreter.interpret(twin, obs, time03_00_00)

                Then("descarta como ILLEGAL_TRANSITION") {
                    result.discards.map { it.cause } shouldContain ILLEGAL_TRANSITION
                }

                Then("el gemelo no cambia") {
                    result.value.twin shouldBe twin
                }
            }
        }
    }
})
