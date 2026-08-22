package com.manahive.contracts.perception

import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import java.time.Instant

/**
 * What the edge (ia-cell) claims to have seen, already translated from
 * hardware vocabulary to domain vocabulary. Published on
 * `perception.observation.v1.<bed>`. The scene-engine digests these; nothing
 * downstream ever sees hardware words.
 */
public data class Observation(
    public val sourceEventId: String,
    public val monitor: MonitorId,
    public val bed: BedId,
    public val kind: ObservationKind,
    public val confidence: Double,
    public val observedAt: Instant,
) {
    init { require(confidence in 0.0..1.0) { "confidence must be within [0,1]" } }
}

public enum class ObservationKind {
    // ── In bed ────────────────────────────────────────────────
    IN_BED, SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE,

    // ── Out of bed ────────────────────────────────────────────
    STANDING, IN_BATHROOM, IN_ROOM, IN_HALLWAY, OUTDOOR,

    // ── Furniture ─────────────────────────────────────────────
    IN_CHAIR, IN_WHEELCHAIR,

    // ── Scene state (personnel) ───────────────────────────────
    STAFF_ENTERED, STAFF_LEFT, STAFF_IN_REACH,

    // ── Scene state (mobility aids) ───────────────────────────
    WHEELCHAIR_PRESENT, WHEELCHAIR_ABSENT,
    WALKER_PRESENT, WALKER_ABSENT,

    // ── Scene state (bed) ─────────────────────────────────────
    BED_RAILS_UP, BED_RAILS_DOWN,
    COVER_ON, COVER_OFF,

    // ── Meta ──────────────────────────────────────────────────
    OUT_OF_ROOM,
    STAFF_IN_ROOM,
    /** The monitor saying "I am alive". Its silence is a first-class fact. */
    HEARTBEAT,
    UNCLASSIFIED,
}
