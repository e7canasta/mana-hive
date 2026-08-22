package com.manahive.scene

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.buildPolicyCalibration
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant

/**
 * SE-15 · DigitalTwin incluye calibración
 *
 * Patron: Inmutabilidad (Bloch) — data class + copy()
 * TDD: Red-Green-Refactor (Beck)
 *
 * BDD: el DigitalTwin incluye la SceneCalibration del residente.
 * Cuando evoluciona, la calibración se conserva.
 */
class DigitalTwinWithCalibrationSpec : BehaviorSpec({

    Given("un PolicyCalibration para María") {
        val policyCalibration = buildPolicyCalibration {
            resident(ResidentId("maria"))
            hysteresis {
                from(StateKind.LYING) { to(StateKind.BED_EDGE) after Duration.ofMillis(1500) }
            }
            dwell {
                StateKind.STANDING warning Duration.ofMinutes(4) exceeded Duration.ofMinutes(5)
            }
            confidence {
                StateKind.BED_EDGE min 0.9
            }
            heartbeat {
                timeout to Duration.ofSeconds(90)
            }
        }

        And("convertido a SceneCalibration via adaptador") {
            val sceneCalibration = policyCalibration.toSceneCalibration()

            And("un gemelo con calibración") {
                val twin = SceneTestDsl.bed(3)
                    .occupiedBy(SceneTestDsl.maria)
                    .at(StateKind.LYING)
                    .withCalibration(sceneCalibration)
                    .since(SceneTestDsl.time03_00_00)

                Then("el gemelo tiene calibración") {
                    twin.calibration shouldNotBe null
                }

                Then("el gemelo tiene ocupante") {
                    twin.occupant shouldBe ResidentId("maria")
                }

                Then("la calibración tiene dwell thresholds") {
                    twin.calibration?.dwellThresholds?.containsKey(StateKind.STANDING) shouldBe true
                }

                When("evoluciona con TransitionDetected") {
                    val updated = twin.evolve(
                        SceneFact.TransitionDetected(
                            bed = BedId("bed-3"),
                            night = NightId("night-1"),
                            at = SceneTestDsl.time03_00_02,
                            from = PersonState.Lying,
                            to = PersonState.BedEdge,
                        ),
                    )

                    Then("la calibración se conserva") {
                        updated.calibration shouldBe twin.calibration
                    }

                    Then("el estado cambia") {
                        updated.state shouldBe PersonState.BedEdge
                    }
                }
            }
        }
    }
})
