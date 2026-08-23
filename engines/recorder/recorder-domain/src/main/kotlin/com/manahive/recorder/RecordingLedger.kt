package com.manahive.recorder

import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.MonitorId
import java.time.Instant

/**
 * Target for recording: which bed and monitor to record from.
 *
 * Fowler: "Extract Class" — bed + monitor always go together.
 * They form a cohesive unit: "where to record".
 */
public data class RecordingTarget(
    val bed: BedId,
    val monitor: MonitorId,
) {
    init {
        require(bed.value.isNotBlank()) { "bed must not be blank" }
        require(monitor.value.isNotBlank()) { "monitor must not be blank" }
    }
}

/**
 * Context for recording: is this tied to an episode or standalone?
 *
 * Vernon: "Ubiquitous Language" — this is the "context" of the recording.
 * - Standalone: triggered by SceneFact (not tied to an episode)
 * - TiedToEpisode: triggered by SentinelSignal (tied to an episode)
 */
public sealed interface RecordingContext {
    /**
     * Standalone recording: triggered by SceneFact, not tied to an episode.
     */
    public data object Standalone : RecordingContext

    /**
     * Recording tied to an episode: triggered by SentinelSignal.
     */
    public data class TiedToEpisode(val episodeId: EpisodeId) : RecordingContext

    /**
     * The episode ID if tied to an episode.
     */
    public val episode: EpisodeId? get() = when (this) {
        is TiedToEpisode -> episodeId
        is Standalone -> null
    }
}

/**
 * Key for identifying a recording.
 *
 * Fowler: "Replace Data Value Object with Object" — but this is truly a value.
 * The key is immutable and used for lookup.
 *
 * Composed of:
 * - `target`: where to record (bed + monitor)
 * - `context`: why to record (standalone or tied to episode)
 */
public data class RecordingKey(
    val target: RecordingTarget,
    val context: RecordingContext,
) {
    /**
     * Convenience constructor for backward compatibility.
     */
    public constructor(bed: BedId, monitor: MonitorId, episode: EpisodeId? = null) : this(
        target = RecordingTarget(bed, monitor),
        context = if (episode != null) RecordingContext.TiedToEpisode(episode) else RecordingContext.Standalone,
    )

    /**
     * The bed being recorded.
     */
    public val bed: BedId get() = target.bed

    /**
     * The monitor recording.
     */
    public val monitor: MonitorId get() = target.monitor

    /**
     * The episode ID if tied to an episode.
     */
    public val episode: EpisodeId? get() = context.episode
}

/**
 * In-memory ledger of recording state.
 *
 * Same pattern as NoticeRegistry and EpisodeLedger:
 * - Immutable, copied on every change
 * - Pure value object
 * - No side effects
 *
 * Tracks:
 * - Active recordings (started but not stopped)
 * - Scheduled stops (will be confirmed by adapter)
 * - Completed recordings (stopped and confirmed)
 */
@JvmInline
public value class RecordingLedger(
    public val active: Map<RecordingKey, RecordingState> = emptyMap(),
) {
    /**
     * Start a new recording.
     */
    public fun startRecording(event: RecordingStarted): RecordingLedger {
        val key = RecordingKey(event.target, event.context)
        val state = RecordingState(
            started = event,
            stopped = null,
            clip = null,
        )
        return RecordingLedger(active + (key to state))
    }

    /**
     * Schedule a stop for an active recording.
     */
    public fun scheduleStop(event: RecordingStopped): RecordingLedger {
        val key = RecordingKey(event.target, event.context)
        val currentState = active[key] ?: return this
        val updatedState = currentState.copy(stopped = event)
        return RecordingLedger(active + (key to updatedState))
    }

    /**
     * Confirm a recording was stopped (by adapter).
     */
    public fun confirmStop(key: RecordingKey): RecordingLedger {
        val currentState = active[key] ?: return this
        return RecordingLedger(active - key)
    }

    /**
     * Add a clip to a recording.
     */
    public fun addClip(key: RecordingKey, clip: ClipCreated): RecordingLedger {
        val currentState = active[key] ?: return this
        val updatedState = currentState.copy(clip = clip)
        return RecordingLedger(active + (key to updatedState))
    }

    /**
     * Find an active recording for a bed and optional episode.
     */
    public fun findActive(bed: BedId, episode: EpisodeId? = null): RecordingState? =
        active.values.find { state ->
            state.started.target.bed == bed &&
            (episode == null || state.started.context.episode == episode)
        }

    /**
     * Get all active recordings for a bed.
     */
    public fun findActiveForBed(bed: BedId): List<RecordingState> =
        active.values.filter { it.started.target.bed == bed }

    /**
     * Get all active recordings for an episode.
     */
    public fun findActiveForEpisode(episode: EpisodeId): List<RecordingState> =
        active.values.filter { it.started.context.episode == episode }

    /**
     * Check if a bed is being recorded.
     */
    public fun isRecording(bed: BedId): Boolean =
        active.keys.any { it.bed == bed }

    /**
     * Check if an episode is being recorded.
     */
    public fun isRecordingEpisode(episode: EpisodeId): Boolean =
        active.keys.any { it.episode == episode }

    /**
     * Get the number of active recordings.
     */
    public fun activeCount(): Int = active.size
}

/**
 * State of a single recording.
 *
 * Vernon: "Ubiquitous Language" — this is the recording lifecycle state.
 * Fowler: "Rich Domain Model" — this object has behavior, not just data.
 * The recording follows a lifecycle: started → stopped → clip created.
 */
public data class RecordingState(
    public val started: RecordingStarted,
    public val stopped: RecordingStopped? = null,
    public val clip: ClipCreated? = null,
) {
    /**
     * Is this recording still active (started but not stopped)?
     */
    public val isActive: Boolean get() = stopped == null

    /**
     * Is this recording completed (stopped)?
     */
    public val isCompleted: Boolean get() = stopped != null

    /**
     * Does this recording have a clip?
     */
    public val hasClip: Boolean get() = clip != null

    /**
     * Can this recording be stopped?
     *
     * Only active recordings can be stopped.
     */
    public fun canStop(): Boolean = isActive

    /**
     * Can a clip be added to this recording?
     *
     * Only completed recordings (stopped) can have clips added.
     */
    public fun canAddClip(): Boolean = isCompleted && !hasClip

    /**
     * Stop this recording at the given time.
     *
     * @param at When to stop the recording
     * @return A new RecordingState with the stop event
     * @throws IllegalStateException if the recording is not active
     */
    public fun stop(at: java.time.Instant): RecordingState {
        require(canStop()) { "Cannot stop recording that is not active" }
        val stopped = RecordingStopped(
            target = started.target,
            end = at,
            context = started.context,
            at = at,
        )
        return copy(stopped = stopped)
    }

    /**
     * Add a clip to this recording.
     *
     * @param clip The clip to add
     * @return A new RecordingState with the clip
     * @throws IllegalStateException if the recording is not completed or already has a clip
     */
    public fun withClip(clip: ClipCreated): RecordingState {
        require(canAddClip()) { "Cannot add clip to recording that is not completed or already has a clip" }
        return copy(clip = clip)
    }

    /**
     * Get the recording duration (if stopped).
     */
    public fun duration(): java.time.Duration? {
        val stop = stopped ?: return null
        return java.time.Duration.between(started.config.start, stop.end)
    }
}
