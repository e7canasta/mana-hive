package com.manahive.contracts.policy

import com.manahive.contracts.common.Fingerprint
import com.manahive.kernel.ResidentId
import java.time.Instant

/**
 * Sealed hierarchy for policy change events.
 *
 * Each event corresponds to a PolicyCategory:
 * - CalibrationChanged → Scene Engine
 * - ResponseChanged → Sentinel
 * - EscalationChanged → Harbor
 * - RecordingChanged → Recorder
 *
 * Fowler: "Replace Type Code with Subclasses" — sealed interface
 * enables exhaustive when() expressions.
 *
 * Vernon: Domain Event — something that happened in the domain.
 * Each event carries the full state for that category.
 */
public sealed interface PolicyEvent {
    /** The resident this policy change applies to. */
    public val residentId: ResidentId
    /** When this change was detected. */
    public val at: Instant
    /** Version of this policy bucket (monotonically increasing). */
    public val version: Version
    /** Content fingerprint for change detection. */
    public val fingerprint: Fingerprint
}

/**
 * Response rules changed for Sentinel.
 * Contains the full list of alert rules.
 */
public data class ResponseChanged(
    override val residentId: ResidentId,
    override val at: Instant,
    override val version: Version,
    override val fingerprint: Fingerprint,
    val rules: List<AlertRule>,
) : PolicyEvent

/**
 * Escalation config changed for Harbor.
 * Contains the full escalation configuration.
 */
public data class EscalationChanged(
    override val residentId: ResidentId,
    override val at: Instant,
    override val version: Version,
    override val fingerprint: Fingerprint,
    val escalation: EscalationConfig,
) : PolicyEvent

/**
 * Recording config changed for Recorder.
 * Contains the full recording configuration.
 */
public data class RecordingChanged(
    override val residentId: ResidentId,
    override val at: Instant,
    override val version: Version,
    override val fingerprint: Fingerprint,
    val recording: RecordingConfig,
) : PolicyEvent
