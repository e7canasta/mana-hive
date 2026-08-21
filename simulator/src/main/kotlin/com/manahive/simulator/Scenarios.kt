package com.manahive.simulator

import com.manahive.contracts.perception.ObservationKind
import java.time.Duration

/**
 * The initial scenario bank. Each entry carries its REQUIRED outcome — these
 * are the customer tests of the system, XP style.
 */
object Scenarios {

    /** The canonical night: the 03:00 fall. Sprint 1's demo runs THIS. */
    val fallAtThree = scenario("the 03:00 fall") {
        startsAt("2026-08-21T03:00:00Z")
        bed("12A")

        at("2026-08-21T03:00:00Z") { observes(ObservationKind.STANDING, confidence = 0.93) }
        at("2026-08-21T03:00:40Z") { sensorGoesSilent() }

        after(Duration.ofMinutes(5)) {
            expect(Expectation.AlertRaised(rule = "dwell-standing-5min", severity = "CRITICAL"))
        }
        after(Duration.ofSeconds(90)) {
            expect(Expectation.AlertEscalated(toStep = 2))
        }
        at("2026-08-21T03:08:35Z") { observes(ObservationKind.STAFF_IN_ROOM) }
        after(Duration.ofSeconds(1)) {
            expect(Expectation.AlertResolvedByPresence(maxSecondsToStaff = 600))
        }
    }

    /** Quiet night: turns in bed, micro-movements. Hysteresis absorbs everything. */
    val quietNight = scenario("quiet night") {
        startsAt("2026-08-21T01:00:00Z")
        bed("07B")

        at("2026-08-21T01:10:00Z") { observes(ObservationKind.BED_EDGE, confidence = 0.55) }
        at("2026-08-21T01:10:01Z") { observes(ObservationKind.IN_BED, confidence = 0.97) }
        after(Duration.ofHours(2)) { expect(Expectation.NoAlert) }
    }

    /** Nurse present during a risk transition: fact recorded, alarm suppressed WITH RECORD. */
    val staffPresentDuringTransition = scenario("staff present during risk transition") {
        startsAt("2026-08-21T04:00:00Z")
        bed("03C")

        at("2026-08-21T04:00:00Z") { observes(ObservationKind.STAFF_IN_ROOM) }
        at("2026-08-21T04:00:30Z") { observes(ObservationKind.STANDING, confidence = 0.91) }
        after(Duration.ofMinutes(6)) { expect(Expectation.SuppressedWithCause("STAFF_PRESENT")) }
    }

    val bank = listOf(fallAtThree, quietNight, staffPresentDuringTransition)
}
