package com.manahive.recorder

import com.manahive.kernel.Engine
import com.manahive.kernel.Explained
import java.time.Instant

/**
 * Recording engine request: bundles parameters for issuing a recording.
 *
 * Fowler: "Introduce Parameter Object" — replaces 6-parameter method.
 */
public data class RecordingRequest(
    val rule: RecordingRule,
    val target: RecordingTarget,
    val context: RecordingContext,
    val startTime: Instant,
    val endTime: Instant,
    val now: Instant,
)

/**
 * The Recorder engine: receives scene facts and sentinel signals, produces recording commands.
 *
 * Same philosophy as other engines:
 * - Created with calibration (RecordingCalibration) — immutable
 * - State flows through (RecordingLedger in, RecordingVerdict out)
 * - Pure function: same input → same output
 * - Now is injected, never Instant.now()
 *
 * Responsible for:
 * - Matching triggers against recording rules
 * - Deciding when to start/stop recording
 * - Producing RecordingCommand instructions for the NVR adapter
 * - Producing EvidenceRecord events for evidence tracking
 *
 * NOT responsible for:
 * - Actual video recording (NVR adapter)
 * - Video storage (NVR adapter)
 * - Clip management (NVR adapter)
 */
public interface RecorderEngine : Engine {
    public fun evaluate(
        trigger: RecordingTrigger,
        ledger: RecordingLedger,
        now: Instant,
    ): Explained<RecordingVerdict>
}

/**
 * Factory function for creating RecorderEngine instances.
 */
public fun createRecorderEngine(calibration: RecordingCalibration): RecorderEngine =
    RecorderEngineImpl(calibration)

/**
 * The output of one evaluation: commands to execute + next ledger state.
 */
public data class RecordingVerdict(
    val commands: List<RecordingCommand>,
    val evidenceRecords: List<EvidenceRecord>,
    val ledger: RecordingLedger,
)
