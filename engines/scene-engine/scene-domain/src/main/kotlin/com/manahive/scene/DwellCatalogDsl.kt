package com.manahive.scene

import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.shared.HeartbeatBuilder
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * DSL for building [DwellCatalog] instances.
 *
 * ```kotlin
 * val catalog = dwellCatalog {
 *     dwell {
 *         STANDING warning 4.minutes exceeded 5.minutes
 *         BED_EDGE warning 8.minutes exceeded 10.minutes
 *     }
 *     heartbeat { timeout = 90.seconds }
 * }
 * ```
 *
 * Uses the shared [DwellThresholdsBuilder] — no parallel hierarchy (Fowler).
 */
public fun dwellCatalog(block: DwellCatalogBuilder.() -> Unit): DwellCatalog =
    DwellCatalogBuilder().apply(block).build()

@SceneDsl
public class DwellCatalogBuilder {
    private val dwellThresholds = mutableMapOf<StateKind, DwellThreshold>()

    private var heartbeatTimeout: Duration = Duration.ofSeconds(90)

    public fun dwell(block: DwellThresholdsBuilder.() -> Unit) {
        DwellThresholdsBuilder(dwellThresholds).apply(block)
    }

    public fun heartbeat(block: HeartbeatBuilder.() -> Unit) {
        HeartbeatBuilder().apply(block).also { heartbeatTimeout = it.timeout }
    }

    internal fun build(): DwellCatalog = DwellCatalog(
        byState = dwellThresholds.toMap(),
        heartbeatTimeout = heartbeatTimeout,
    )
}
