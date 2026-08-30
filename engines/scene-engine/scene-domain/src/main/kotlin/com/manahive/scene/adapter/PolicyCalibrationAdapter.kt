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
 * - comeBackThresholds → comeBackThresholds
 * - sceneHysteresis → sceneHysteresis (debounce por campo: la baranda, la silla)
 * - sceneThresholds → sceneThresholds (permanencia por campo)
 *
 * Every field of [PolicyCalibration.scene] must appear here. There used to be
 * a second adapter with the same name in politica-adapters; each carried half
 * the policy — this one dropped comeBackThresholds, that one dropped the
 * hysteresis — and which half a caller lost depended on which import it had
 * picked. That one now delegates here.
 *
 * Fowler: "Feature Envy" → Use TransitionTable.from() factory.
 *
 * @param base the transition table to overlay overrides on. Defaults to
 *        [TransitionTable.RELEASE_2] (13-state clinical catalog).
 */
public fun PolicyCalibration.toSceneCalibration(
    base: TransitionTable = TransitionTable.RELEASE_2,
): SceneCalibration = SceneCalibration(
    table = TransitionTable.from(base = base, overrides = scene.hysteresis),
    confidence = ConfidenceThresholds(scene.confidence.minConfidence.mapValues { Confidence(it.value) }),
    heartbeatTimeout = scene.confidence.heartbeatTimeout,
    dwellThresholds = scene.dwellThresholds,
    comeBackThresholds = scene.comeBackThresholds,
    // El interprete ya consultaba `sceneHysteresisFor(field)` y el barredor ya
    // medía permanencia por campo; los dos leian mapas que nadie llenaba.
    sceneHysteresis = scene.sceneHysteresis,
    sceneThresholds = scene.sceneThresholds,
)
