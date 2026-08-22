package com.manahive.scene.adapter

import com.manahive.contracts.policy.PolicyCalibration
import com.manahive.scene.calibration.Confidence
import com.manahive.scene.calibration.ConfidenceThresholds
import com.manahive.scene.calibration.SceneCalibration
import com.manahive.scene.core.TransitionTable

/**
 * Converts a [PolicyCalibration] (raw rules from Politica Engine) into a
 * [SceneCalibration] (compiled form for Scene Engine).
 *
 * Pattern: Adapter (Vernon) — bridges the bounded context boundary.
 * The extension function is the simplest form; we can wrap it in a class
 * later if state or configuration is needed.
 *
 * Conversion:
 * - hysteresis map → TransitionTable (via factory)
 * - confidence.minConfidence → minConfidence
 * - confidence.heartbeatTimeout → heartbeatTimeout
 * - dwellThresholds → dwellThresholds
 *
 * Fowler: "Feature Envy" → Use TransitionTable.from() factory.
 *
 * @param base the transition table to overlay overrides on. Defaults to
 *        [TransitionTable.RELEASE_2] (13-state clinical catalog).
 */
public fun PolicyCalibration.toSceneCalibration(
    base: TransitionTable = TransitionTable.RELEASE_2,
): SceneCalibration = SceneCalibration(
    table = TransitionTable.from(base = base, overrides = hysteresis),
    confidence = ConfidenceThresholds(confidence.minConfidence.mapValues { Confidence(it.value) }),
    heartbeatTimeout = confidence.heartbeatTimeout,
    dwellThresholds = dwellThresholds,
)
