package com.manahive.scene

import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.UnknownCause
import com.manahive.contracts.scene.toPersonState
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ObservationKindMappingSpec : BehaviorSpec({

    Given("un ObservationKind") {
        When("es IN_BED") {
            Then("traduce a Lying") {
                ObservationKind.IN_BED.toPersonState() shouldBe PersonState.Lying
            }
        }

        When("es BED_EDGE") {
            Then("traduce a BedEdge") {
                ObservationKind.BED_EDGE.toPersonState() shouldBe PersonState.BedEdge
            }
        }

        When("es STANDING") {
            Then("traduce a Standing") {
                ObservationKind.STANDING.toPersonState() shouldBe PersonState.Standing
            }
        }

        When("es OUT_OF_ROOM") {
            Then("traduce a Absent") {
                ObservationKind.OUT_OF_ROOM.toPersonState() shouldBe PersonState.Absent
            }
        }

        When("es HEARTBEAT") {
            Then("traduce a Lying (no cambia estado)") {
                ObservationKind.HEARTBEAT.toPersonState() shouldBe PersonState.Lying
            }
        }

        When("es STAFF_IN_ROOM") {
            Then("traduce a Lying (staff no afecta persona)") {
                ObservationKind.STAFF_IN_ROOM.toPersonState() shouldBe PersonState.Lying
            }
        }

        When("es UNCLASSIFIED") {
            Then("traduce a Unknown(SCENE)") {
                val result = ObservationKind.UNCLASSIFIED.toPersonState()
                result shouldBe PersonState.Unknown(UnknownCause.SCENE)
            }
        }
    }
})
