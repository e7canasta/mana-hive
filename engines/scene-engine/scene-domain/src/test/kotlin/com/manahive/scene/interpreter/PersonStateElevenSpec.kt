package com.manahive.scene.interpreter

import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.kind
import com.manahive.kernel.DiscardCause
import com.manahive.scene.calibration.ConfidenceThresholds
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.TransitionTable
import com.manahive.scene.support.SceneTestDsl
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * SE-17 · PersonState 13 estados
 *
 * Patron: Value Object (Vernon) — PersonState como tipo seguro
 * TDD: Red-Green-Refactor (Beck)
 *
 * BDD: los 13 estados del catálogo clínico son soportados por el interprete.
 * Cada estado tiene una transición legal en la tabla.
 */
class PersonStateElevenSpec : BehaviorSpec({

    Given("un interprete con la tabla RELEASE_2") {
        val table = TransitionTable.RELEASE_2
        val interpreter = createInterpreter(
            calibration = SceneCalibration(
                table = table,
                confidence = ConfidenceThresholds(emptyMap()),
                heartbeatTimeout = Duration.ofSeconds(90),
            )
        )

        When("el gemelo esta en LYING y llega SITTING_IN_BED") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.LYING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.SITTING_IN_BED, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a SittingInBed") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.SittingInBed
                result.value.twin.state.kind shouldBe StateKind.SITTING_IN_BED
            }
        }

        When("el gemelo esta en LYING y llega ATTEMPTING_EXIT") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.LYING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.ATTEMPTING_EXIT, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a AttemptingExit") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.AttemptingExit
                result.value.twin.state.kind shouldBe StateKind.ATTEMPTING_EXIT
            }
        }

        When("el gemelo esta en LYING y llega BED_EDGE") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.LYING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.BED_EDGE, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a BedEdge") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.BedEdge
                result.value.twin.state.kind shouldBe StateKind.BED_EDGE
            }
        }

        When("el gemelo esta en LYING y llega STANDING directamente") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.LYING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.STANDING, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("la transicion es ilegal (LYING -> STANDING no existe)") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldHaveSize 1
                result.discards[0].cause shouldBe DiscardCause.ILLEGAL_TRANSITION
            }
        }

        When("el gemelo esta en BED_EDGE y llega STANDING") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.BED_EDGE)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.STANDING, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a Standing") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.Standing
                result.value.twin.state.kind shouldBe StateKind.STANDING
            }
        }

        When("el gemelo esta en STANDING y llega IN_BATHROOM") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.IN_BATHROOM, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a InBathroom") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.InBathroom
                result.value.twin.state.kind shouldBe StateKind.IN_BATHROOM
            }
        }

        When("el gemelo esta en STANDING y llega IN_ROOM") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.IN_ROOM, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a InRoom") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.InRoom
                result.value.twin.state.kind shouldBe StateKind.IN_ROOM
            }
        }

        When("el gemelo esta en STANDING y llega IN_HALLWAY") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.IN_HALLWAY, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a InHallway") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.InHallway
                result.value.twin.state.kind shouldBe StateKind.IN_HALLWAY
            }
        }

        When("el gemelo esta en STANDING y llega OUTDOOR") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.OUTDOOR, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a Outdoor") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.Outdoor
                result.value.twin.state.kind shouldBe StateKind.OUTDOOR
            }
        }

        When("el gemelo esta en STANDING y llega IN_CHAIR") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.IN_CHAIR, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a InChair") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.InChair
                result.value.twin.state.kind shouldBe StateKind.IN_CHAIR
            }
        }

        When("el gemelo esta en STANDING y llega IN_WHEELCHAIR") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.IN_WHEELCHAIR, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a InWheelchair") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.InWheelchair
                result.value.twin.state.kind shouldBe StateKind.IN_WHEELCHAIR
            }
        }

        When("el gemelo esta en STANDING y llega OUT_OF_ROOM") {
            val twin = SceneTestDsl.bed(3)
                .occupiedBy(SceneTestDsl.maria)
                .at(StateKind.STANDING)
                .since(SceneTestDsl.time03_00_00)

            val obs = SceneTestDsl.obs(ObservationKind.OUT_OF_ROOM, 0.95)
                .at(SceneTestDsl.time03_00_02)

            Then("el estado cambia a Absent") {
                val result = interpreter.interpret(twin, obs, SceneTestDsl.time03_00_02)
                result.discards shouldBe emptyList()
                result.value.twin.state shouldBe PersonState.Absent
                result.value.twin.state.kind shouldBe StateKind.ABSENT
            }
        }
    }
})
