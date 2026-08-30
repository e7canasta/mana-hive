package jose301

import com.manahive.contracts.scene.PersonState
import com.manahive.blueprint.BlueprintOutcome
import com.manahive.scene.bdd.scenario
import com.manahive.scene.bdd.to

fun main() {
    val Unknown = PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SCENE)
    val Lying = PersonState.Lying
    val SittingInBed = PersonState.SittingInBed

    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — ComeBack via DAG → Politica → Scene (SPEC-05)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── Config Básica: comeBack 12/15, sin dwell ──────────────────────────

    println("── Config Básica: comeBack 12/15m ──")
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

    jose.scenario("E4: 4 min sentado") {
        given { calibration(configBasica) }
        includes(e4)
        thenExpectTransitions(3)
        thenExpectNoComeBackExceeded()
        thenExpectNoDwellExceeded()
    }.report()

    // ── Config Con Dwell: comeBack 20/25 + dwell SITTING_IN_BED 10/15 ────

    println("── Config Con Dwell: comeBack 20/25m, dwell SITTING_IN_BED 10/15m ──")
    println()

    jose.scenario("E1: dwell exceeded, comeBack no") {
        given { calibration(configConDwell) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectDwellExceeded(SittingInBed)
        thenExpectNoComeBackExceeded()
    }.report()

    jose.scenario("E4: sin dwell, sin comeBack") {
        given { calibration(configConDwell) }
        includes(e4)
        thenExpectTransitions(3)
        thenExpectNoDwellExceeded()
        thenExpectNoComeBackExceeded()
    }.report()

    jose.scenario("E6: sin dwell, sin comeBack") {
        given { calibration(configConDwell) }
        includes(e6)
        thenExpectTransitions(3)
        thenExpectNoDwellExceeded()
        thenExpectNoComeBackExceeded()
    }.report()

    // ── Config via Policy: DAG catalog → PolicyResolver → Scene ───────────
    // SPEC-05: ComeBack configured in DAG catalog, resolved through Politica Engine.

    println("── Config via Policy: comeBack 12/15m (DAG → Politica → Scene) ──")
    println()

    jose.scenario("E1 via Policy: 17 min sin acostarse") {
        given { calibration(configViaPolicy) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectTransition(Unknown to Lying)
        thenExpectTransition(Lying to SittingInBed)
        thenExpectTransition(SittingInBed to Lying)
        thenExpectComeBackExceeded(Lying)
        thenExpectNoDwellExceeded()
    }.report()

    jose.scenario("E4 via Policy: 4 min sentado") {
        given { calibration(configViaPolicy) }
        includes(e4)
        thenExpectTransitions(3)
        thenExpectTransition(Unknown to Lying)
        thenExpectTransition(Lying to SittingInBed)
        thenExpectTransition(SittingInBed to Lying)
        thenExpectNoComeBackExceeded()
        thenExpectNoDwellExceeded()
    }.report()

    // ── Config via Policy: comeBack + dwell ────────────────────────────────

    println("── Config via Policy: comeBack 20/25m + dwell 10/15m (DAG → Politica → Scene) ──")
    println()

    jose.scenario("E1 via Policy: dwell exceeded, comeBack no") {
        given { calibration(configViaPolicyConDwell) }
        includes(e1)
        thenExpectTransitions(3)
        thenExpectDwellExceeded(SittingInBed)
        thenExpectNoComeBackExceeded()
    }.report()

    jose.scenario("E4 via Policy: sin dwell, sin comeBack") {
        given { calibration(configViaPolicyConDwell) }
        includes(e4)
        thenExpectTransitions(3)
        thenExpectNoDwellExceeded()
        thenExpectNoComeBackExceeded()
    }.report()

    // Sale distinto de cero si algún check quedó rojo: los criterios de
    // aceptación de SPEC-05 se demuestran acá, así que este main tiene que
    // poder fallar. Antes imprimía "✅ DONE" pasara lo que pasara.
    BlueprintOutcome.summarize()
}
