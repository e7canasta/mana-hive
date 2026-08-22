package com.manahive.scene.sweeper

import com.manahive.contracts.shared.minutes
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.support.SceneTestDsl.bed
import com.manahive.scene.support.SceneTestDsl.maria
import com.manahive.scene.support.SceneTestDsl.time03_00_00
import com.manahive.scene.support.SceneTestDsl.time03_05_00
import com.manahive.scene.calibration.dsl.dwellCatalog
import com.manahive.scene.sweeper.ClockSweeperImpl
import com.manahive.scene.sweeper.DwellMarks
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * SE-11 · El Reloj es idempotente
 *
 * Patron: Idempotency via Marks (Fowler) — DwellMarks previene duplicacion
 */
class ClockSweeperIdempotentSpec : BehaviorSpec({

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

                When("sweep dos veces con el mismo now") {
                    val marks1 = DwellMarks(emptySet())
                    val result1 = sweeper.sweep(listOf(twin), time03_05_00, catalog, marks1)
                    val result2 = sweeper.sweep(listOf(twin), time03_05_00, catalog, result1.value.marks)

                    Then("solo 1 DwellExceeded en total") {
                        val total = result1.value.facts.filterIsInstance<DwellExceeded>().size +
                                result2.value.facts.filterIsInstance<DwellExceeded>().size
                        total shouldBe 1
                    }

                    Then("marks tiene 1 sola marca") {
                        result2.value.marks.emitted.filter { !it.warning } shouldHaveSize 1
                    }
                }
            }
        }
    }
})
