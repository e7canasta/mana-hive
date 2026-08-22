package com.manahive.contracts.policy

import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * The catalog of alarm rules: transitions, dwell thresholds, templates.
 * Loaded from TOML, validated, versioned. Pure data — no identity.
 */
public data class AlarmCatalog(
    public val transitions: Map<TransitionKey, Duration>,
    public val dwellThresholds: Map<StateKind, DwellThreshold>,
    public val templates: Map<TemplateId, Template>,
    public val version: CatalogVersion,
)

public data class Template(
    public val id: TemplateId,
    public val hysteresis: Map<TransitionKey, Duration>,
    public val dwellThresholds: Map<StateKind, DwellThreshold>,
)
