package com.manahive.sentinel

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant

/**
 * SPEC-01 · La rampa de severidad sobre un mismo estado.
 *
 * "Avisen cuando se pare; si sigue parado a los diez minutos, es crítico."
 * Un estado con regla de entrada Y regla de plazo, cada una con su severidad.
 *
 * Esto obliga a dos cosas que antes no se cumplían:
 *  - la calibración tiene que poder sostener las dos reglas sin descartar una;
 *  - escalar tiene que obedecer el mismo criterio que abrir — sólo la regla
 *    inmediata actúa en la transición, la temporizada espera su plazo.
 */
class SeverityRampSpec : BehaviorSpec({

    val now = Instant.parse("2026-08-22T02:00:00Z")
    val bed = BedId("301")
    val resident = ResidentId("maria")

    fun rampa() = sentinelCalibration {
        resident(resident)

        // Se para → aviso.
        rule("r-standing-entry", StateKind.STANDING, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            umbrellaEvents(StateKind.IN_ROOM)
        }

        // Sigue parado a los 10 minutos → crítico.
        rule("r-standing-dwell", StateKind.STANDING, TriggerOn.DWELL) {
            severity = Severity.CRITICAL
            closureCondition = ClosureCondition.STAFF_AND_SAFE
            umbrellaEvents(StateKind.IN_BATHROOM)
        }
    }

    Given("un estado vigilado por entrada Y por plazo") {
        val cal = rampa()

        Then("la calibración conserva las dos reglas") {
            cal.rulesForState(StateKind.STANDING).map { it.id.value }
                .shouldContainExactly("r-standing-entry", "r-standing-dwell")
        }

        Then("cada familia ve la suya") {
            cal.transitionRuleFor(StateKind.STANDING)!!.severity shouldBe Severity.WARNING
            cal.dwellRuleFor(StateKind.STANDING)!!.severity shouldBe Severity.CRITICAL
        }

        Then("el paraguas es la unión de los dos") {
            cal.notifiableStatesFor(StateKind.STANDING) shouldBe
                setOf(StateKind.IN_ROOM, StateKind.IN_BATHROOM)
        }
    }

    Given("María se para, con un episodio ya abierto de menor severidad") {
        val cal = rampa()
        val evaluator = createSentinelEvaluator(cal)

        val abre = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying, to = PersonState.Standing,
        )

        When("llega la transición") {
            val result = evaluator.evaluate(abre, EpisodeLedger.empty(resident), now)

            Then("abre episodio con la severidad de la regla inmediata, no la del plazo") {
                val opened = result.value.signals.single().shouldBeInstanceOf<SentinelSignal.EpisodeOpened>()
                opened.severity shouldBe Severity.WARNING
            }
        }

        When("después se cumple el plazo estando parada") {
            val abierto = evaluator.evaluate(abre, EpisodeLedger.empty(resident), now).value.episodes

            val vence = SceneEvent.DwellExceeded(
                bed = bed, night = NightId("night-1"), at = now.plusSeconds(600),
                state = PersonState.Standing,
                threshold = Duration.ofMinutes(10),
                since = now,
            )
            val result = evaluator.evaluate(vence, abierto, now.plusSeconds(600))

            Then("escala a CRITICAL — antes esto no era alcanzable por una regla temporizada") {
                val signal = result.value.signals.single().shouldBeInstanceOf<SentinelSignal.EpisodeComplicated>()
                signal.severity shouldBe Severity.CRITICAL
            }

            Then("sigue siendo el mismo episodio") {
                val open = result.value.episodes.openForBed(bed)
                open.shouldNotBeNull()
                open.severity shouldBe Severity.CRITICAL
            }
        }
    }

    Given("un estado vigilado SÓLO por plazo, con un episodio abierto de menor severidad") {
        val cal = sentinelCalibration {
            resident(resident)
            rule("r-sitting-entry", StateKind.SITTING_IN_BED, TriggerOn.ENTRY) {
                severity = Severity.WARNING
                closureCondition = ClosureCondition.SAFE_ONLY
            }
            rule("r-bathroom-dwell", StateKind.IN_BATHROOM, TriggerOn.DWELL) {
                severity = Severity.CRITICAL
                closureCondition = ClosureCondition.STAFF_AND_SAFE
            }
        }
        val evaluator = createSentinelEvaluator(cal)

        val abre = SceneEvent.TransitionDetected(
            bed = bed, night = NightId("night-1"), at = now,
            from = PersonState.Lying, to = PersonState.SittingInBed,
        )
        val abierto = evaluator.evaluate(abre, EpisodeLedger.empty(resident), now).value.episodes

        When("entra al baño — el plazo todavía no venció") {
            val entra = SceneEvent.TransitionDetected(
                bed = bed, night = NightId("night-1"), at = now.plusSeconds(60),
                from = PersonState.SittingInBed, to = PersonState.InBathroom,
            )
            val result = evaluator.evaluate(entra, abierto, now.plusSeconds(60))

            Then("NO escala: entrar al baño no es todavía demorarse en el baño") {
                result.value.episodes.openForBed(bed)!!.severity shouldBe Severity.WARNING
                result.value.signals.none { it is SentinelSignal.EpisodeOpened } shouldBe true
            }
        }
    }
})
