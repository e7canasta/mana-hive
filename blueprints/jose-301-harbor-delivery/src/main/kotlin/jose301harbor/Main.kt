package jose301harbor

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.sentinel.ClosureCause
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.scene.StateKind
import com.manahive.harbor.*
import com.manahive.harbor.bdd.HarborContext
import com.manahive.harbor.bdd.scenario
import com.manahive.harbor.testdata.episodeClosed
import com.manahive.harbor.testdata.episodeOpened
import com.manahive.harbor.testdata.testCalibration
import com.manahive.kernel.*
import java.time.Instant

val BED_4 = BedId("bed-4")
val NIGHT = NightId("night-jose-301")
val JOSE = ResidentId("jose")
val START = Instant.parse("2024-01-15T22:00:00Z")

fun harborCtx(cal: HarborCalibration) = HarborContext(
    bed = BED_4,
    resident = JOSE,
    calibration = cal,
)

fun main() {
    val cal = testCalibration("jose", warningMax = 3, infoMax = 2)
    val ctx = harborCtx(cal)

    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — Harbor Delivery (Fatigue Tests)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── 1. WARNING episode → dispatch ───────────────────────────────────────

    ctx.scenario("WARNING episode se dispatcha") {
        signal(episodeOpened(BED_4, JOSE, NIGHT, START, "ep-1", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START)

        thenExpectDispatch { cmd ->
            assert(cmd.channels == setOf(Channel.PUSH, Channel.TABLET))
        }
        thenExpectNoticeCreated(EpisodeId("ep-1"))
        thenExpectFatigueNotExceeded(Severity.WARNING)
    }.report()

    // ── 2. CRITICAL episode → siempre dispatch (sin fatiga) ────────────────

    ctx.scenario("CRITICAL episode siempre se dispatcha") {
        signal(episodeOpened(BED_4, JOSE, NIGHT, START, "ep-2", "r-fall",
            StateKind.BED_EDGE, Severity.CRITICAL, reversible = false), START)

        thenExpectDispatch()
        thenExpectFatigueNotExceeded(Severity.CRITICAL)
    }.report()

    // ── 3. Fatigue: 3 WARNING episodes → 4to suprimido ────────────────────

    ctx.scenario("Fatigue suprime 4to WARNING") {
        // 3 WARNING episodes (max = 3)
        signal(episodeOpened(BED_4, JOSE, NIGHT, START, "ep-3", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START)
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(300), "ep-3"), START.plusSeconds(300))
        signal(episodeOpened(BED_4, JOSE, NIGHT, START.plusSeconds(600), "ep-4", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START.plusSeconds(600))
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(900), "ep-4"), START.plusSeconds(900))
        signal(episodeOpened(BED_4, JOSE, NIGHT, START.plusSeconds(1200), "ep-5", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START.plusSeconds(1200))
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(1500), "ep-5"), START.plusSeconds(1500))

        // 4th WARNING → fatigue exceeded, notice created but not dispatched
        signal(episodeOpened(BED_4, JOSE, NIGHT, START.plusSeconds(1800), "ep-6", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START.plusSeconds(1800))

        thenExpectFatigueExceeded(Severity.WARNING)
        thenExpectNoticeCreated(EpisodeId("ep-6"))
    }.report()

    // ── 4. INFO fatigue: 2 max ─────────────────────────────────────────────

    ctx.scenario("Fatigue suprime 3er INFO") {
        signal(episodeOpened(BED_4, JOSE, NIGHT, START, "ep-7", "r-info",
            StateKind.STANDING, Severity.INFO), START)
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(100), "ep-7"), START.plusSeconds(100))
        signal(episodeOpened(BED_4, JOSE, NIGHT, START.plusSeconds(200), "ep-8", "r-info",
            StateKind.STANDING, Severity.INFO), START.plusSeconds(200))
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(300), "ep-8"), START.plusSeconds(300))

        // 3rd INFO → fatigue exceeded
        signal(episodeOpened(BED_4, JOSE, NIGHT, START.plusSeconds(400), "ep-9", "r-info",
            StateKind.STANDING, Severity.INFO), START.plusSeconds(400))

        thenExpectFatigueExceeded(Severity.INFO)
    }.report()

    // ── 5. EpisodeClosed → Resolve ─────────────────────────────────────────

    ctx.scenario("EpisodeClosed genera Resolve command") {
        signal(episodeOpened(BED_4, JOSE, NIGHT, START, "ep-10", "r-sit",
            StateKind.SITTING_IN_BED, Severity.WARNING), START)
        signal(episodeClosed(BED_4, JOSE, NIGHT, START.plusSeconds(300), "ep-10"), START.plusSeconds(300))

        thenExpectResolve { cmd ->
            assert(cmd.resolution == Resolution.AUTO_RECOVERY)
        }
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE")
    println("═══════════════════════════════════════════════════════════════")
}
