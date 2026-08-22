package com.manahive.scene

import com.manahive.contracts.scene.PersonState
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * The living model of one bed: who occupies it, in what state, since when,
 * and whether its own eye is alive. Immutable — evolving it is a fold over
 * scene facts; hydration reads the open night only (closing-the-books).
 *
 * No setters, no ORM identity: the twin is a value.
 */
public data class DigitalTwin(
    public val bed: BedId,
    public val night: NightId,
    public val occupant: ResidentId?,
    public val state: PersonState,
    public val stateSince: Instant,
    public val signal: SignalHealth,
    public val calibration: SceneCalibration? = null,
)

public data class SignalHealth(
    public val monitor: MonitorId,
    public val lastHeartbeat: Instant,
    public val lost: Boolean,
)
