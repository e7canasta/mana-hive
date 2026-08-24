package com.manahive.scene.adapter

import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.kernel.DiscardCause
import com.manahive.contracts.policy.ConfidenceConfig
import com.manahive.contracts.policy.HarborPolicy
import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.contracts.policy.RecorderPolicy
import com.manahive.contracts.policy.ScenePolicy
import com.manahive.contracts.policy.SentinelPolicy
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.scene.adapter.toSceneCalibration
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.core.SignalHealth
import com.manahive.scene.calibration.Confidence
import com.manahive.scene.interpreter.createInterpreter
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * Integration test: Politica Engine → Scene Engine bridging.
 *
 * Verifies that:
 * 1. PolicyCalibration from Politica converts to SceneCalibration
 * 2. SceneInterpreter uses the converted calibration correctly
 * 3. Different calibrations produce different results
 *
 * Pattern: Integration Test (Vernon) — tests the adapter boundary.
 */
class PoliticaToSceneIntegrationSpec : BehaviorSpec({

    Given("a PolicyCalibration from Politica Engine for María") {
        val mariaPolicyCalibration = PolicyCalibration(
            residentId = ResidentId("maria"),
            scene = ScenePolicy(
                hysteresis = mapOf(
                    TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                    TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofMillis(1500),
                ),
                dwellThresholds = mapOf(
                    StateKind.STANDING to com.manahive.contracts.policy.DwellThreshold(
                        warning = Duration.ofMinutes(4),
                        exceeded = Duration.ofMinutes(5),
                    ),
                ),
                confidence = ConfidenceConfig(
                    minConfidence = mapOf(
                        StateKind.BED_EDGE to 0.9,
                        StateKind.STANDING to 0.85,
                    ),
                    heartbeatTimeout = Duration.ofSeconds(90),
                ),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
        )

        When("converting to SceneCalibration via adapter") {
            val sceneCalibration = mariaPolicyCalibration.toSceneCalibration()

            Then("the SceneCalibration has correct hysteresis") {
                sceneCalibration.table.isLegal(StateKind.LYING, StateKind.BED_EDGE) shouldBe true
                sceneCalibration.table.hysteresis(
                    StateKind.LYING,
                    StateKind.BED_EDGE,
                ) shouldBe Duration.ofMillis(1500)
            }

            Then("the SceneCalibration has correct confidence") {
                sceneCalibration.confidence.forState(StateKind.BED_EDGE) shouldBe Confidence(0.9)
                sceneCalibration.confidence.forState(StateKind.STANDING) shouldBe Confidence(0.85)
            }

            Then("the SceneCalibration has correct heartbeat timeout") {
                sceneCalibration.heartbeatTimeout shouldBe Duration.ofSeconds(90)
            }

            Then("the SceneCalibration has correct dwell thresholds") {
                sceneCalibration.dwellThresholds[StateKind.STANDING]?.warning shouldBe Duration.ofMinutes(4)
                sceneCalibration.dwellThresholds[StateKind.STANDING]?.exceeded shouldBe Duration.ofMinutes(5)
            }
        }

        When("creating a SceneInterpreter with the converted calibration") {
            val sceneCalibration = mariaPolicyCalibration.toSceneCalibration()
            val interpreter = createInterpreter(sceneCalibration)
            val twin = DigitalTwin(
                bed = BedId("3"),
                night = NightId("night-1"),
                occupant = ResidentId("maria"),
                state = PersonState.Lying,
                stateSince = Instant.parse("2026-08-21T03:00:00Z"),
                signal = SignalHealth(
                    monitor = com.manahive.kernel.MonitorId("monitor-1"),
                    lastHeartbeat = Instant.parse("2026-08-21T03:00:00Z"),
                    lost = false,
                ),
                calibration = sceneCalibration,
            )

            Then("interpreter uses the calibration for confidence filtering") {
                val obs = Observation(
                    sourceEventId = "obs-1",
                    monitor = MonitorId("monitor-1"),
                    bed = BedId("3"),
                    kind = ObservationKind.BED_EDGE,
                    confidence = 0.85,  // Below 0.9 threshold for BED_EDGE
                    observedAt = Instant.parse("2026-08-21T03:00:02Z"),
                )

                val result = interpreter.interpret(twin, obs, Instant.parse("2026-08-21T03:00:02Z"))
                result.discards shouldHaveSize 1
                result.discards[0].cause shouldBe DiscardCause.CONFIDENCE_TOO_LOW
            }

            Then("interpreter accepts observation above confidence threshold") {
                val obs = Observation(
                    sourceEventId = "obs-2",
                    monitor = MonitorId("monitor-1"),
                    bed = BedId("3"),
                    kind = ObservationKind.BED_EDGE,
                    confidence = 0.95,  // Above 0.9 threshold for BED_EDGE
                    observedAt = Instant.parse("2026-08-21T03:00:02Z"),
                )

                val result = interpreter.interpret(twin, obs, Instant.parse("2026-08-21T03:00:02Z"))
                result.discards.shouldBeEmpty()
                result.value.twin.state shouldBe PersonState.BedEdge
            }
        }
    }

    Given("two residents with different PolicyCalibrations") {
        val mariaPolicyCalibration = PolicyCalibration(
            residentId = ResidentId("maria"),
            scene = ScenePolicy(
                hysteresis = mapOf(
                    TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                ),
                dwellThresholds = emptyMap(),
                confidence = ConfidenceConfig(
                    minConfidence = mapOf(StateKind.BED_EDGE to 0.9),
                    heartbeatTimeout = Duration.ofSeconds(90),
                ),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
        )

        val josePolicyCalibration = PolicyCalibration(
            residentId = ResidentId("jose"),
            scene = ScenePolicy(
                hysteresis = mapOf(
                    TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
                ),
                dwellThresholds = emptyMap(),
                confidence = ConfidenceConfig(
                    minConfidence = mapOf(StateKind.BED_EDGE to 0.7),  // Lower threshold
                    heartbeatTimeout = Duration.ofSeconds(90),
                ),
            ),
            sentinel = SentinelPolicy(alertRules = emptyMap()),
            harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap()),
            recorder = RecorderPolicy(transitionWindows = emptyMap()),
        )

        When("converting both to SceneCalibrations") {
            val mariaSceneCalibration = mariaPolicyCalibration.toSceneCalibration()
            val joseSceneCalibration = josePolicyCalibration.toSceneCalibration()

            Then("they have different confidence thresholds") {
                mariaSceneCalibration.confidence.forState(StateKind.BED_EDGE) shouldBe Confidence(0.9)
                joseSceneCalibration.confidence.forState(StateKind.BED_EDGE) shouldBe Confidence(0.7)
            }

            And("creating interpreters for each") {
                val mariaInterpreter = createInterpreter(mariaSceneCalibration)
                val joseInterpreter = createInterpreter(joseSceneCalibration)

                val mariaTwin = DigitalTwin(
                    bed = BedId("3"),
                    night = NightId("night-1"),
                    occupant = ResidentId("maria"),
                    state = PersonState.Lying,
                    stateSince = Instant.parse("2026-08-21T03:00:00Z"),
                    signal = SignalHealth(
                        monitor = com.manahive.kernel.MonitorId("monitor-1"),
                        lastHeartbeat = Instant.parse("2026-08-21T03:00:00Z"),
                        lost = false,
                    ),
                    calibration = mariaSceneCalibration,
                )

                val joseTwin = DigitalTwin(
                    bed = BedId("4"),
                    night = NightId("night-1"),
                    occupant = ResidentId("jose"),
                    state = PersonState.Lying,
                    stateSince = Instant.parse("2026-08-21T03:00:00Z"),
                    signal = SignalHealth(
                        monitor = com.manahive.kernel.MonitorId("monitor-2"),
                        lastHeartbeat = Instant.parse("2026-08-21T03:00:00Z"),
                        lost = false,
                    ),
                    calibration = joseSceneCalibration,
                )

                And("both receive BED_EDGE observation with confidence 0.8") {
                    val obs = Observation(
                        sourceEventId = "obs-3",
                        monitor = MonitorId("monitor-1"),
                        bed = BedId("3"),
                        kind = ObservationKind.BED_EDGE,
                        confidence = 0.8,
                        observedAt = Instant.parse("2026-08-21T03:00:02Z"),
                    )

                    Then("María's interpreter discards (threshold 0.9)") {
                        val result = mariaInterpreter.interpret(mariaTwin, obs, Instant.parse("2026-08-21T03:00:02Z"))
                        result.discards shouldHaveSize 1
                        result.discards[0].cause shouldBe DiscardCause.CONFIDENCE_TOO_LOW
                    }

                    Then("José's interpreter accepts (threshold 0.7)") {
                        val result = joseInterpreter.interpret(joseTwin, obs, Instant.parse("2026-08-21T03:00:02Z"))
                        result.discards.shouldBeEmpty()
                        result.value.twin.state shouldBe PersonState.BedEdge
                    }
                }
            }
        }
    }
})
