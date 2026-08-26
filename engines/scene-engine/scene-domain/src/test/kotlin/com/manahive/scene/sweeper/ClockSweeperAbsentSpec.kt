package com.manahive.scene.sweeper

import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent.DwellExceeded
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.scene.toPersonState
import com.manahive.contracts.shared.minutes
import com.manahive.scene.calibration.dsl.dwellCatalog
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_05_00
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * "Se fue de la habitación y no volvió."
 *
 * Los cuatro niveles de `docs/NIVELES-MONITOREO.md` configuran ABSENT — es la
 * fila "Fuera de la habitación", y en CRITICAL avisa a los 2 minutos. Si el
 * barrido no la vigila, el residente que se va de noche no dispara nada, que es
 * el caso más peligroso que este sistema dice cubrir.
 *
 * Este spec existe porque al cerrar SPEC-03 se retiró un escenario de blueprint
 * atribuyéndolo a un gap del motor de escena. Acá se comprueba si tal gap existe.
 */
class ClockSweeperAbsentSpec : BehaviorSpec({

    Given("la percepción reporta que el residente salió de la habitación") {
        Then("OUT_OF_ROOM se traduce a Absent") {
            ObservationKind.OUT_OF_ROOM.toPersonState() shouldBe PersonState.Absent
        }
    }

    Given("el grafo de transiciones") {
        val tabla = com.manahive.scene.core.TransitionTable.RELEASE_2

        Then("se puede llegar a ABSENT desde cualquier estado fuera de la cama") {
            // El recorrido natural para irse es habitación → pasillo → fuera.
            // Antes sólo existía STANDING → ABSENT, así que ese recorrido moría
            // y la fila "Fuera de la habitación" del catálogo no disparaba nunca.
            listOf(
                StateKind.STANDING,
                StateKind.IN_ROOM,
                StateKind.IN_HALLWAY,
                StateKind.IN_BATHROOM,
                StateKind.OUTDOOR,
            ).forEach { desde ->
                withClue("$desde → ABSENT") {
                    tabla.isLegal(desde, StateKind.ABSENT) shouldBe true
                }
            }
        }
    }

    Given("un gemelo en ABSENT desde hace 5 minutos, con umbral de 5") {
        val sweeper = ClockSweeperImpl()
        val twin = bed(3) occupiedBy maria at StateKind.ABSENT since time03_00_00
        val catalog = dwellCatalog {
            dwell {
                ABSENT warning 2.minutes exceeded 5.minutes
            }
        }

        When("barre a las 03:05") {
            val result = sweeper.sweep(listOf(twin), time03_05_00, catalog, DwellMarks(emptySet()))

            Then("emite DwellExceeded para ABSENT") {
                result.value.facts.any {
                    it is DwellExceeded && it.state == PersonState.Absent
                } shouldBe true
            }
        }
    }
})
