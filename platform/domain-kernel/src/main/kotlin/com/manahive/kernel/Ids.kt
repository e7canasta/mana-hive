package com.manahive.kernel

/**
 * Strongly-typed identities. A BedId can never be passed where a ResidentId
 * is expected; the compiler is the reviewer. Zero runtime cost.
 */
@JvmInline public value class BedId(public val value: String) {
    init { require(value.isNotBlank()) { "BedId must not be blank" } }
}
@JvmInline public value class ResidentId(public val value: String) {
    init { require(value.isNotBlank()) { "ResidentId must not be blank" } }
}
@JvmInline public value class MonitorId(public val value: String) {
    init { require(value.isNotBlank()) { "MonitorId must not be blank" } }
}
@JvmInline public value class StaffId(public val value: String) {
    init { require(value.isNotBlank()) { "StaffId must not be blank" } }
}
@JvmInline public value class AlertId(public val value: String) {
    init { require(value.isNotBlank()) { "AlertId must not be blank" } }
}
@JvmInline public value class RuleId(public val value: String) {
    init { require(value.isNotBlank()) { "RuleId must not be blank" } }
}
@JvmInline public value class EpisodeId(public val value: String) {
    init { require(value.isNotBlank()) { "EpisodeId must not be blank" } }
}
@JvmInline public value class NoticeId(public val value: String) {
    init { require(value.isNotBlank()) { "NoticeId must not be blank" } }
    public companion object {
        /** Create a NoticeId from an EpisodeId. One notice per episode. */
        public fun fromEpisode(episodeId: EpisodeId): NoticeId =
            NoticeId("${episodeId.value}-notice")
    }
}

/** The accounting period of the scene stream: one night per bed (closing-the-books). */
@JvmInline public value class NightId(public val value: String) {
    init { require(value.isNotBlank()) { "NightId must not be blank" } }
}

/** A pointer to an event already accepted somewhere (bus sequence or ledger sequence). */
public data class EventRef(public val stream: String, public val seq: Long)

/** Identifier for a Directed Acyclic Graph. */
@JvmInline public value class DagId(public val value: String) {
    init { require(value.isNotBlank()) { "DagId must not be blank" } }
}

/** Identifier for a node in a DAG. */
@JvmInline public value class NodeId(public val value: String) {
    init { require(value.isNotBlank()) { "NodeId must not be blank" } }
}
