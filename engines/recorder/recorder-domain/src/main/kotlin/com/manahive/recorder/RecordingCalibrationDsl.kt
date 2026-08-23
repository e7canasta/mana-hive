package com.manahive.recorder

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.kernel.MonitorId
import com.manahive.kernel.ResidentId
import java.time.Duration

/**
 * Type-safe DSL for building [RecordingCalibration] instances.
 *
 * Example:
 * ```kotlin
 * val calibration = recordingCalibration {
 *     resident("maria")
 *
 *     rule("r-fall-recording") {
 *         trigger {
 *             transition(from = PersonState.Lying, to = PersonState.Standing)
 *         }
 *         recordingWindow {
 *             before = 2.minutes
 *             after = 5.minutes
 *         }
 *         quality = Quality.HD
 *         monitors = listOf(MonitorId("CAMERA_12A_MAIN"))
 *     }
 *
 *     rule("r-bathroom-recording") {
 *         trigger {
 *             dwellExceeded(state = PersonState.InBathroom)
 *         }
 *         recordingWindow {
 *             before = 5.minutes
 *             after = 10.minutes
 *         }
 *         quality = Quality.HD
 *         monitors = listOf(MonitorId("CAMERA_12A_MAIN"))
 *     }
 * }
 * ```
 *
 * Fowler: "Internal DSL" — type-safe, IDE completion, no parsing.
 * Vernon: "Ubiquitous Language" — recordingWindow is a domain concept.
 */
public fun recordingCalibration(init: RecordingCalibrationBuilder.() -> Unit): RecordingCalibration =
    RecordingCalibrationBuilder().apply(init).build()

@RecorderDsl
public class RecordingCalibrationBuilder {
    private var residentId: ResidentId = ResidentId("default")
    private val rules = mutableListOf<RecordingRuleBuilder>()
    private val evidenceRules = mutableListOf<EvidenceRuleBuilder>()

    /** Set the resident ID for this calibration. */
    public fun resident(id: String) {
        residentId = ResidentId(id)
    }

    /** Set the resident ID for this calibration. */
    public fun resident(id: ResidentId) {
        residentId = id
    }

    /** Add a recording rule. */
    public fun rule(id: String, init: RecordingRuleBuilder.() -> Unit) {
        rules.add(RecordingRuleBuilder(id).apply(init))
    }

    /** Add an evidence rule. */
    public fun evidenceRule(id: String, init: EvidenceRuleBuilder.() -> Unit) {
        evidenceRules.add(EvidenceRuleBuilder(id).apply(init))
    }

    internal fun build(): RecordingCalibration {
        val builtRules = rules.map { it.build() }
        val builtEvidenceRules = evidenceRules.map { it.build() }
        val fingerprint = buildString {
            append("resident=${residentId.value};")
            builtRules.sortedBy { it.id }.forEach { rule ->
                append("${rule.id}=${rule.trigger};")
            }
            builtEvidenceRules.sortedBy { it.id }.forEach { rule ->
                append("evidence-${rule.id}=${rule.trigger};")
            }
        }.hashCode().toString(16)
        return RecordingCalibration(
            residentId = residentId,
            rules = builtRules,
            evidenceRules = builtEvidenceRules,
            fingerprint = fingerprint,
        )
    }
}

/**
 * Shared trigger + window configuration for rule builders.
 *
 * Fowler: "Extract Superclass" — eliminates duplicate trigger/recordingWindow code.
 */
@RecorderDsl
public abstract class TriggerMixin {
    protected var triggerMatcher: RecordingTriggerMatcher = AnyMatcher
    protected var beforeDuration: Duration = Duration.ZERO
    protected var afterDuration: Duration = Duration.ZERO

    /** Configure the trigger for this rule. */
    public fun trigger(init: TriggerMatcherBuilder.() -> Unit) {
        triggerMatcher = TriggerMatcherBuilder().apply(init).build()
    }

    /** Configure the recording window (before/after the trigger). */
    public fun recordingWindow(init: RecordingWindowBuilder.() -> Unit) {
        val builder = RecordingWindowBuilder().apply(init)
        beforeDuration = builder.before
        afterDuration = builder.after
    }

    protected fun buildWindow(): RecordingWindow =
        RecordingWindow(before = beforeDuration, after = afterDuration)
}

@RecorderDsl
public class RecordingRuleBuilder(private val id: String) : TriggerMixin() {
    private var recordingQuality: Quality = Quality.HD
    private val monitorIds = mutableListOf<MonitorId>()

    /** Recording quality. */
    public var quality: Quality
        get() = recordingQuality
        set(value) { recordingQuality = value }

    /** Monitors to record from. */
    public var monitors: List<MonitorId>
        get() = monitorIds
        set(value) { monitorIds.clear(); monitorIds.addAll(value) }

    internal fun build(): RecordingRule = RecordingRule(
        id = id,
        trigger = triggerMatcher,
        recordingWindow = buildWindow(),
        quality = recordingQuality,
        monitors = monitorIds.toList(),
    )
}

@RecorderDsl
public class EvidenceRuleBuilder(private val id: String) : TriggerMixin() {
    private var evidenceTypeValue: EvidenceType = EvidenceType.INCIDENT

    /** The type of evidence this rule produces. */
    public var evidenceType: EvidenceType
        get() = evidenceTypeValue
        set(value) { evidenceTypeValue = value }

    internal fun build(): EvidenceRule = EvidenceRule(
        id = id,
        trigger = triggerMatcher,
        evidenceType = evidenceTypeValue,
        window = buildWindow(),
    )
}

@RecorderDsl
public class RecordingWindowBuilder {
    /** How long before the trigger to start recording. */
    public var before: Duration = Duration.ZERO

    /** How long after the trigger to stop recording. */
    public var after: Duration = Duration.ZERO
}

@RecorderDsl
public class TriggerMatcherBuilder {
    private var matcher: RecordingTriggerMatcher = AnyMatcher

    /** Match a transition between states. */
    public fun transition(from: PersonState? = null, to: PersonState? = null) {
        matcher = TransitionMatcher(from, to)
    }

    /** Match a dwell exceeded for a specific state. */
    public fun dwellExceeded(state: PersonState) {
        matcher = DwellExceededMatcher(state)
    }

    /** Match a dwell warning for a specific state. */
    public fun dwellWarning(state: PersonState) {
        matcher = DwellWarningMatcher(state)
    }

    /** Match an episode opened with optional severity filter. */
    public fun episodeOpened(severity: Severity? = null) {
        matcher = EpisodeOpenedMatcher(severity)
    }

    /** Match an episode closed. */
    public fun episodeClosed() {
        matcher = EpisodeClosedMatcher
    }

    /** Match any trigger (catch-all). */
    public fun any() {
        matcher = AnyMatcher
    }

    internal fun build(): RecordingTriggerMatcher = matcher
}

@DslMarker
public annotation class RecorderDsl

// ── Duration extensions for DSL fluency ──

public val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
public val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
public val Int.hours: Duration get() = Duration.ofHours(this.toLong())
