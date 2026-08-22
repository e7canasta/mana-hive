package com.manahive.scene

import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.DwellWarning
import com.manahive.contracts.scene.SceneFact.SignalLost
import com.manahive.contracts.scene.SceneFact.SignalRecovered
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class DigitalTwinEvolutionSpec : BehaviorSpec({

    val bed3 = BedId("bed-3")
    val night1 = NightId("night-1")
    val maria = ResidentId("maria")
    val monitor1 = MonitorId("m1")
    val time3_00_00 = Instant.parse("2024-01-01T03:00:00Z")
    val time3_00_02 = Instant.parse("2024-01-01T03:00:02Z")
    val time3_03_00 = Instant.parse("2024-01-01T03:03:00Z")

    Given("un gemelo en LYING desde 03:00:00") {
        val twin = DigitalTwin(
            bed = bed3,
            night = night1,
            occupant = maria,
            state = PersonState.Lying,
            stateSince = time3_00_00,
            signal = SignalHealth(monitor1, time3_00_00.minusSeconds(60), false),
        )

        When("evoluciona con TransitionDetected a BED_EDGE") {
            val updated = twin.evolve(TransitionDetected(bed3, night1, time3_00_02, PersonState.Lying, PersonState.BedEdge))

            Then("el estado cambia a BedEdge") {
                updated.state shouldBe PersonState.BedEdge
            }

            Then("stateSince se actualiza") {
                updated.stateSince shouldBe time3_00_02
            }

            Then("el gemelo original no cambia") {
                twin.state shouldBe PersonState.Lying
            }
        }
    }

    Given("un gemelo con sensor vivo") {
        val twin = DigitalTwin(
            bed = bed3,
            night = night1,
            occupant = maria,
            state = PersonState.Lying,
            stateSince = time3_00_00,
            signal = SignalHealth(monitor1, time3_00_00.minusSeconds(60), false),
        )

        When("evoluciona con SignalLost") {
            val updated = twin.evolve(SignalLost(bed3, night1, time3_03_00, monitor1, time3_00_00))

            Then("signal.lost es true") {
                updated.signal.lost shouldBe true
            }

            Then("el gemelo original no cambia") {
                twin.signal.lost shouldBe false
            }
        }
    }

    Given("un gemelo con sensor perdido") {
        val twin = DigitalTwin(
            bed = bed3,
            night = night1,
            occupant = maria,
            state = PersonState.Lying,
            stateSince = time3_00_00,
            signal = SignalHealth(monitor1, time3_00_00.minusSeconds(60), true),
        )

        When("evoluciona con SignalRecovered") {
            val updated = twin.evolve(SignalRecovered(bed3, night1, time3_00_02, monitor1))

            Then("signal.lost es false") {
                updated.signal.lost shouldBe false
            }
        }
    }

    Given("un gemelo en STANDING") {
        val twin = DigitalTwin(
            bed = bed3,
            night = night1,
            occupant = maria,
            state = PersonState.Standing,
            stateSince = time3_00_00,
            signal = SignalHealth(monitor1, time3_00_00.minusSeconds(60), false),
        )

        When("evoluciona con DwellWarning") {
            val updated = twin.evolve(DwellWarning(bed3, night1, time3_03_00, PersonState.Standing, Duration.ofMinutes(5), time3_00_00))

            Then("el gemelo no cambia") {
                updated shouldBe twin
            }
        }
    }
})
