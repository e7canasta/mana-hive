package com.manahive.sentinel

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.sentinel.stateLabel
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant

/**
 * What Sentinel says about a resident who has not come back.
 *
 * The subject here is not whether the episode opens — that is covered
 * elsewhere — but whether what Sentinel emits can be told apart from the
 * opposite fact. A come-back is about a state the resident is NOT in, and
 * every signal that names that state has to survive being read out loud.
 */
class ComeBackJudgmentSpec : BehaviorSpec({

    val now = Instant.parse("2026-08-22T02:00:00Z")
    val bed = BedId("301")
    val night = NightId("night-1")
    val resident = ResidentId("jose")

    /** Only a come-back rule: nothing else watches LYING. */
    fun comeBackOnly(): SentinelCalibration = sentinelCalibration {
        resident("jose")
        rule("comeback-lying", StateKind.LYING, TriggerOn.COME_BACK) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.STAFF_OR_SAFE
        }
        rule("r-standing", StateKind.STANDING, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            umbrellaEvents(StateKind.IN_BATHROOM)
        }
    }

    // ── The pre-warning ──────────────────────────────────────────────────

    Given("a resident who has been away from bed, short of the deadline") {
        val evaluator = createSentinelEvaluator(comeBackOnly())
        val fact = SceneEvent.ComeBackWarning(
            bed = bed, night = night, at = now,
            baseline = PersonState.Lying,
            threshold = Duration.ofMinutes(15),
            since = now.minusSeconds(720),
        )

        When("evaluated") {
            val result = evaluator.evaluate(fact, EpisodeLedger.empty(resident), now)

            Then("it is a come-back pre-warning, not a dwell one") {
                // As a DwellPreWarning(state = LYING) this was indistinguishable
                // from "lleva mucho acostado" — the opposite of the fact.
                val signal = result.value.signals.single()
                signal.shouldBeInstanceOf<SentinelSignal.ComeBackPreWarning>()
                signal.baseline shouldBe StateKind.LYING
                signal.elapsed shouldBe Duration.ofMinutes(12)
            }

            Then("no episode opens") {
                result.value.episodes.open.size shouldBe 0
            }
        }
    }

    // ── The umbrella ─────────────────────────────────────────────────────

    Given("an open episode and a resident who then fails to come back") {
        val evaluator = createSentinelEvaluator(comeBackOnly())
        val opened = evaluator.evaluate(
            SceneEvent.TransitionDetected(
                bed = bed, night = night, at = now,
                from = PersonState.Lying, to = PersonState.Standing,
            ),
            EpisodeLedger.empty(resident),
            now,
        ).value.episodes

        val fact = SceneEvent.ComeBackExceeded(
            bed = bed, night = night, at = now.plusSeconds(900),
            baseline = PersonState.Lying,
            threshold = Duration.ofMinutes(15),
            since = now,
        )

        When("evaluated under the open episode") {
            val result = evaluator.evaluate(fact, opened, now.plusSeconds(900))

            Then("it is reported under the umbrella") {
                // Nothing else watches LYING: before, reporting depended on an
                // unrelated dwell rule happening to watch the same state, so
                // the director's own come-back rule bought him no report.
                result.value.signals.shouldHaveSize(1)
            }

            Then("the umbrella says which question it answers") {
                val umbrella = result.value.signals.single()
                umbrella.shouldBeInstanceOf<SentinelSignal.UmbrellaEvent>()
                umbrella.state shouldBe StateKind.LYING
                umbrella.triggerOn shouldBe TriggerOn.COME_BACK
            }

            Then("and reads as an absence, not a presence") {
                val umbrella = result.value.signals.single() as SentinelSignal.UmbrellaEvent
                umbrella.stateLabel() shouldBe "awayFrom=LYING"
            }
        }
    }

    Given("an umbrella event raised by a transition") {
        val evaluator = createSentinelEvaluator(comeBackOnly())

        When("the resident moves under an open episode") {
            val opened = evaluator.evaluate(
                SceneEvent.TransitionDetected(
                    bed = bed, night = night, at = now,
                    from = PersonState.Lying, to = PersonState.Standing,
                ),
                EpisodeLedger.empty(resident),
                now,
            ).value.episodes
            val result = evaluator.evaluate(
                SceneEvent.TransitionDetected(
                    bed = bed, night = night, at = now.plusSeconds(60),
                    from = PersonState.Standing, to = PersonState.InBathroom,
                ),
                opened,
                now.plusSeconds(60),
            )

            Then("it reads as a presence") {
                val umbrella = result.value.signals
                    .filterIsInstance<SentinelSignal.UmbrellaEvent>()
                    .single()
                umbrella.triggerOn shouldBe TriggerOn.ENTRY
                umbrella.stateLabel() shouldBe "state=IN_BATHROOM"
            }
        }
    }
})
