package com.manahive.sentinel.batch

import com.manahive.kernel.BedId
import com.manahive.kernel.NightId
import com.manahive.sentinel.EpisodeLedger
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.SentinelEvaluator
import com.manahive.sentinel.batch.config.BatchConfig
import java.time.Instant

/**
 * Shared mutable state for batch processing.
 *
 * Fowler: "Extract Class" — the (ledger, lastTime) clump was
 * duplicated in RunCommand and VerifyCommand. Now it's a single concept.
 */
data class BatchState(
    val ledger: EpisodeLedger,
    val lastTime: Instant,
    val passed: Int = 0,
    val suppressed: Int = 0,
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
    val evaluator: SentinelEvaluator,
    val calibration: SentinelCalibration,
    val startTime: Instant,
    /** Pre-resolved bed ID — avoids recreation on every event. */
    val bedId: BedId,
    /** Pre-resolved night ID — avoids recreation on every event. */
    val nightId: NightId,
)
