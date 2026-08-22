package com.manahive.scene.sweeper

import com.manahive.contracts.shared.minutes
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.DwellWarning
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_04_00
import com.manahive.scene.support.SceneTestDsl.time03_03_00
import com.manahive.scene.support.SceneTestDsl.time03_05_00
import com.manahive.scene.calibration.dsl.dwellCatalog
import com.manahive.scene.sweeper.ClockSweeperImpl
import com.manahive.scene.sweeper.DwellMarks
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-9 · El Reloj detecta pre-aviso
 *
 * Patron: Domain Event (Vernon) — DwellWarning es un evento de dominio
 * Patron: Threshold Specification (Fowler) — duration >= warningThreshold
 */
class ClockSweeperWarningSpec : BehaviorSpec({

    Given("un reloj") {
        val sweeper = ClockSweeperImpl()

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

                    Then("emite DwellWarning") {
                        result.value.facts.any {
                            it is DwellWarning && it.state == PersonState.Standing
                        } shouldBe true
                    }

                    Then("marks contiene la marca de warning") {
                        result.value.marks.emitted.any { it.warning } shouldBe true
                    }
                }
            }
        }

        And("un gemelo en STANDING desde hace 3 min") {
            val twin = bed(3) occupiedBy maria at StateKind.STANDING since time03_00_00

            And("umbral STANDING = 5 min") {
                val catalog = dwellCatalog {
                    dwell {
                        STANDING warning 4.minutes exceeded 5.minutes
                    }
                }
                val marks = DwellMarks(emptySet())

                When("sweep a las 03:03:00") {
                    val result = sweeper.sweep(listOf(twin), time03_03_00, catalog, marks)

                    Then("no emite DwellWarning") {
                        result.value.facts.any { it is DwellWarning } shouldBe false
                    }
                }
            }
        }
    }
})
