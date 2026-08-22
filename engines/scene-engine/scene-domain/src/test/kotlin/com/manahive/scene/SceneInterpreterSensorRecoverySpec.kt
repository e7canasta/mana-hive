package com.manahive.scene

import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.perception.ObservationKind.STANDING
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.SignalRecovered
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.StateKind.BED_EDGE as STATE_BED_EDGE
import com.manahive.contracts.scene.StateKind.STANDING as STATE_STANDING
import com.manahive.kernel.DiscardCause.DUPLICATE
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.obs
import com.manahive.scene.SceneTestDsl.time03_00_00
import com.manahive.scene.SceneTestDsl.time03_00_05
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * SE-8 · El Interprete recupera sensor
 *
 * Patron: Chain of Responsibility (Fowler) — no corta el pipeline
 * TDD: Red-Green-Refactor (Beck)
 */
class SceneInterpreterSensorRecoverySpec : BehaviorSpec({

    Given("un interprete") {
        val calibration = calibration {
            table = TransitionTable.RELEASE_1
            confidence(STATE_STANDING) min 0.8
            confidence(STATE_BED_EDGE) min 0.8
        }
        val interpreter = SceneInterpreterImpl(calibration)

        And("un gemelo con signal.lost = true, estado STANDING") {
            val twin = (bed(3) occupiedBy maria at STATE_STANDING since time03_00_00)
                .let { it.copy(signal = it.signal.copy(lost = true)) }

            When("llega STANDING (mismo estado)") {
                val obs = obs(STANDING, 0.9) at time03_00_05
                val result = interpreter.interpret(twin, obs, time03_00_05)

                Then("se emite SignalRecovered") {
                    result.value.facts.any { it is SignalRecovered } shouldBe true
                }

                Then("signal.lost es false") {
                    result.value.twin.signal.lost shouldBe false
                }

                Then("descarta como DUPLICATE") {
                    result.discards.map { it.cause } shouldContain DUPLICATE
                }
            }
        }

        And("un gemelo con signal.lost = true, estado STANDING") {
            val twin = (bed(3) occupiedBy maria at STATE_STANDING since time03_00_00)
                .let { it.copy(signal = it.signal.copy(lost = true)) }

            When("llega BED_EDGE (cambio de estado)") {
                val obs = obs(BED_EDGE, 0.9) at time03_00_05
                val result = interpreter.interpret(twin, obs, time03_00_05)

                Then("se emite SignalRecovered") {
                    result.value.facts.any { it is SignalRecovered } shouldBe true
                }

                Then("se emite TransitionDetected(STANDING, BED_EDGE)") {
                    result.value.facts.any {
                        it is TransitionDetected && it.from == PersonState.Standing && it.to == PersonState.BedEdge
                    } shouldBe true
                }

                Then("el estado es BED_EDGE") {
                    result.value.twin.state shouldBe PersonState.BedEdge
                }

                Then("signal.lost es false") {
                    result.value.twin.signal.lost shouldBe false
                }
            }
        }
    }
})
