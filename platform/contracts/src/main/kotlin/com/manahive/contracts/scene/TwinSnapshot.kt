package com.manahive.contracts.scene

import com.manahive.kernel.MonitorId
import java.time.Instant

/**
 * TwinSnapshot — Value Object del estado del gemelo en el instante del hecho.
 * Sin domicilio (bed/night/occupant ya están en el sobre/subject).
 * Fowler: Value Object, inmutable, con headers de mensaje aparte.
 */
public data class TwinSnapshot(
    val state: PersonState,
    val stateSince: Instant,
    val scene: SceneState,
    val sceneSince: Instant,
    val signalLost: Boolean,
    val signalLastHeartbeat: Instant,
    val monitor: MonitorId? = null,
)
