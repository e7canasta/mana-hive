package com.manahive.scene.batch

import com.manahive.scene.batch.config.BatchConfig
import com.manahive.scene.calibration.DwellCatalog
import com.manahive.scene.core.DigitalTwin
import com.manahive.scene.interpreter.SceneInterpreter
import com.manahive.scene.sweeper.ClockSweeper
import com.manahive.scene.sweeper.DwellMarks
import java.time.Instant

/**
 * Shared mutable state for batch processing.
 *
 * Fowler: "Extract Class" — the (twin, marks, lastTime) clump was
 * duplicated in RunCommand and VerifyState. Now it's a single concept.
 */
data class BatchState(
    val twin: DigitalTwin,
    val marks: DwellMarks,
    val lastTime: Instant,
    val passed: Int = 0,
    val discarded: Int = 0,
    val expectedIndex: Int = 0,
)

/**
 * Immutable context for batch processing — holds all dependencies.
 *
 * Fowler: "Introduce Parameter Object" — replaces long parameter lists.
 * Vernon: "Value Object" — immutable, compared by value.
 */
data class BatchContext(
    val config: BatchConfig,
    val interpreter: SceneInterpreter,
    val sweeper: ClockSweeper,
    val dwellCatalog: DwellCatalog,
    val startTime: Instant,
)
