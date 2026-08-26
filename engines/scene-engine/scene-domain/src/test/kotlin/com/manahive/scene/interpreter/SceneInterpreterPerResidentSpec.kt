package com.manahive.scene.interpreter

import com.manahive.contracts.common.Fingerprint
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.DiscardCause.CONFIDENCE_TOO_LOW
import com.manahive.kernel.ResidentId
import com.manahive.scene.adapter.toSceneCalibration
import com.manahive.scene.support.SceneTestDsl
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
        val mariaPolicy = PolicyCalibration(
            residentId = ResidentId("maria"),
            scene = ScenePolicy(
                hysteresis = mapOf(TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500)),
                dwellThresholds = emptyMap(),
                confidence = ConfidenceConfig(minConfidence = mapOf(StateKind.BED_EDGE to 0.9), heartbeatTimeout = Duration.ofSeconds(90)),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
            fingerprint = Fingerprint("test-fixture"),
        )

        val josePolicy = PolicyCalibration(
            residentId = ResidentId("jose"),
            scene = ScenePolicy(
                hysteresis = mapOf(TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500)),
                dwellThresholds = emptyMap(),
                confidence = ConfidenceConfig(minConfidence = mapOf(StateKind.BED_EDGE to 0.7), heartbeatTimeout = Duration.ofSeconds(90)),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
            fingerprint = Fingerprint("test-fixture"),
        )

        And("ambos convertidos a SceneCalibration") {
            val mariaCalibration = mariaPolicy.toSceneCalibration()
            val joseCalibration = josePolicy.toSceneCalibration()

            And("SceneInterpreters separados") {
                val mariaInterpreter = createInterpreter(mariaCalibration)
                val joseInterpreter = createInterpreter(joseCalibration)

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
