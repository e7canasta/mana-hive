package com.manahive.kernel

/**
 * Strongly-typed identities. A BedId can never be passed where a ResidentId
 * is expected; the compiler is the reviewer. Zero runtime cost.
 */
@JvmInline public value class BedId(public val value: String)
@JvmInline public value class ResidentId(public val value: String)
@JvmInline public value class MonitorId(public val value: String)
@JvmInline public value class StaffId(public val value: String)
@JvmInline public value class AlertId(public val value: String)
@JvmInline public value class RuleId(public val value: String)
@JvmInline public value class EpisodeId(public val value: String)

/** The accounting period of the scene stream: one night per bed (closing-the-books). */
@JvmInline public value class NightId(public val value: String)

/** A pointer to an event already accepted somewhere (bus sequence or ledger sequence). */
public data class EventRef(public val stream: String, public val seq: Long)
