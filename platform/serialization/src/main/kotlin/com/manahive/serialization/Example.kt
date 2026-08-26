package com.manahive.serialization

import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.NightSummary
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.common.Channel
import com.manahive.kernel.*
import java.time.Duration
import java.time.Instant

/**
 * Example demonstrating serialization usage.
 *
 * Shows how to:
 * 1. Serialize domain events to JSON/Text
 * 2. Deserialize from JSON/Text back to objects
 * 3. Use in blueprints and batch processing
 */
fun main() {
    val startTime = Instant.parse("2024-01-15T22:00:00Z")
    val bed = BedId("bed-301")
    val night = NightId("night-301")

    // ── SceneEvent Serialization ─────────────────────────────────────────────

    println("=== SceneEvent Serialization ===")
    println()

    // Create a TransitionDetected event
    val transition = SceneEvent.TransitionDetected(
        bed = bed,
        night = night,
        at = startTime.plusSeconds(3600), // 1 hour later
        from = PersonState.Lying,
        to = PersonState.SittingInBed,
    )

    // Serialize to JSON
    val transitionJson = SceneEventSerializer.toJson(transition)
    println("JSON:")
    println(transitionJson)
    println()

    // Deserialize from JSON
    val restoredTransition = SceneEventSerializer.fromJson(transitionJson)
    println("Restored: ${restoredTransition::class.simpleName}")
    println("  From: ${(restoredTransition as SceneEvent.TransitionDetected).from::class.simpleName}")
    println("  To: ${restoredTransition.to::class.simpleName}")
    println()

    // Serialize to text format
    val transitionText = SceneEventSerializer.toText(transition, startTime)
    println("Text format:")
    println("  t=${transitionText.offset}  ${transitionText.type} ${transitionText.details}")
    println()

    // ── SentinelSignal Serialization ─────────────────────────────────────────

    println("=== SentinelSignal Serialization ===")
    println()

    // Create an EpisodeOpened signal
    val signal = SentinelSignal.EpisodeOpened(
        bed = bed,
        resident = ResidentId("jose"),
        at = startTime.plusSeconds(3600),
        rulesFingerprint = "abc123",
        episode = EpisodeId("ep-001"),
        rule = RuleId("r-sitting"),
        trigger = StateKind.SITTING_IN_BED,
        severity = Severity.WARNING,
        reversible = true,
        requiresNvr = false,
        confirmationWindow = Duration.ofMinutes(5),
    )

    // Serialize to JSON
    val signalJson = SentinelSignalSerializer.toJson(signal)
    println("JSON:")
    println(signalJson)
    println()

    // Deserialize from JSON
    val restoredSignal = SentinelSignalSerializer.fromJson(signalJson)
    println("Restored: ${restoredSignal::class.simpleName}")
    println("  Episode: ${(restoredSignal as SentinelSignal.EpisodeOpened).episode.value}")
    println("  Rule: ${restoredSignal.rule.value}")
    println("  Severity: ${restoredSignal.severity}")
    println()

    // ── NoticeCommand Serialization ──────────────────────────────────────────

    println("=== NoticeCommand Serialization ===")
    println()

    // Create a Dispatch command
    val command = com.manahive.harbor.NoticeCommand.Dispatch(
        id = NoticeId("notice-001"),
        channels = setOf(Channel.CONSOLE, Channel.PUSH),
    )

    // Serialize to JSON
    val commandJson = NoticeCommandSerializer.toJson(command)
    println("JSON:")
    println(commandJson)
    println()

    // Deserialize from JSON
    val restoredCommand = NoticeCommandSerializer.fromJson(commandJson)
    println("Restored: ${restoredCommand::class.simpleName}")
    println("  Channels: ${(restoredCommand as com.manahive.harbor.NoticeCommand.Dispatch).channels}")
    println()

    // ── Use Cases ────────────────────────────────────────────────────────────

    println("=== Use Cases ===")
    println()
    println("1. Blueprint scenarios: save/load test scenarios")
    println("   - Write scenario in code → serialize to JSON → save to file")
    println("   - Load from file → deserialize → run in batch")
    println()
    println("2. Event Sourcing: persist domain events")
    println("   - Serialize SceneEvent to JSON → store in database")
    println("   - Load from database → deserialize → replay events")
    println()
    println("3. Batch processing: read/write .dat/.out files")
    println("   - Parse events.dat → EventParser → List<Event>")
    println("   - Process through engines → produce outputs")
    println("   - Serialize outputs → write to .out files")
    println()
    println("4. Real-time monitoring: dump state for debugging")
    println("   - Serialize current state → write to file")
    println("   - Load file → inspect in editor/IDE")
    println()
    println("✅ Serialization module ready!")
}
