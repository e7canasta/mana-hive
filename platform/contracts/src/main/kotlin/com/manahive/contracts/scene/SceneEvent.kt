package com.manahive.contracts.scene

import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import java.time.Duration
import java.time.Instant

/**
 * What the digital twin states about the world — distilled by the scene-engine
 * from noisy observations. Published on `scene.fact.v1.<bed>`. This is the
 * language the sentinel judges against policies.
 */
public sealed interface SceneEvent {
    public val bed: BedId
    public val night: NightId
    public val at: Instant
    public val twinSnapshot: TwinSnapshot? get() = null

    // ── Person State Facts ────────────────────────────────────────

    /** Closing-the-books: the opening entry. Rehydration never reads past it. */
    public data class NightOpened(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val occupant: ResidentId?,
        public val initialState: PersonState,
        public val stateSince: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    public data class TransitionDetected(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val from: PersonState,
        public val to: PersonState,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** Early warning at ~80% of the threshold: "on its way to expire". */
    public data class DwellWarning(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val state: PersonState,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    public data class DwellExceeded(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val state: PersonState,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    // ── Scene State Facts ─────────────────────────────────────────

    /** A scene field changed value (staff, wheelchair, bed rails, etc.) */
    public data class SceneStateChanged(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val field: String,
        public val from: String,
        public val to: String,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** Early warning for scene dwell (e.g., staff present for 10 min). */
    public data class SceneDwellWarning(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val field: String,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** Scene dwell exceeded (e.g., staff present for 30 min). */
    public data class SceneDwellExceeded(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val field: String,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    // ── Signal Facts ──────────────────────────────────────────────

    /** A fact, never a suppression: suppressing alarms is the sentinel's call. */
    public data class StaffPresenceDetected(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val staff: StaffId?,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** Staff left the room. Resident may be alone or taken away. */
    public data class StaffLeftDetected(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** The system that patrols silence models the silence of its own eye. */
    public data class SignalLost(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val monitor: MonitorId,
        public val lastHeartbeat: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    public data class SignalRecovered(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val monitor: MonitorId,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    // ── ComeBack Facts (Inverse Dwell) ─────────────────────────

    /**
     * Early warning for come-back: the resident has been away
     * from the baseline state (e.g., LYING) for longer than the warning threshold.
     *
     * Unlike normal dwell (time IN a state), come-back measures
     * time SINCE LEAVING the baseline state.
     *
     * Vernon: "Domain Event — a fact about something that happened."
     */
    public data class ComeBackWarning(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val baseline: PersonState,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    /** Come-back exceeded: the resident has been away from baseline too long. */
    public data class ComeBackExceeded(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val baseline: PersonState,
        public val threshold: Duration,
        public val since: Instant,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent

    // ── Lifecycle Facts ───────────────────────────────────────────

    public data class NightClosed(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val summary: NightSummary,
        override val twinSnapshot: TwinSnapshot? = null,
    ) : SceneEvent
}

public data class NightSummary(
    public val transitions: Int,
    public val minutesUnknown: Long,
    public val episodes: Int,
) {
    init {
        require(transitions >= 0) { "transitions must be non-negative" }
        require(minutesUnknown >= 0) { "minutesUnknown must be non-negative" }
        require(episodes >= 0) { "episodes must be non-negative" }
    }
}
