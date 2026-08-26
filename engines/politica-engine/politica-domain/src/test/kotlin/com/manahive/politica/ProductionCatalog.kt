package com.manahive.politica

import com.manahive.contracts.policy.AlarmCatalog
import com.manahive.contracts.policy.CatalogVersion
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.Template
import com.manahive.contracts.policy.TemplateId
import com.manahive.contracts.policy.TransitionKey
import com.manahive.contracts.scene.StateKind
import java.time.Duration

/**
 * Production AlarmCatalog for testing template resolution.
 * Mirrors the level catalogs but uses the legacy AlarmCatalog API.
 */
val PRODUCTION_CATALOG = AlarmCatalog(
    version = CatalogVersion("1.0.0"),
    transitions = mapOf(
        TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1500),
        TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofMillis(1500),
        TransitionKey(StateKind.LYING, StateKind.STANDING) to Duration.ofMillis(2000),
        TransitionKey(StateKind.BED_EDGE, StateKind.STANDING) to Duration.ofMillis(1500),
        TransitionKey(StateKind.STANDING, StateKind.IN_BATHROOM) to Duration.ofMillis(2000),
    ),
    dwellThresholds = mapOf(
        StateKind.SITTING_IN_BED to DwellThreshold(
            warning = Duration.ofMinutes(30),
            exceeded = Duration.ofMinutes(45),
        ),
        StateKind.IN_BATHROOM to DwellThreshold(
            warning = Duration.ofMinutes(20),
            exceeded = Duration.ofMinutes(30),
        ),
    ),
    templates = mapOf(
        TemplateId("standard") to Template(
            id = TemplateId("standard"),
            hysteresis = emptyMap(),
            dwellThresholds = emptyMap(),
        ),
        TemplateId("night-wandering") to Template(
            id = TemplateId("night-wandering"),
            hysteresis = mapOf(
                TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofMillis(1000),
                TransitionKey(StateKind.LYING, StateKind.BED_EDGE) to Duration.ofMillis(1000),
                TransitionKey(StateKind.LYING, StateKind.STANDING) to Duration.ofMillis(1000),
            ),
            dwellThresholds = mapOf(
                StateKind.SITTING_IN_BED to DwellThreshold(
                    warning = Duration.ofMinutes(20),
                    exceeded = Duration.ofMinutes(30),
                ),
                StateKind.IN_BATHROOM to DwellThreshold(
                    warning = Duration.ofMinutes(15),
                    exceeded = Duration.ofMinutes(25),
                ),
                StateKind.STANDING to DwellThreshold(
                    warning = Duration.ofMinutes(10),
                    exceeded = Duration.ofMinutes(15),
                ),
            ),
        ),
        TemplateId("fall-risk") to Template(
            id = TemplateId("fall-risk"),
            hysteresis = mapOf(
                TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofMillis(2000),
                TransitionKey(StateKind.LYING, StateKind.STANDING) to Duration.ofMillis(3000),
            ),
            dwellThresholds = mapOf(
                StateKind.STANDING to DwellThreshold(
                    warning = Duration.ofMinutes(2),
                    exceeded = Duration.ofMinutes(3),
                ),
                StateKind.BED_EDGE to DwellThreshold(
                    warning = Duration.ofMinutes(1),
                    exceeded = Duration.ofMinutes(2),
                ),
            ),
        ),
        TemplateId("low-mobility") to Template(
            id = TemplateId("low-mobility"),
            hysteresis = mapOf(
                TransitionKey(StateKind.LYING, StateKind.SITTING_IN_BED) to Duration.ofMillis(2500),
                TransitionKey(StateKind.LYING, StateKind.STANDING) to Duration.ofMillis(3000),
            ),
            dwellThresholds = mapOf(
                StateKind.SITTING_IN_BED to DwellThreshold(
                    warning = Duration.ofMinutes(45),
                    exceeded = Duration.ofMinutes(60),
                ),
                StateKind.IN_BATHROOM to DwellThreshold(
                    warning = Duration.ofMinutes(30),
                    exceeded = Duration.ofMinutes(45),
                ),
            ),
        ),
    ),
)
