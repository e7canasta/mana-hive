package com.manahive.recorder

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneFact
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.time.Instant

/**
 * Recording calibration for the Recorder engine.
 *
 * Same pattern as SentinelCalibration and HarborCalibration:
 * - Created per resident (one calibration per bed/night)
 * - Immutable for the engine's lifetime
 * - If rules change, create a new engine with new calibration
 *
 * Defines:
 * - What triggers recording (SceneFact or SentinelSignal)
 * - How long to record before/after the trigger
 * - Which monitors to use
 * - Recording quality
 * - What triggers evidence records
 */
public data class RecordingCalibration(
    public val residentId: ResidentId,
    public val rules: List<RecordingRule>,
    public val evidenceRules: List<EvidenceRule>,
    public val fingerprint: String,
) {
    /**
     * Find all rules that match a given trigger.
     */
    public fun matchingRules(trigger: RecordingTrigger): List<RecordingRule> =
        rules.filter { it.matches(trigger) }

    /**
     * Find all evidence rules that match a given trigger.
     */
    public fun matchingEvidenceRules(trigger: RecordingTrigger): List<EvidenceRule> =
        evidenceRules.filter { it.matches(trigger) }

    public companion object {
        /**
         * Default calibration with no recording rules.
         */
        public fun default(residentId: ResidentId = ResidentId("default")): RecordingCalibration =
            recordingCalibration {
                resident(residentId)
            }
    }
}

/**
 * A single recording rule.
 *
 * Defines when to record, how long, and with what quality.
 *
 * Vernon: "Ubiquitous Language" — the rule name, trigger, and window
 * are domain concepts, not technical abstractions.
 */
public data class RecordingRule(
    public val id: String,
    public val trigger: RecordingTriggerMatcher,
    public val recordingWindow: RecordingWindow,
    public val quality: Quality,
    public val monitors: List<MonitorId>,
) {
    /**
     * Check if this rule matches a given trigger.
     */
    public fun matches(trigger: RecordingTrigger): Boolean = this.trigger.matches(trigger)
}

/**
 * An evidence rule: defines when to emit evidence records.
 *
 * Different from RecordingRule which defines when to start/stop recording.
 * EvidenceRule defines when to produce evidence records that can be used
 * by other systems (Director's report, compliance, etc.).
 *
 * Fowler: "Separate responsibility" — recording commands vs evidence records.
 * Vernon: "Ubiquitous Language" — this is an evidence policy rule.
 */
public data class EvidenceRule(
    public val id: String,
    public val trigger: RecordingTriggerMatcher,
    public val evidenceType: EvidenceType,
    public val window: RecordingWindow = RecordingWindow(Duration.ZERO, Duration.ZERO),
) {
    /**
     * Check if this rule matches a given trigger.
     */
    public fun matches(trigger: RecordingTrigger): Boolean = this.trigger.matches(trigger)
}

/**
 * Types of evidence records.
 *
 * Vernon: "Ubiquitous Language" — these are evidence types in the domain.
 * Fowler: "Replace Primitive with Object" — evidence type is not just a string.
 */
public enum class EvidenceType {
    /** Evidence that a recording was started for an incident */
    INCIDENT,
    /** Evidence that a clip was created from the recording */
    CLIP,
}

/**
 * Recording window: how long before/after the trigger to record.
 *
 * Vernon: "Ubiquitous Language" — this is a "ventana de grabación" in the domain.
 *
 * @param before How long before the trigger to start recording
 * @param after How long after the trigger to stop recording
 */
public data class RecordingWindow(
    public val before: Duration,
    public val after: Duration,
) {
    init {
        require(!before.isNegative) { "before must not be negative" }
        require(!after.isNegative) { "after must not be negative" }
        require(before.toHours() <= 24) { "before must not exceed 24 hours" }
        require(after.toHours() <= 24) { "after must not exceed 24 hours" }
    }

    /**
     * Calculate the start time from the trigger time.
     */
    public fun startTime(triggerTime: Instant): Instant = triggerTime.minus(before)

    /**
     * Calculate the end time from the trigger time.
     */
    public fun endTime(triggerTime: Instant): Instant = triggerTime.plus(after)

    /**
     * Calculate the total duration of the recording window.
     */
    public fun totalDuration(): Duration = before.plus(after)
}

/**
 * Matcher for recording triggers.
 *
 * Can match:
 * - A specific SceneFact type (TransitionDetected, DwellExceeded, etc.)
 * - A specific SentinelSignal type (EpisodeOpened, EpisodeClosed, etc.)
 * - A combination of conditions
 */
public sealed interface RecordingTriggerMatcher {
    /**
     * Check if a trigger matches this matcher.
     */
    public fun matches(trigger: RecordingTrigger): Boolean
}

/**
 * Match a TransitionDetected with specific from/to states.
 */
public data class TransitionMatcher(
    public val from: PersonState? = null,
    public val to: PersonState? = null,
) : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SceneFactTrigger) return false
        val fact = trigger.fact
        if (fact !is SceneFact.TransitionDetected) return false
        if (from != null && fact.from != from) return false
        if (to != null && fact.to != to) return false
        return true
    }
}

/**
 * Match a DwellExceeded with a specific state.
 */
public data class DwellExceededMatcher(
    public val state: PersonState,
) : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SceneFactTrigger) return false
        val fact = trigger.fact
        if (fact !is SceneFact.DwellExceeded) return false
        return fact.state == state
    }
}

/**
 * Match a DwellWarning with a specific state.
 */
public data class DwellWarningMatcher(
    public val state: PersonState,
) : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SceneFactTrigger) return false
        val fact = trigger.fact
        if (fact !is SceneFact.DwellWarning) return false
        return fact.state == state
    }
}

/**
 * Match an EpisodeOpened with optional severity filter.
 */
public data class EpisodeOpenedMatcher(
    public val severity: Severity? = null,
) : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SentinelSignalTrigger) return false
        val signal = trigger.signal
        if (signal !is SentinelSignal.EpisodeOpened) return false
        if (severity != null && signal.severity != severity) return false
        return true
    }
}

/**
 * Match an EpisodeClosed.
 */
public object EpisodeClosedMatcher : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean {
        if (trigger !is SentinelSignalTrigger) return false
        return trigger.signal is SentinelSignal.EpisodeClosed
    }
}

/**
 * Match any trigger (catch-all).
 */
public object AnyMatcher : RecordingTriggerMatcher {
    override public fun matches(trigger: RecordingTrigger): Boolean = true
}
