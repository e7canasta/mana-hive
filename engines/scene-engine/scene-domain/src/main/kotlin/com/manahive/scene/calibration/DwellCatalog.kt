package com.manahive.scene.calibration

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.PolicyDefaults
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Dwell thresholds keyed by state. Each [DwellThreshold] holds both
 * warning and exceeded durations — no separate maps (Fowler: Data Clumps).
 */
public data class DwellCatalog(
    public val byState: Map<StateKind, DwellThreshold>,
    public val heartbeatTimeout: Duration = PolicyDefaults.heartbeatTimeout,
    public val sceneThresholds: Map<String, DwellThreshold> = emptyMap(),
)

/**
 * Derives a [DwellCatalog] from this [SceneCalibration].
 *
 * Each resident can have different dwell thresholds; this conversion
 * makes the calibration consumable by [com.manahive.scene.sweeper.ClockSweeper].
 *
 * Fowler: "Derived Value" — the catalog is computed from the calibration,
 * never stored independently.
 */
public fun SceneCalibration.toDwellCatalog(): DwellCatalog = DwellCatalog(
    byState = dwellThresholds.toMap(),
    heartbeatTimeout = heartbeatTimeout,
    sceneThresholds = sceneThresholds,
)
