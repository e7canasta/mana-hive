package com.manahive.scene

import com.manahive.contracts.shared.minutes
import com.manahive.contracts.shared.seconds
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact.DwellExceeded
import com.manahive.contracts.scene.SceneFact.DwellWarning
import com.manahive.contracts.scene.StateKind
import com.manahive.scene.SceneTestDsl.bed
import com.manahive.scene.SceneTestDsl.jose
import com.manahive.scene.SceneTestDsl.maria
import com.manahive.scene.SceneTestDsl.time03_00_00
import com.manahive.scene.SceneTestDsl.time03_02_30
import com.manahive.scene.SceneTestDsl.time03_03_00
import com.manahive.scene.SceneTestDsl.time03_04_00
import com.manahive.scene.SceneTestDsl.time03_05_00
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * SE-18 + SE-20 · DwellCatalog per resident + CalibrationChanged regeneration
 *
 * Each resident has its own SceneCalibration, which derives a DwellCatalog.
 * ClockSweeper resolves the catalog per twin on each sweep:
 * - If twin has calibration → use it
 * - If twin has no calibration → fallback to default
 *
 * When CalibrationChanged arrives, the DigitalTwin.calibration is updated
 * and the next sweep uses the new DwellCatalog automatically.
 *
 * Pattern: Derived Value (Vernon) — DwellCatalog is computed from SceneCalibration.
 * Pattern: Observer (Fowler) — SceneEngine observes CalibrationChanged.
 */
class DwellCatalogSpec : BehaviorSpec({

    // ── Calibrations ───────────────────────────────────────────────────

    val mariaCalibration = calibration {
        dwell {
            STANDING warning 3.minutes exceeded 5.minutes
        }
        heartbeat { timeout = 90.seconds }
    }

    val joseCalibration = calibration {
        dwell {
            STANDING warning 2.minutes exceeded 3.minutes
        }
        heartbeat { timeout = 90.seconds }
    }

    val updatedCalibration = calibration {
        dwell {
            STANDING warning 2.minutes exceeded 3.minutes
        }
        heartbeat { timeout = 90.seconds }
    }

    // ── Default catalog (fallback) ─────────────────────────────────────

    val defaultCatalog = dwellCatalog {
        dwell {
            STANDING warning 4.minutes exceeded 5.minutes
        }
        heartbeat { timeout = 90.seconds }
    }

    // ── Per-resident thresholds ────────────────────────────────────────

    Given("dos residentes con diferentes umbrales de dwell") {
        val sweeper = ClockSweeperImpl()
        val marks = DwellMarks.NONE

        val mariaTwin = bed(1) occupiedBy maria at StateKind.STANDING withCalibration(mariaCalibration) since time03_00_00
        val joseTwin = bed(2) occupiedBy jose at StateKind.STANDING withCalibration(joseCalibration) since time03_00_00

        When("ambos llevan 4 minutos de pie") {
            val result = sweeper.sweep(listOf(mariaTwin, joseTwin), time03_04_00, defaultCatalog, marks)

            Then("Maria recibe warning (threshold 3 min, 4 min >= 3 min)") {
                val warning = result.value.facts.filterIsInstance<DwellWarning>()
                warning.any { it.bed == mariaTwin.bed && it.state == PersonState.Standing } shouldBe true
            }

            Then("Jose recibe exceeded (threshold 3 min, 4 min >= 3 min)") {
                val exceeded = result.value.facts.filterIsInstance<DwellExceeded>()
                exceeded.any { it.bed == joseTwin.bed && it.state == PersonState.Standing } shouldBe true
            }
        }
    }

    Given("un residente con calibration y otro sin calibration") {
        val sweeper = ClockSweeperImpl()
        val marks = DwellMarks.NONE

        val mariaTwin = bed(1) occupiedBy maria at StateKind.STANDING withCalibration(mariaCalibration) since time03_00_00
        val joseTwin = bed(2) occupiedBy jose at StateKind.STANDING since time03_00_00

        When("ambos llevan 4 minutos de pie") {
            val result = sweeper.sweep(listOf(mariaTwin, joseTwin), time03_04_00, defaultCatalog, marks)

            Then("Maria usa su calibration (warning threshold 3 min)") {
                val warning = result.value.facts.filterIsInstance<DwellWarning>()
                warning.any { it.bed == mariaTwin.bed && it.state == PersonState.Standing } shouldBe true
            }

            Then("Jose usa el default (warning threshold 4 min, 4 min >= 4 min)") {
                val warning = result.value.facts.filterIsInstance<DwellWarning>()
                warning.any { it.bed == joseTwin.bed && it.state == PersonState.Standing } shouldBe true
            }
        }
    }

    Given("un residente sin calibration") {
        val sweeper = ClockSweeperImpl()
        val marks = DwellMarks.NONE

        val twin = bed(1) occupiedBy maria at StateKind.STANDING since time03_00_00

        When("sweep a las 03:04:00 (4 min)") {
            val result = sweeper.sweep(listOf(twin), time03_04_00, defaultCatalog, marks)

            Then("usa default catalog (warning 4 min, 4 min >= 4 min)") {
                val warning = result.value.facts.filterIsInstance<DwellWarning>()
                warning.any { it.state == PersonState.Standing } shouldBe true
            }
        }

        When("sweep a las 03:05:00 (5 min)") {
            val result = sweeper.sweep(listOf(twin), time03_05_00, defaultCatalog, marks)

            Then("usa default catalog (exceeded 5 min, 5 min >= 5 min)") {
                val exceeded = result.value.facts.filterIsInstance<DwellExceeded>()
                exceeded.any { it.state == PersonState.Standing } shouldBe true
            }
        }
    }

    // ── CalibrationChanged regeneration ─────────────────────────────────

    Given("un residente con calibration actualizada") {
        val sweeper = ClockSweeperImpl()
        val marks = DwellMarks.NONE

        val twin = bed(1) occupiedBy maria at StateKind.STANDING withCalibration(updatedCalibration) since time03_00_00

        When("sweep a las 03:03:00 (3 min, exceeded threshold)") {
            val result = sweeper.sweep(listOf(twin), time03_03_00, defaultCatalog, marks)

            Then("recibe exceeded (threshold 3 min, 3 min >= 3 min)") {
                val exceeded = result.value.facts.filterIsInstance<DwellExceeded>()
                exceeded.any { it.state == PersonState.Standing } shouldBe true
            }
        }

        When("sweep a las 03:02:30 (2.5 min, after warning threshold)") {
            val result = sweeper.sweep(listOf(twin), time03_02_30, defaultCatalog, marks)

            Then("recibe warning (threshold 2 min, 2.5 min >= 2 min)") {
                val warning = result.value.facts.filterIsInstance<DwellWarning>()
                warning.any { it.state == PersonState.Standing } shouldBe true
            }
        }
    }

    Given("dos residentes con calibraciones diferentes") {
        val sweeper = ClockSweeperImpl()
        val marks = DwellMarks.NONE

        val mariaTwin = bed(1) occupiedBy maria at StateKind.STANDING withCalibration(updatedCalibration) since time03_00_00
        val joseTwin = bed(2) occupiedBy jose at StateKind.STANDING since time03_00_00

        When("sweep a las 03:04:00 (4 min)") {
            val result = sweeper.sweep(listOf(mariaTwin, joseTwin), time03_04_00, defaultCatalog, marks)

            Then("Maria recibe exceeded (calibration threshold 3 min)") {
                val exceeded = result.value.facts.filterIsInstance<DwellExceeded>()
                exceeded.any { it.bed == mariaTwin.bed && it.state == PersonState.Standing } shouldBe true
            }

            Then("Jose NO recibe exceeded (default threshold 5 min)") {
                val exceeded = result.value.facts.filterIsInstance<DwellExceeded>()
                exceeded.none { it.bed == joseTwin.bed && it.state == PersonState.Standing } shouldBe true
            }
        }
    }
})
