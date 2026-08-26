package jose301recording

import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.PersonState
import com.manahive.recorder.*
import com.manahive.recorder.bdd.scenario
import java.time.Instant

fun main() {
    val ctx = com.manahive.recorder.bdd.RecorderContext(
        bed = BED_4,
        resident = JOSE,
        calibration = configCompleta,
    )

    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Recording Rules")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Fall detection → recording started ───────────────────────────────

    ctx.scenario("Fall detection inicia recording") {
        transitionDetected(
            from = PersonState.Lying, to = PersonState.Standing,
            at = START.plusSeconds(180),
        )

        thenExpectRecordingStarted { cmd ->
            assert(cmd.quality == Quality.HD) { "Expected HD quality" }
            assert(cmd.target.monitor == CAM_MAIN) { "Expected single camera" }
            assert(cmd.context is RecordingContext.Standalone) { "Expected standalone context" }
        }
    }.report()

    // ── 2. Dwell exceeded → recording started ──────────────────────────────

    ctx.scenario("Dwell exceeded inicia recording") {
        dwellExceeded(
            state = PersonState.InBathroom,
            at = START.plusSeconds(3600),
        )

        thenExpectRecordingStarted { cmd ->
            assert(cmd.quality == Quality.HD) { "Expected HD quality" }
        }
    }.report()

    // ── 3. Critical episode → multi-camera recording ───────────────────────

    ctx.scenario("Critical episode graba multicam") {
        episodeOpened(
            episodeId = "ep-critical-1",
            severity = Severity.CRITICAL,
            at = START.plusSeconds(7200),
        )

        thenExpectRecordingStarted { cmd ->
            assert(cmd.quality == Quality.FULL) { "Expected FULL quality" }
            assert(cmd.context is RecordingContext.TiedToEpisode) {
                "Expected TiedToEpisode context"
            }
        }
    }.report()

    // ── 4. Recording window: before + after ────────────────────────────────

    ctx.scenario("Recording window calcula tiempos correctos") {
        val triggerTime = START.plusSeconds(300)
        transitionDetected(
            from = PersonState.Lying, to = PersonState.Standing,
            at = triggerTime,
        )

        thenExpectRecordingStarted { cmd ->
            val expectedStart = triggerTime.minusSeconds(120) // 2 minutes before
            assert(cmd.start == expectedStart) {
                "Expected start $expectedStart, got ${cmd.start}"
            }
        }
    }.report()

    // ── 5. Duplicate trigger → no double recording ─────────────────────────

    ctx.scenario("Trigger duplicado no crea dos recordings") {
        val at = START.plusSeconds(300)
        transitionDetected(from = PersonState.Lying, to = PersonState.Standing, at = at)
        transitionDetected(from = PersonState.Lying, to = PersonState.Standing, at = at.plusSeconds(10))

        thenExpectRecordingStartedCount(1)
    }.report()

    // ── 6. Warning episode → evidence started ──────────────────────────────

    ctx.scenario("Warning episode crea evidence") {
        episodeOpened(
            episodeId = "ep-warn-1",
            severity = Severity.WARNING,
            at = START.plusSeconds(600),
        )

        thenExpectEvidenceStarted { ev ->
            assert(ev.trigger.isNotEmpty()) {
                "Expected non-empty trigger"
            }
        }
    }.report()

    // ── 7. Scene fact standalone context ────────────────────────────────────

    ctx.scenario("Scene fact usa standalone context") {
        transitionDetected(
            from = PersonState.Lying, to = PersonState.Standing,
            at = START.plusSeconds(600),
        )

        thenExpectRecordingStarted { cmd ->
            assert(cmd.context is RecordingContext.Standalone) {
                "Expected standalone context for scene fact"
            }
        }
    }.report()

    // ── 8. No matching rule → no recording ─────────────────────────────────

    ctx.scenario("Sin regla matching no graba") {
        transitionDetected(
            from = PersonState.InRoom, to = PersonState.InBathroom,
            at = START.plusSeconds(600),
        )

        thenExpectNoCommands()
        thenExpectNoEvidence()
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE")
    println("═══════════════════════════════════════════════════════════════")
}
