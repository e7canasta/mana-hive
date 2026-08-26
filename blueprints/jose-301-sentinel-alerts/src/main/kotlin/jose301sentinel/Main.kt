package jose301sentinel

import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.StateKind
import com.manahive.sentinel.bdd.scenario
import java.time.Instant

fun main() {
    val Unknown = PersonState.Unknown(com.manahive.contracts.scene.UnknownCause.SCENE)
    val Lying = PersonState.Lying
    val SittingInBed = PersonState.SittingInBed
    val Standing = PersonState.Standing

    val ctx = sentinelCtx(calBasica)

    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Sentinel Alerts")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. Sitting opens a WARNING episode ─────────────────────────────────

    ctx.scenario("Sentarse abre episode WARNING") {
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(4500))
        fact(StateKind.SITTING_IN_BED, SittingInBed, Lying, START.plusSeconds(5400))

        thenExpectEpisodeOpened { signal ->
            assert(signal.severity == com.manahive.contracts.policy.Severity.WARNING) {
                "Expected WARNING but got ${signal.severity}"
            }
            assert(signal.trigger == StateKind.SITTING_IN_BED)
            assert(signal.reversible)
        }
    }.report()

    // ── 2. Returning to LYING closes the episode (SAFE_ONLY) ───────────────

    ctx.scenario("Volver a LYING cierra el episode") {
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(4500))
        fact(StateKind.SITTING_IN_BED, SittingInBed, Lying, START.plusSeconds(5400))

        thenExpectEpisodeClosed { signal ->
            assert(signal.cause == ClosureCause.AUTO_RECOVERY)
        }
    }.report()

    // ── 3. Standing opens + sitting is umbrella event ──────────────────────

    ctx.scenario("Standing abre episode, SittingInBed es umbrella") {
        fact(StateKind.STANDING, SittingInBed, Standing, START.plusSeconds(10000))
        fact(StateKind.STANDING, Standing, SittingInBed, START.plusSeconds(10100))

        thenExpectEpisodeOpened { signal ->
            assert(signal.trigger == StateKind.STANDING)
        }
        thenExpectUmbrellaEvent { signal ->
            assert(signal.state == StateKind.SITTING_IN_BED)
        }
    }.report()

    // ── 4. Multiple episodes: Sentinel ALWAYS opens (no fatigue) ──────────

    ctx.scenario("Sentinel siempre abre episodios (sin fatiga)") {
        // First sitting → open
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(4500))
        fact(StateKind.SITTING_IN_BED, SittingInBed, Lying, START.plusSeconds(5400))
        // Second sitting → open again (no suppression)
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(20000))
        fact(StateKind.SITTING_IN_BED, SittingInBed, Lying, START.plusSeconds(20900))
        // Third sitting → open again
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(40000))

        thenExpectEpisodeOpenedCount(3)
        thenExpectEpisodeClosedCount(2)
    }.report()

    // ── 5. Staff presence marks episode but doesn't close SAFE_ONLY ───────

    ctx.scenario("Staff presente marca episode, cierra con safe state") {
        fact(StateKind.SITTING_IN_BED, Lying, SittingInBed, START.plusSeconds(4500))
        factStaffPresent("nurse-1", START.plusSeconds(4600))
        fact(StateKind.SITTING_IN_BED, SittingInBed, Lying, START.plusSeconds(5400))

        thenExpectEpisodeOpened { signal ->
            assert(signal.trigger == StateKind.SITTING_IN_BED)
        }
        thenExpectEpisodeClosed { signal ->
            assert(signal.cause == ClosureCause.STAFF_AND_SAFE)
        }
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE")
    println("═══════════════════════════════════════════════════════════════")
}
