package com.manahive.scene.sweeper

import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.BedId
import java.time.Instant

/** Idempotency marks: what this sweep already emitted, keyed by dwell identity. */
public data class DwellMarks(public val emitted: Set<DwellMarkKey>) {
    public companion object { public val NONE: DwellMarks = DwellMarks(emptySet()) }

    /** Merges two DwellMarks sets. */
    public operator fun plus(other: DwellMarks): DwellMarks = DwellMarks(emitted + other.emitted)
}

/** Discriminator for mark key origin — prevents collision between dwell and signal-lost marks. */
public enum class DwellMarkKind { DWELL, SIGNAL_LOST }

/** Dwell mark key for person state. */
public data class DwellMarkKey(
    public val bed: BedId,
    public val state: StateKind,
    public val since: Instant,
    public val warning: Boolean,
    public val kind: DwellMarkKind = DwellMarkKind.DWELL,
) {
    init {
        require(since != Instant.MAX) { "DwellMarkKey.since must be a real timestamp" }
    }
}

/** Idempotency marks for scene state dwell. */
public data class SceneDwellMarks(public val emitted: Set<SceneDwellMarkKey>) {
    public companion object { public val NONE: SceneDwellMarks = SceneDwellMarks(emptySet()) }

    public operator fun plus(other: SceneDwellMarks): SceneDwellMarks = SceneDwellMarks(emitted + other.emitted)
}

/** Dwell mark key for scene state — semantically correct for scene fields. */
public data class SceneDwellMarkKey(
    public val bed: BedId,
    public val field: String,
    public val since: Instant,
    public val warning: Boolean,
) {
    init {
        require(since != Instant.MAX) { "SceneDwellMarkKey.since must be a real timestamp" }
    }
}

/** Combined dwell marks for person and scene state. */
public data class AllDwellMarks(
    val person: DwellMarks = DwellMarks.NONE,
    val scene: SceneDwellMarks = SceneDwellMarks.NONE,
) {
    public companion object { public val NONE: AllDwellMarks = AllDwellMarks() }

    public operator fun plus(other: AllDwellMarks): AllDwellMarks = AllDwellMarks(
        person = person + other.person,
        scene = scene + other.scene,
    )
}
