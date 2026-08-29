package com.manahive.scene.sweeper

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.scene.PresenceState
import com.manahive.contracts.scene.RailState
import com.manahive.contracts.scene.SceneEvent.SceneDwellExceeded
import com.manahive.contracts.scene.SceneState
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.calibration.DwellCatalog
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

private val time03_10_00: Instant = Instant.parse("2024-01-01T03:10:00Z")
private val time03_10_30: Instant = Instant.parse("2024-01-01T03:10:30Z")

/**
 * SE-· El reloj de la escena es por campo, no del conjunto.
 *
 * El defecto que este spec fija: habia un solo `sceneSince` para todo el
 * SceneState compuesto. Si la baranda bajaba a las 03:00 y la silla se movia a
 * las 03:10, el reloj se reseteaba y se perdia que la baranda llevaba diez
 * minutos abajo. La permanencia por campo —que es exactamente lo que el director
 * configura— no se podia calcular.
 */
class ClockSweeperPerFieldClockSpec : BehaviorSpec({

    Given("un reloj y una cama con umbral de permanencia sobre la baranda izquierda") {
        val sweeper = ClockSweeperImpl()
        val catalog = DwellCatalog(
            byState = emptyMap(),
            sceneThresholds = mapOf(
                SceneState.BED_LEFT to DwellThreshold(
                    warning = Duration.ofMinutes(5),
                    exceeded = Duration.ofMinutes(10),
                ),
            ),
        )

        And("la baranda baja a las 03:00 y la silla se mueve recien a las 03:10") {
            val twin = (bed(3) occupiedBy maria at StateKind.LYING since time03_00_00)
                .evolveScene({ it.copy(bed = it.bed.copy(left = RailState.Down)) }, time03_00_00)
                .evolveScene({ it.copy(wheelchair = PresenceState.NotPresent) }, time03_10_00)

            Then("cada campo conserva su propio reloj") {
                twin.scene.sinceOf(SceneState.BED_LEFT) shouldBe time03_00_00
                twin.scene.sinceOf(SceneState.WHEELCHAIR) shouldBe time03_10_00
            }

            When("barre a las 03:10:30") {
                val result = sweeper.sweep(listOf(twin), time03_10_30, catalog, DwellMarks(emptySet()))

                Then("la baranda supera su umbral: lleva 10 min abajo, no 30 segundos") {
                    result.value.facts.any {
                        it is SceneDwellExceeded && it.field == SceneState.BED_LEFT
                    } shouldBe true
                }

                Then("y el reloj que reporta es el de la baranda, no el del conjunto") {
                    val fact = result.value.facts
                        .filterIsInstance<SceneDwellExceeded>()
                        .single { it.field == SceneState.BED_LEFT }
                    fact.since shouldBe time03_00_00
                }
            }
        }
    }

    Given("un campo que ningun sensor informo") {
        val sweeper = ClockSweeperImpl()
        val catalog = DwellCatalog(
            byState = emptyMap(),
            sceneThresholds = mapOf(
                SceneState.BED_LEFT to DwellThreshold(
                    warning = Duration.ofSeconds(1),
                    exceeded = Duration.ofSeconds(2),
                ),
            ),
        )
        val twin = bed(3) occupiedBy maria at StateKind.LYING since time03_00_00

        Then("arranca en desconocido, no en un valor afirmado") {
            twin.scene.bed.left shouldBe RailState.Unknown
            twin.scene.staff shouldBe PresenceState.Unknown
        }

        Then("no acumula permanencia: no lleva cero tiempo en un estado, no tiene estado") {
            twin.durationInSceneField(SceneState.BED_LEFT, time03_10_00).shouldBeNull()
        }

        When("barre mucho despues") {
            val result = sweeper.sweep(listOf(twin), time03_10_30, catalog, DwellMarks(emptySet()))

            Then("no alerta por un sensor que nunca hablo") {
                result.value.facts.any { it is SceneDwellExceeded } shouldBe false
            }
        }
    }

    Given("las afirmaciones sobre la cama") {
        Then("no se puede decir que las barandas estan arriba si una es desconocida") {
            val media = com.manahive.contracts.scene.BedState(
                left = RailState.Up,
                right = RailState.Unknown,
            )
            media.isRailsUp shouldBe false
            media.isKnown shouldBe false
        }

        Then("desconocido no es presente") {
            PresenceState.Unknown.isPresent shouldBe false
            PresenceState.Unknown.isKnown shouldBe false
        }
    }
})
