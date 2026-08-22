package com.manahive.scene

import com.manahive.contracts.shared.minutes
import com.manahive.contracts.perception.ObservationKind.BED_EDGE
import com.manahive.contracts.perception.ObservationKind.STANDING
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.SceneFact.TransitionDetected
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.bed3
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.night1
import com.manahive.scene.SceneTestDsl.obs
import com.manahive.scene.SceneTestDsl.time02_59_58
import com.manahive.scene.SceneTestDsl.time03_00_00
import com.manahive.scene.SceneTestDsl.time03_00_02
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-13 · Escenario completo — La caida de las 03:00
 *
 * Patron: Use Case (Cockburn) — scenario end-to-end que cruza todos los componentes
 * TDD: Integration Test — verifica que interprete + reloj funcionan juntos
 */
class LaCaidaDeLas03Spec : BehaviorSpec({

    Given("Maria en cama 3, dormida") {
        val cal = calibration {
            table = TransitionTable.RELEASE_1
            confidence(StateKind.BED_EDGE) min 0.8
            confidence(StateKind.STANDING) min 0.8
        }
        val interpreter = SceneInterpreterImpl(cal)
        val sweeper = ClockSweeperImpl()
        val catalog = dwellCatalog {
            dwell {
                STANDING warning 4.minutes exceeded 5.minutes
            }
        }

        When("sensor ve borde de cama y luego de pie, y el reloj corre 5 minutos") {
            var twin: DigitalTwin = bed(3) occupiedBy maria at StateKind.LYING since time02_59_58
            val allFacts = mutableListOf<SceneFact>()
            var marks = DwellMarks(emptySet())

            // Paso 1: BED_EDGE a las 03:00:00
            val obs1 = obs(BED_EDGE, 0.9) at time03_00_00
            val result1 = interpreter.interpret(twin, obs1, time03_00_00)
            twin = result1.value.twin.withSignal(lastHeartbeat = time03_00_00)
            allFacts += result1.value.facts

            // Paso 2: STANDING a las 03:00:02
            val obs2 = obs(STANDING, 0.95) at time03_00_02
            val result2 = interpreter.interpret(twin, obs2, time03_00_02)
            twin = result2.value.twin.withSignal(lastHeartbeat = time03_00_02)
            allFacts += result2.value.facts

            // Paso 3: Reloj corre cada minuto hasta 03:05:02
            for (minute in 1..5) {
                val now = time03_00_00.plusSeconds(minute * 60L + 2)
                val sweepResult = sweeper.sweep(listOf(twin), now, catalog, marks)
                allFacts += sweepResult.value.facts
                marks = sweepResult.value.marks
                // Actualizar heartbeat para simular sensor vivo
                twin = twin.withSignal(lastHeartbeat = now)
            }

            Then("se emiten exactamente 4 hechos") {
                allFacts.size shouldBe 4
            }

            Then("facts[0] = TransitionDetected(LYING, BED_EDGE)") {
                allFacts[0] shouldBe TransitionDetected(
                    bed = bed3,
                    night = night1,
                    at = time03_00_00,
                    from = PersonState.Lying,
                    to = PersonState.BedEdge,
                )
            }

            Then("facts[1] = TransitionDetected(BED_EDGE, STANDING)") {
                allFacts[1] shouldBe TransitionDetected(
                    bed = bed3,
                    night = night1,
                    at = time03_00_02,
                    from = PersonState.BedEdge,
                    to = PersonState.Standing,
                )
            }

            Then("facts contiene DwellExceeded(STANDING)") {
                allFacts.any {
                    it is DwellExceeded && it.state == PersonState.Standing
                } shouldBe true
            }

            Then("el gemelo queda en STANDING") {
                twin.state shouldBe PersonState.Standing
            }

            Then("stateSince es 03:00:02") {
                twin.stateSince shouldBe time03_00_02
            }
        }
    }
})
