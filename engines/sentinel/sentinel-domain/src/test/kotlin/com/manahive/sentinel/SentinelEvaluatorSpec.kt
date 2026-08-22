package com.manahive.sentinel

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.StateKind
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
 * escalation, fatigue budget, dwell exceeded.
 */
class SentinelEvaluatorSpec : BehaviorSpec({

    val now = Instant.parse("2026-08-22T02:00:00Z")
    val bed = BedId("301")
    val resident = ResidentId("maria")

    // ── Helper: create a calibration with common rules ──

    fun testCalibration(
        maxFatigue: Int = 5,
    ): SentinelCalibration = sentinelCalibration {
        resident("maria")
        fatigue { maxPerShift = maxFatigue }

        rule("r-sitting") {
            trigger = StateKind.SITTING_IN_BED
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
        }

        rule("r-bed-edge") {
            trigger = StateKind.BED_EDGE
            severity = Severity.CRITICAL
            closureCondition = ClosureCondition.STAFF_AND_SAFE
            reversible = false
            requiresNvr = true
            umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
        }

        rule("r-standing") {
            trigger = StateKind.STANDING
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
            umbrellaEvents(StateKind.SITTING_IN_BED)
        }
    }

    // ── 1. No rule match → no signal ──

    Given("a transition with no matching rule") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(0, 5))

        val fact = SceneFact.TransitionDetected(
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
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(0, 5))

        val fact = SceneFact.TransitionDetected(
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

            Then("fatigue incremented") {
                result.value.episodes.fatigue.interruptionsThisShift shouldBe 1
            }
        }
    }

    // ── 3. New episode opened (CRITICAL) ──

    Given("a BED_EDGE transition (CRITICAL rule)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(0, 5))

        val fact = SceneFact.TransitionDetected(
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

    // ── 4. Fatigue budget exceeded → suppressed ──

    Given("fatigue budget exceeded") {
        val calibration = testCalibration(maxFatigue = 1)
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(1, 1))

        val fact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
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

    // ── 5. Umbrella event under open episode ──

    Given("an open episode and a STANDING transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open an episode first
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // Now a STANDING transition under the umbrella
        val umbrellaFact = SceneFact.TransitionDetected(
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

    // ── 6. Safe state → auto-recovery (reversible) ──

    Given("open WARNING episode and LYING transition (reversible)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open WARNING episode (SITTING_IN_BED)
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.SittingInBed,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // Safe state
        val safeFact = SceneFact.TransitionDetected(
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

    // ── 7. Safe state → no close (non-reversible, STAFF_AND_SAFE) ──

    Given("open CRITICAL episode and LYING transition (non-reversible)") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode (BED_EDGE)
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // Safe state
        val safeFact = SceneFact.TransitionDetected(
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

    // ── 8. Staff presence → close (STAFF_AND_SAFE) ──

    Given("open CRITICAL episode and staff presence") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // Staff arrives
        val staffFact = SceneFact.StaffPresenceDetected(
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

    // ── 9. Staff presence + safe state → close ──

    Given("open CRITICAL episode with staff present and LYING transition") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open CRITICAL episode
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Standing,
            to = PersonState.BedEdge,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // Staff arrives
        val staffFact = SceneFact.StaffPresenceDetected(
            bed = bed, night = NightId("night-1"), at = now.plusSeconds(30),
            staff = StaffId("nurse-1"),
        )
        val afterStaff = evaluator.evaluate(staffFact, afterOpen.value.episodes, now.plusSeconds(30))

        // Safe state
        val safeFact = SceneFact.TransitionDetected(
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

    // ── 10. Escalation (higher severity under umbrella) ──

    Given("open WARNING episode and CRITICAL trigger under umbrella") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)

        // Open WARNING episode (STANDING)
        val openFact = SceneFact.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying,
            to = PersonState.Standing,
        )
        val afterOpen = evaluator.evaluate(openFact, EpisodeLedger.empty(resident, FatigueBudget(0, 5)), now)

        // BED_EDGE under umbrella (CRITICAL > WARNING → escalation)
        val escalateFact = SceneFact.TransitionDetected(
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

    // ── 11. Dwell exceeded → opens episode ──

    Given("a DwellExceeded fact with no open episode") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(0, 5))

        val fact = SceneFact.DwellExceeded(
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

    // ── 12. Staff presence with no open episode → no-op ──

    Given("staff presence with no open episode") {
        val calibration = testCalibration()
        val evaluator = createSentinelEvaluator(calibration)
        val episodes = EpisodeLedger.empty(resident, FatigueBudget(0, 5))

        val fact = SceneFact.StaffPresenceDetected(
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
})
