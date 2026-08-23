package com.manahive.recorder

import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.MonitorId
import java.time.Instant

/**
 * Evidence record produced by the Recorder engine.
 *
 * This is the "evidence" that has been recorded for a specific incident.
 * Different from RecordingCommand which are instructions for the NVR adapter.
 *
 * Vernon: "Ubiquitous Language" — this is the evidence record.
 * Fowler: "Separate responsibility" — recording commands vs evidence records.
 */
public sealed interface EvidenceRecord {
    public val bed: BedId
    public val at: Instant
}

/**
 * Evidence that a recording was started for an incident.
 *
 * @param bed Where the incident occurred
 * @param episode The episode this evidence is for (if tied to an episode)
 * @param monitors Which monitors recorded the evidence
 * @param start When the evidence recording starts
 * @param trigger What triggered the evidence recording
 * @param at When this record was produced
 */
public data class EvidenceRecordingStarted(
    override val bed: BedId,
    val episode: EpisodeId?,
    val monitors: List<MonitorId>,
    val start: Instant,
    val trigger: String,
    override val at: Instant,
) : EvidenceRecord

/**
 * Evidence that a recording was stopped for an incident.
 *
 * @param bed Where the incident occurred
 * @param episode The episode this evidence is for
 * @param monitors Which monitors recorded the evidence
 * @param end When the evidence recording stops
 * @param at When this record was produced
 */
public data class EvidenceRecordingStopped(
    override val bed: BedId,
    val episode: EpisodeId?,
    val monitors: List<MonitorId>,
    val end: Instant,
    override val at: Instant,
) : EvidenceRecord

/**
 * Evidence that a clip was created from the recording.
 *
 * @param bed Where the incident occurred
 * @param episode The episode this evidence is for
 * @param monitors Which monitors recorded the evidence
 * @param start When the evidence starts
 * @param end When the evidence ends
 * @param path Path to the stored clip (if available)
 * @param size Size of the clip (if known)
 * @param at When this record was produced
 */
public data class EvidenceClipCreated(
    override val bed: BedId,
    val episode: EpisodeId,
    val monitors: List<MonitorId>,
    val start: Instant,
    val end: Instant,
    val path: ClipPath? = null,
    val size: FileSize = FileSize.ZERO,
    override val at: Instant,
) : EvidenceRecord
