package com.manahive.sentinel

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * SentinelEvaluatorImpl tests.
 *
 * Covers: new episode, umbrella events, safe state, staff presence,
 * escalation, dwell exceeded.
 *
 * NOTE: Fatigue is NOT tested here — it's Harbor's concern.
 * Sentinel ALWAYS opens episodes when a rule matches.
 */
class SentinelEvaluatorSpec : BehaviorSpec({

    val now = Instant.parse("2026-08-22T02:00:00Z")
    val bed = BedId("301")
    val resident = ResidentId("maria")

    // ── Helper: create a calibration with common rules ──

    fun testCalibration(): SentinelCalibration = sentinelCalibration {
        resident("maria")

        rule("r-sitting", StateKind.SITTING_IN_BED, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
        }

        rule("r-bed-edge", StateKind.BED_EDGE, TriggerOn.ENTRY) {
            severity = Severity.CRITICAL
            closureCondition = ClosureCondition.STAFF_AND_SAFE
            reversible = false
            requiresNvr = true
            umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
        }

        rule("r-standing", StateKind.STANDING, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
            umbrellaEvents(StateKind.SITTING_IN_BED)
        }

        rule("r-standing-dwell", StateKind.STANDING, TriggerOn.DWELL) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
        }

        rule("r-staff-or-safe", StateKind.IN_BATHROOM, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.STAFF_OR_SAFE
            reversible = true
        }
    }

    // ── 1. No rule match → no signal ──

    Given("a transition with no matching rule") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.InRoom,
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("no signals emitted") {
                result.value.signals.shouldBeEmpty()
            }

            Then("episodes unchanged") {
                result.value.episodes.open.size shouldBe 0
            }
        }
    }

    // ── 2. New episode opened (WARNING) ──

    Given("a SITTING_IN_BED transition (WARNING rule)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("emits EpisodeOpened with WARNING") {
                val signals = result.value.signals
                signals.shouldHaveSize(1)
                val opened = signals[0] as SentinelSignal.EpisodeOpened
                opened.severity shouldBe Severity.WARNING
                opened.trigger shouldBe StateKind.SITTING_IN_BED
                opened.reversible shouldBe true
            }

            Then("episode is open for bed") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.severity shouldBe Severity.WARNING
                open.closureCondition shouldBe ClosureCondition.SAFE_ONLY
            }
        }
    }

    // ── 3. New episode opened (CRITICAL) ──

    Given("a BED_EDGE transition (CRITICAL rule)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("emits EpisodeOpened with CRITICAL") {
                val opened = result.value.signals[0] as SentinelSignal.EpisodeOpened
                opened.severity shouldBe Severity.CRITICAL
                opened.reversible shouldBe false
                opened.requiresNvr shouldBe true
            }

            Then("closure condition is STAFF_AND_SAFE") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
            }
        }
    }

    // ── 4. Umbrella event under open episode ──

    Given("an open episode and a STANDING transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open an episode first
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Now a STANDING transition under the umbrella
        val umbrellaFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(10),
            from = PersonState.BedEdge,
            to = PersonState.Standing,
        )

        When("evaluated under umbrella") {
            val result = evaluator.evaluate(umbrellaFact, afterOpen.value.episodes, now.plusSeconds(10))

            Then("emits UmbrellaEvent, not a new episode") {
                val signals = result.value.signals
                signals.shouldHaveSize(1)
                val umbrella = signals[0] as SentinelSignal.UmbrellaEvent
                umbrella.state shouldBe StateKind.STANDING
            }

            Then("episode remains open") {
                result.value.episodes.openForBed(bed).shouldNotBeNull()
            }
        }
    }

    // ── 5. Safe state → auto-recovery (reversible) ──

    Given("open WARNING episode and LYING transition (reversible)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open WARNING episode (SITTING_IN_BED)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Safe state
        val safeFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(30),
            from = PersonState.SittingInBed,
            to = PersonState.Lying,
        )

        When("evaluated") {
            val result = evaluator.evaluate(safeFact, afterOpen.value.episodes, now.plusSeconds(30))

            Then("emits EpisodeClosed with AUTO_RECOVERY") {
                val closed = result.value.signals[0] as SentinelSignal.EpisodeClosed
                closed.cause shouldBe com.manahive.contracts.sentinel.ClosureCause.AUTO_RECOVERY
            }

            Then("episode closed") {
                result.value.episodes.openForBed(bed) shouldBe null
            }
        }
    }

    // ── 6. Safe state → no close (non-reversible, STAFF_AND_SAFE) ──

    Given("open CRITICAL episode and LYING transition (non-reversible)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode (BED_EDGE)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Safe state
        val safeFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(30),
            from = PersonState.BedEdge,
            to = PersonState.Lying,
        )

        When("evaluated") {
            val result = evaluator.evaluate(safeFact, afterOpen.value.episodes, now.plusSeconds(30))

            Then("emits AutoRecovery with requiresConfirmation") {
                val autoRecovery = result.value.signals[0] as SentinelSignal.AutoRecovery
                autoRecovery.reversible shouldBe false
                autoRecovery.requiresConfirmation shouldBe true
            }

            Then("episode remains open") {
                result.value.episodes.openForBed(bed).shouldNotBeNull()
            }
        }
    }

    // ── 7. Staff presence → close (STAFF_AND_SAFE) ──

    Given("open CRITICAL episode and staff presence") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Staff arrives
        val staffFact = SceneEvent.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            staff = StaffId("nurse-1"),
        )

        When("evaluated") {
            val result = evaluator.evaluate(staffFact, afterOpen.value.episodes, now.plusSeconds(60))

            Then("episode remains open (needs safe state too)") {
                result.value.episodes.openForBed(bed).shouldNotBeNull()
            }

            Then("staff marked present") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.staffPresent shouldBe true
            }
        }
    }

    // ── 8. Staff presence + safe state → close ──

    Given("open CRITICAL episode with staff present and LYING transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Staff arrives
        val staffFact = SceneEvent.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(30),
            staff = StaffId("nurse-1"),
        )
        val afterStaff = evaluator.evaluate(staffFact, afterOpen.value.episodes, now.plusSeconds(30))

        // Safe state
        val safeFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            from = PersonState.BedEdge,
            to = PersonState.Lying,
        )

        When("evaluated") {
            val result = evaluator.evaluate(safeFact, afterStaff.value.episodes, now.plusSeconds(60))

            Then("emits EpisodeClosed with STAFF_AND_SAFE") {
                val closed = result.value.signals[0] as SentinelSignal.EpisodeClosed
                closed.cause shouldBe com.manahive.contracts.sentinel.ClosureCause.STAFF_AND_SAFE
            }

            Then("no gap (staff was present)") {
                val closed = result.value.signals[0] as SentinelSignal.EpisodeClosed
                closed.gapDuration shouldBe null
            }

            Then("episode closed") {
                result.value.episodes.openForBed(bed) shouldBe null
            }
        }
    }

    // ── 9. Escalation (higher severity under umbrella) ──

    Given("open WARNING episode and CRITICAL trigger under umbrella") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open WARNING episode (STANDING)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.Standing,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // BED_EDGE under umbrella (CRITICAL > WARNING → escalation)
        val escalateFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(10),
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )

        When("evaluated") {
            val result = evaluator.evaluate(escalateFact, afterOpen.value.episodes, now.plusSeconds(10))

            Then("emits EpisodeOpened with escalated severity") {
                val opened = result.value.signals[0] as SentinelSignal.EpisodeOpened
                opened.severity shouldBe Severity.CRITICAL
            }

            Then("episode escalated") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.severity shouldBe Severity.CRITICAL
                open.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
            }
        }
    }

    // ── 10. Dwell exceeded → opens episode ──

    Given("a DwellExceeded fact with no open episode") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.DwellExceeded(
            bed = bed, night = NightId("night-1"), at = now,
            state = PersonState.Standing,
            threshold = Duration.ofMinutes(5),
            since = now.minus(Duration.ofMinutes(5)),
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("emits EpisodeOpened") {
                val opened = result.value.signals[0] as SentinelSignal.EpisodeOpened
                opened.trigger shouldBe StateKind.STANDING
            }
        }
    }

    // ── 11. Staff presence with no open episode → no-op ──

    Given("staff presence with no open episode") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now,
            staff = StaffId("nurse-1"),
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("no signals emitted") {
                result.value.signals.shouldBeEmpty()
            }

            Then("no episode opened") {
                result.value.episodes.open.size shouldBe 0
            }
        }
    }

    // ── 12. Multiple episodes: Sentinel ALWAYS opens (no fatigue) ──

    Given("multiple SITTING transitions in sequence") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        val fact1 = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )
        val result1 = evaluator.evaluate(fact1, EpisodeLedger.empty(resident), now)

        val fact2 = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(30),
            from = PersonState.SittingInBed,
            to = PersonState.Lying,
        )
        val result2 = evaluator.evaluate(fact2, result1.value.episodes, now.plusSeconds(30))

        val fact3 = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )

        When("evaluated third time") {
            val result3 = evaluator.evaluate(fact3, result2.value.episodes, now.plusSeconds(60))

            Then("opens episode again (no fatigue suppression)") {
                val signals = result3.value.signals
                signals.shouldHaveSize(1)
                val opened = signals[0] as SentinelSignal.EpisodeOpened
                opened.severity shouldBe Severity.WARNING
            }

            Then("episode is open") {
                result3.value.episodes.openForBed(bed).shouldNotBeNull()
            }
        }
    }

    // ── 13. STAFF_OR_SAFE: staff alone closes episode ──

    Given("open IN_BATHROOM episode (STAFF_OR_SAFE) and staff presence") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open IN_BATHROOM episode (r-staff-or-safe rule)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.InBathroom,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Staff arrives
        val staffFact = SceneEvent.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            staff = StaffId("nurse-1"),
        )

        When("evaluated") {
            val result = evaluator.evaluate(staffFact, afterOpen.value.episodes, now.plusSeconds(60))

            Then("episode closes (staff alone is enough)") {
                result.value.episodes.openForBed(bed) shouldBe null
            }

            Then("emits EpisodeClosed with STAFF_PRESENT cause") {
                val closed = result.value.signals.filterIsInstance<SentinelSignal.EpisodeClosed>()
                closed shouldHaveSize 1
                closed[0].cause shouldBe ClosureCause.STAFF_PRESENT
            }
        }
    }

    // ── 14. STAFF_OR_SAFE: safe state alone closes episode ──

    Given("open IN_BATHROOM episode (STAFF_OR_SAFE) and safe transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open IN_BATHROOM episode (r-staff-or-safe rule)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.InBathroom,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Safe transition: IN_BATHROOM -> LYING (safe state)
        val safeFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            from = PersonState.InBathroom,
            to = PersonState.Lying,
        )

        When("evaluated") {
            val result = evaluator.evaluate(safeFact, afterOpen.value.episodes, now.plusSeconds(60))

            Then("episode closes (safe state alone is enough)") {
                result.value.episodes.openForBed(bed) shouldBe null
            }

            Then("emits EpisodeClosed with AUTO_RECOVERY cause") {
                val closed = result.value.signals.filterIsInstance<SentinelSignal.EpisodeClosed>()
                closed shouldHaveSize 1
                closed[0].cause shouldBe ClosureCause.AUTO_RECOVERY
            }
        }
    }

    // ── 15. STAFF_OR_SAFE: neither staff nor safe → stays open ──

    Given("open IN_BATHROOM episode (STAFF_OR_SAFE) and unsafe transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open IN_BATHROOM episode (r-staff-or-safe rule)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.InBathroom,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Unsafe transition: IN_BATHROOM -> STANDING (not a safe state)
        val unsafeFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            from = PersonState.InBathroom,
            to = PersonState.Standing,
        )

        When("evaluated") {
            val result = evaluator.evaluate(unsafeFact, afterOpen.value.episodes, now.plusSeconds(60))

            Then("episode remains open") {
                result.value.episodes.openForBed(bed).shouldNotBeNull()
            }

            Then("no EpisodeClosed signal") {
                val closed = result.value.signals.filterIsInstance<SentinelSignal.EpisodeClosed>()
                closed.shouldBeEmpty()
            }
        }
    }

    // ── 16. Staff left → staffPresent reset ──

    Given("open episode with staff present, then staff leaves") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open BED_EDGE episode (r-bed-edge rule, STAFF_AND_SAFE — needs both staff AND safe)
        val openFact = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident), now)

        // Staff arrives → staffPresent = true, but episode stays open (needs safe state too)
        val staffFact = SceneEvent.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
            staff = StaffId("nurse-1"),
        )
        val afterStaff = evaluator.evaluate(staffFact, afterOpen.value.episodes, now.plusSeconds(60))

        // Staff leaves → staffPresent = false
        val staffLeftFact = SceneEvent.StaffLeftDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(120),
        )

        When("evaluated") {
            val result = evaluator.evaluate(staffLeftFact, afterStaff.value.episodes, now.plusSeconds(120))

            Then("episode remains open") {
                result.value.episodes.openForBed(bed).shouldNotBeNull()
            }

            Then("staff marked absent") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.staffPresent shouldBe false
            }

            Then("gap duration is no longer zero") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                val gap = open.gapDuration(now.plusSeconds(120))
                gap.seconds shouldBe 120
            }
        }
    }

    // ── 17. Staff left with no open episode → no-op ──

    Given("staff left with no open episode") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident)

        val fact = SceneEvent.StaffLeftDetected(
            bed = bed, night = NightId("night-1"), at = now,
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, episodes, now)

            Then("no signals emitted") {
                result.value.signals.shouldBeEmpty()
            }

            Then("no episode opened") {
                result.value.episodes.open.size shouldBe 0
            }
        }
    }
})
