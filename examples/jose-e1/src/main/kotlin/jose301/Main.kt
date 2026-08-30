package jose301

import com.manahive.blueprint.BlueprintOutcome
import com.manahive.scene.bdd.scenario
import com.manahive.scene.bdd.to

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Escenario E1: ComeBack 12/15m (DSL)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    jose.scenario("E1: 17 min sin acostarse") {
        given { calibration(configBasica) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectTransition(Unknown to Lying)
        thenExpectTransition(Lying to SittingInBed)
        thenExpectTransition(SittingInBed to Lying)
        thenExpectComeBackExceeded(Lying)
        thenExpectNoDwellExceeded()
    }.report()

    BlueprintOutcome.summarize()
}
