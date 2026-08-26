package com.manahive.sentinel

import com.manahive.contracts.policy.AlertRule
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.EffectiveRules
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.ResidentId
import com.manahive.kernel.RuleId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * SPEC-01 · Sentinel enruta cada regla a la familia que le corresponde.
 *
 * Una regla temporizada NO debe ser encontrada por el camino de la transición:
 * si lo fuera, el episodio se abriría al entrar al estado y el plazo que escribió
 * el director no serviría de nada. Ese fue el defecto original.
 */
class TriggerRoutingSpec : BehaviorSpec({

    fun regla(state: StateKind, triggerOn: TriggerOn) = AlertRule(
        id = RuleId("r-${state.name.lowercase()}"),
        trigger = state,
        triggerOn = triggerOn,
        severity = Severity.WARNING,
        closureCondition = ClosureCondition.STAFF_OR_SAFE,
        reversible = true,
        requiresConfirmation = false,
        requiresNvr = false,
        confirmationWindow = null,
    )

    fun calibracion(vararg reglas: AlertRule) = SentinelCalibration.from(
        EffectiveRules(
            residentId = ResidentId("test"),
            rules = reglas.toList(),
            fingerprint = "test",
        ),
    )

    Given("una regla temporizada sobre SITTING_IN_BED") {
        val cal = calibracion(regla(StateKind.SITTING_IN_BED, TriggerOn.DWELL))

        Then("la encuentra el camino de permanencia") {
            cal.dwellRuleFor(StateKind.SITTING_IN_BED).shouldNotBeNull()
        }

        Then("NO la encuentra el camino de la transición") {
            cal.transitionRuleFor(StateKind.SITTING_IN_BED).shouldBeNull()
        }
    }

    Given("una regla de entrada inmediata sobre BED_EDGE") {
        val cal = calibracion(regla(StateKind.BED_EDGE, TriggerOn.ENTRY))

        Then("la encuentra el camino de la transición") {
            cal.transitionRuleFor(StateKind.BED_EDGE).shouldNotBeNull()
        }

        Then("NO la encuentra el camino de permanencia") {
            cal.dwellRuleFor(StateKind.BED_EDGE).shouldBeNull()
        }
    }

    Given("reglas de las dos familias, sobre estados distintos") {
        val cal = calibracion(
            regla(StateKind.SITTING_IN_BED, TriggerOn.DWELL),
            regla(StateKind.BED_EDGE, TriggerOn.ENTRY),
        )

        Then("cada familia contiene exactamente una") {
            cal.dwellRules.keys shouldBe setOf(StateKind.SITTING_IN_BED)
            cal.transitionRules.keys shouldBe setOf(StateKind.BED_EDGE)
        }

        Then("las dos siguen siendo notificables bajo un paraguas") {
            cal.ruleIds.size shouldBe 2
        }
    }
})
