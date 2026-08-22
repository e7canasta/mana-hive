package com.manahive.scene.sweeper

import com.manahive.contracts.shared.minutes
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_05_00
import com.manahive.scene.support.SceneTestDsl.time03_04_00
import com.manahive.scene.calibration.dsl.dwellCatalog
import com.manahive.scene.sweeper.ClockSweeperImpl
import com.manahive.scene.sweeper.DwellMarks
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-10 · El Reloj detecta umbral superado
 *
 * Patron: Domain Event (Vernon) — DwellExceeded es un evento de dominio
 * Patron: Threshold Specification (Fowler) — duration >= threshold
 */
class ClockSweeperExceededSpec : BehaviorSpec({

    Given("un reloj") {
        val sweeper = ClockSweeperImpl()

        And("un gemelo en STANDING desde hace 5 min") {
            val twin = bed(3) occupiedBy maria at StateKind.STANDING since time03_00_00

            And("umbral STANDING = 5 min") {
                val catalog = dwellCatalog {
                    dwell {
                        STANDING warning 4.minutes exceeded 5.minutes
                    }
                }
                val marks = DwellMarks(emptySet())

                When("sweep a las 03:05:00") {
                    val result = sweeper.sweep(listOf(twin), time03_05_00, catalog, marks)

                    Then("emite DwellExceeded") {
                        result.value.facts.any {
                            it is DwellExceeded && it.state == PersonState.Standing
                        } shouldBe true
                    }

                    Then("marks contiene la marca de exceeded") {
                        result.value.marks.emitted.any { !it.warning } shouldBe true
                    }
                }
            }
        }

        And("un gemelo en STANDING desde hace 4 min") {
            val twin = bed(3) occupiedBy maria at StateKind.STANDING since time03_00_00

            And("umbral STANDING = 5 min") {
                val catalog = dwellCatalog {
                    dwell {
                        STANDING warning 4.minutes exceeded 5.minutes
                    }
                }
                val marks = DwellMarks(emptySet())

                When("sweep a las 03:04:00") {
                    val result = sweeper.sweep(listOf(twin), time03_04_00, catalog, marks)

                    Then("no emite DwellExceeded") {
                        result.value.facts.any { it is DwellExceeded } shouldBe false
                    }
                }
            }
        }
    }
})
