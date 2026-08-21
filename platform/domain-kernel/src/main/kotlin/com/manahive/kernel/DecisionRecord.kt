package com.manahive.kernel

/**
 * Durable trace of one engine invocation — the answer to "why did the alarm
 * (not) ring at 03:12?". Persisted append-only by the hub, outside the domain
 * ledger: it is judgment telemetry, not business fact.
 *
 * The triple (inputs fingerprints, rules fingerprint, engine version) makes
 * every decision machine-reproducible.
 */
public data class DecisionRecord(
    public val engine: EngineVersion,
    public val stimulus: EventRef,
    public val inputs: Map<String, String>,
    public val output: String,
    public val explanation: List<ExplanationStep>,
    public val discards: List<Discard>,
    public val tookMillis: Long,
)
