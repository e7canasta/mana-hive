package com.manahive.scene.calibration.dsl

/**
 * DslMarker for scene engine builders.
 * Prevents scope leakage: inner builders cannot access outer scope accidentally.
 *
 * Naming: @SceneDsl matches @PolicyDsl in contracts/policy.
 */
@DslMarker
public annotation class SceneDsl
