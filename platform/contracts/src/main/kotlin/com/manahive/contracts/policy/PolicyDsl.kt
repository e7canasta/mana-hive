package com.manahive.contracts.policy

/**
 * DslMarker for policy calibration builders.
 * Prevents scope leakage: inner builders cannot access outer scope accidentally.
 */
@DslMarker
public annotation class PolicyDsl
