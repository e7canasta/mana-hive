package com.manahive.recorder

import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.EpisodeId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Recording commands produced by the Recorder engine.
 *
 * These commands tell the NVR adapter WHAT to record.
 * The adapter handles the actual video recording.
 *
 * Vernon: "Ubiquitous Language" — these are commands, not events.
 * The adapter receives them and executes the recording.
 */
public sealed interface RecordingCommand {
    public val target: RecordingTarget
    public val at: Instant

    /**
     * Convenience: the bed being recorded.
     */
    public val bed: BedId get() = target.bed

    /**
     * Convenience: the monitor recording.
     */
    public val monitor: MonitorId get() = target.monitor
}

/**
 * Start recording on a monitor.
 *
 * @param target Where to record (bed + monitor)
 * @param config Recording configuration (start time + quality)
 * @param context Recording context (standalone or tied to episode)
 * @param at When this event was produced
 */
public data class RecordingStarted(
    override val target: RecordingTarget,
    val config: RecordingConfig,
    val context: RecordingContext,
    override val at: Instant,
) : RecordingCommand {
    /**
     * Convenience constructor for backward compatibility.
     */
    public constructor(
        bed: BedId,
        monitor: MonitorId,
        start: Instant,
        quality: Quality,
        episode: EpisodeId? = null,
        at: Instant,
    ) : this(
        target = RecordingTarget(bed, monitor),
        config = RecordingConfig(start, quality),
        context = if (episode != null) RecordingContext.TiedToEpisode(episode) else RecordingContext.Standalone,
        at = at,
    )

    /**
     * Convenience: the start time.
     */
    public val start: Instant get() = config.start

    /**
     * Convenience: the quality.
     */
    public val quality: Quality get() = config.quality

    /**
     * Convenience: the episode ID if tied to an episode.
     */
    public val episode: EpisodeId? get() = context.episode
}

/**
 * Stop recording on a monitor.
 *
 * @param target Where to record (bed + monitor)
 * @param end When to stop recording
 * @param context Recording context (standalone or tied to episode)
 * @param at When this event was produced
 */
public data class RecordingStopped(
    override val target: RecordingTarget,
    val end: Instant,
    val context: RecordingContext,
    override val at: Instant,
) : RecordingCommand {
    /**
     * Convenience constructor for backward compatibility.
     */
    public constructor(
        bed: BedId,
        monitor: MonitorId,
        end: Instant,
        episode: EpisodeId? = null,
        at: Instant,
    ) : this(
        target = RecordingTarget(bed, monitor),
        end = end,
        context = if (episode != null) RecordingContext.TiedToEpisode(episode) else RecordingContext.Standalone,
        at = at,
    )

    /**
     * Convenience: the episode ID if tied to an episode.
     */
    public val episode: EpisodeId? get() = context.episode
}

/**
 * A video clip has been created from the recording.
 *
 * @param target Where the clip was recorded (bed + monitor)
 * @param episode The episode this clip is evidence for
 * @param start When the clip starts
 * @param end When the clip ends
 * @param path Path to the stored clip
 * @param size Size in bytes (may be 0 if not yet stored)
 * @param at When this event was produced
 */
public data class ClipCreated(
    override val target: RecordingTarget,
    val episode: EpisodeId,
    val start: Instant,
    val end: Instant,
    val path: ClipPath? = null,
    val size: FileSize = FileSize.ZERO,
    override val at: Instant,
) : RecordingCommand {
    /**
     * Convenience constructor for backward compatibility.
     */
    public constructor(
        episode: EpisodeId,
        bed: BedId,
        monitor: MonitorId,
        start: Instant,
        end: Instant,
        path: ClipPath? = null,
        size: FileSize = FileSize.ZERO,
        at: Instant,
    ) : this(
        target = RecordingTarget(bed, monitor),
        episode = episode,
        start = start,
        end = end,
        path = path,
        size = size,
        at = at,
    )
}

/**
 * Recording configuration: start time and quality.
 *
 * Fowler: "Extract Class" — start + quality always go together.
 * They form a cohesive unit: "how to record".
 */
public data class RecordingConfig(
    val start: Instant,
    val quality: Quality,
)

/**
 * Recording quality levels.
 *
 * Vernon: "Ubiquitous Language" — these are clinical monitoring quality levels.
 * - SD: Standard definition — low bandwidth, routine monitoring
 * - HD: High definition — normal monitoring, most scenarios
 * - FULL: Full quality — critical incidents, maximum detail
 *
 * Fowler: "Primitive Obsession" — quality is not just a name, it has behavior.
 * Each level has specific resolution, FPS, and bitrate characteristics.
 */
public data class Quality(
    val name: String,
    val resolution: Resolution,
    val fps: Int,
    val bitrate: Int,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(fps > 0) { "fps must be positive" }
        require(bitrate > 0) { "bitrate must be positive" }
    }

    public companion object {
        /** Standard definition — low bandwidth, routine monitoring */
        public val SD: Quality = Quality(
            name = "SD",
            resolution = Resolution(640, 480),
            fps = 15,
            bitrate = 1_000_000,
        )

        /** High definition — normal monitoring, most scenarios */
        public val HD: Quality = Quality(
            name = "HD",
            resolution = Resolution(1280, 720),
            fps = 30,
            bitrate = 5_000_000,
        )

        /** Full quality — critical incidents, maximum detail */
        public val FULL: Quality = Quality(
            name = "FULL",
            resolution = Resolution(1920, 1080),
            fps = 30,
            bitrate = 10_000_000,
        )

        /** All predefined quality levels */
        public val ALL: List<Quality> = listOf(SD, HD, FULL)
    }

    /**
     * Find a quality level by name.
     */
    public fun findByName(name: String): Quality? = ALL.find { it.name.equals(name, ignoreCase = true) }
}

/**
 * Video resolution: width x height.
 *
 * Fowler: "Value Object" — resolution is a cohesive unit.
 */
public data class Resolution(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }

    /**
     * Aspect ratio as a string (e.g., "16:9").
     */
    public val aspectRatio: String
        get() {
            val gcd = gcd(width, height)
            return "${width / gcd}:${height / gcd}"
        }

    /**
     * Total number of pixels.
     */
    public val totalPixels: Int get() = width * height

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}

/**
 * Input to the Recorder engine.
 *
 * Either a SceneFact or a SentinelSignal can trigger recording.
 * Fowler: "Intention-Revealing Interfaces" — the name should communicate what it is.
 */
public sealed interface RecordingTrigger {
    public val bed: BedId
    public val at: Instant

    /**
     * The recording context for this trigger.
     */
    public val context: RecordingContext
}

/**
 * SceneFact as a recording trigger.
 *
 * Standalone recording: not tied to an episode.
 */
public data class SceneFactTrigger(
    val fact: SceneFact,
    override val bed: BedId,
    override val at: Instant,
) : RecordingTrigger {
    override val context: RecordingContext = RecordingContext.Standalone
}

/**
 * SentinelSignal as a recording trigger.
 *
 * Tied to an episode.
 */
public data class SentinelSignalTrigger(
    val signal: SentinelSignal,
    override val bed: BedId,
    override val at: Instant,
) : RecordingTrigger {
    override val context: RecordingContext = when (signal) {
        is SentinelSignal.EpisodeOpened -> RecordingContext.TiedToEpisode(signal.episode)
        is SentinelSignal.EpisodeClosed -> RecordingContext.TiedToEpisode(signal.episode)
        is SentinelSignal.AutoRecovery -> RecordingContext.TiedToEpisode(signal.episode)
        is SentinelSignal.UmbrellaEvent -> RecordingContext.TiedToEpisode(signal.episode)
        is SentinelSignal.SuppressedWithRecord -> RecordingContext.Standalone
    }
}

/**
 * Value object for a clip file path.
 *
 * Fowler: "Replace Primitive with Object" — a path is not just a string.
 * It enforces non-blank paths and provides domain-meaningful behavior.
 */
@JvmInline
public value class ClipPath(public val value: String) {
    init {
        require(value.isNotBlank()) { "clip path must not be blank" }
    }

    public companion object {
        public fun of(path: String): ClipPath = ClipPath(path)
    }

    override public fun toString(): String = value
}

/**
 * Value object for a file size in bytes.
 *
 * Fowler: "Replace Primitive with Object" — a file size is not just a long.
 * It enforces non-negativity and provides human-readable formatting.
 */
@JvmInline
public value class FileSize(public val bytes: Long) {
    init {
        require(bytes >= 0) { "file size must not be negative" }
    }

    public val isZero: Boolean get() = bytes == 0L

    public val kilobytes: Double get() = bytes / 1024.0

    public val megabytes: Double get() = kilobytes / 1024.0

    public fun format(): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${kilobytes.format(1)} KB"
        else -> "${megabytes.format(1)} MB"
    }

    override public fun toString(): String = format()

    public companion object {
        public val ZERO: FileSize = FileSize(0)

        public fun of(bytes: Long): FileSize = FileSize(bytes)
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}
