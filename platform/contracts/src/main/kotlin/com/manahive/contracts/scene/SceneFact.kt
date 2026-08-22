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
public sealed interface SceneFact {
    public val bed: BedId
    public val night: NightId
    public val at: Instant

    /** Closing-the-books: the opening entry. Rehydration never reads past it. */
    public data class NightOpened(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val occupant: ResidentId?,
        public val initialState: PersonState,
        public val stateSince: Instant,
    ) : SceneFact

    public data class TransitionDetected(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val from: PersonState,
        public val to: PersonState,
    ) : SceneFact

    /** Early warning at ~80% of the threshold: "on its way to expire". */
    public data class DwellWarning(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val state: PersonState,
        public val threshold: Duration,
        public val since: Instant,
    ) : SceneFact

    public data class DwellExceeded(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val state: PersonState,
        public val threshold: Duration,
        public val since: Instant,
    ) : SceneFact

    /** A fact, never a suppression: suppressing alarms is the sentinel's call. */
    public data class StaffPresenceDetected(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val staff: StaffId?,
    ) : SceneFact

    /** The system that patrols silence models the silence of its own eye. */
    public data class SignalLost(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val monitor: MonitorId,
        public val lastHeartbeat: Instant,
    ) : SceneFact

    public data class SignalRecovered(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val monitor: MonitorId,
    ) : SceneFact

    public data class NightClosed(
        override val bed: BedId, override val night: NightId, override val at: Instant,
        public val summary: NightSummary,
    ) : SceneFact
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
