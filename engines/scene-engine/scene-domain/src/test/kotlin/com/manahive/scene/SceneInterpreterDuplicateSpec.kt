package com.manahive.scene

import com.manahive.contracts.perception.ObservationKind.IN_BED
import com.manahive.contracts.scene.StateKind.LYING
import com.manahive.kernel.DiscardCause.DUPLICATE
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.obs
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.time03_00_00
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * SE-4 · El Interprete detecta duplicados
 *
 * Patron: Idempotency Check (Fowler)
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterDuplicateSpec : BehaviorSpec({

    Given("un interprete") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(LYING) min 0.8
        }
        val interpreter = SceneInterpreterImpl(calibration)

        And("un gemelo en LYING") {
            val twin = bed(3) occupiedBy maria at LYING since time03_00_00

            When("llega IN_BED (mismo estado)") {
                val obs = obs(IN_BED, 0.9) at time03_00_00
                val result = interpreter.interpret(twin, obs, time03_00_00)

                Then("descarta como DUPLICATE") {
                    result.discards.map { it.cause } shouldContain DUPLICATE
                }

                Then("el gemelo no cambia") {
                    result.value.twin shouldBe twin
                }
            }
        }
    }
})
