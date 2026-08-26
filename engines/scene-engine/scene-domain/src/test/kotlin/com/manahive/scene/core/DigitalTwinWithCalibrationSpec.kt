package com.manahive.scene.core

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.adapter.toSceneCalibration
import com.manahive.scene.support.SceneTestDsl
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
        val policyCalibration = PolicyCalibration(
            residentId = ResidentId("maria"),
            scene = ScenePolicy(
                hysteresis = mapOf(TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500)),
                dwellThresholds = mapOf(StateKind.STANDING to DwellThreshold(Duration.ofMinutes(4), Duration.ofMinutes(5))),
                confidence = ConfidenceConfig(minConfidence = mapOf(StateKind.BED_EDGE to 0.9), heartbeatTimeout = Duration.ofSeconds(90)),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
            fingerprint = Fingerprint("test-fixture"),
        )

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
                        SceneEvent.TransitionDetected(
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
