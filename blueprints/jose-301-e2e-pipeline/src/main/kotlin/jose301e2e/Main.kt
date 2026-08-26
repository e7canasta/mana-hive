package jose301e2e

import com.manahive.contracts.perception.ObservationKind.*
import com.manahive.contracts.policy.*
import com.manahive.contracts.scene.PersonState
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.*
import com.manahive.kernel.*
import com.manahive.pipeline.bdd.PipelineContext
import com.manahive.pipeline.bdd.pipeline
import com.manahive.politica.PolicyResolver
import com.manahive.politica.adapters.toHarborCalibration
import com.manahive.politica.adapters.toSceneCalibration
import com.manahive.politica.adapters.toSentinelCalibration
import com.manahive.recorder.*
import java.time.Duration
import java.time.Instant

val BED = BedId("bed-4")
val JOSE = ResidentId("jose")
val NIGHT = NightId("night-jose-301")
val CAM = MonitorId("CAMERA_MAIN")
val START = Instant.parse("2024-01-15T22:00:00Z")

// ── José's Profile — Director's Language ─────────────────────────────────────

val joseProfile = buildResidentProfile("jose") {
    risk(RiskLevel.HIGH)
    mobility(MobilityAid.NONE)
    template("standard")

    resident {
        sitting { alertAfter(Duration.ofMinutes(15)) }
        bathroom { alertAfter(Duration.ofMinutes(10)) }
    }

    transitions {
        lyingToStanding { hysteresis(Duration.ofMillis(1000)) }
    }
}

// ── Politica Engine resolves ────────────────────────────────────────────────

val policyCalibration = PolicyResolver.resolve(STANDARD_CATALOG, joseProfile.profile)

// ── Derived Calibrations ────────────────────────────────────────────────────

val sceneCal = policyCalibration.toSceneCalibration()
val sentinelCal = policyCalibration.toSentinelCalibration()
val harborCal = policyCalibration.toHarborCalibration()

// ── Recorder: custom rules from blueprint ───────────────────────────────────

val recorderCal = recordingCalibration {
    resident("jose")
    rule("r-fall-recording") {
        trigger { transition(from = PersonState.Lying, to = PersonState.Standing) }
        recordingWindow { before = Duration.ofMinutes(2); after = Duration.ofMinutes(5) }
        quality = Quality.HD
        monitors = listOf(CAM)
    }
    rule("r-dwell-recording") {
        trigger { dwellExceeded(state = PersonState.InBathroom) }
        recordingWindow { before = Duration.ofMinutes(3); after = Duration.ofMinutes(10) }
        quality = Quality.HD
        monitors = listOf(CAM)
    }
    rule("r-incident-recording") {
        trigger { episodeOpened(severity = Severity.CRITICAL) }
        recordingWindow { before = Duration.ofMinutes(10); after = Duration.ofMinutes(15) }
        quality = Quality.FULL
        monitors = listOf(CAM, MonitorId("CAMERA_CORRIDOR"))
    }
    evidenceRule("e-warning-evidence") {
        trigger { episodeOpened(severity = Severity.WARNING) }
        evidenceType = EvidenceType.INCIDENT
    }
}

// ── Pipeline Context ────────────────────────────────────────────────────────

val ctx = PipelineContext(
    bed = BED,
    resident = JOSE,
    night = NIGHT,
    monitor = CAM,
    sceneCalibration = sceneCal,
    sentinelCalibration = sentinelCal,
    harborCalibration = harborCal,
    recorderCalibration = recorderCal,
    start = START,
)

// ── Scenarios ───────────────────────────────────────────────────────────────

/** Compute the staff arrival offset from a sitting start: start + exceeded + 5 min buffer. */
fun staffArrivalAfter(sittingStart: String, exceeded: java.time.Duration): String {
    val startSeconds = parseOffset(sittingStart)
    val totalSeconds = startSeconds + exceeded.seconds + 300 // +5 min buffer
    val m = totalSeconds / 60
    return "${m}m"
}

/** Parse "30m" / "1h15m" to seconds. */
fun parseOffset(offset: String): Long {
    var total = 0L
    Regex("(\\d+)(h|m|s)").findAll(offset).forEach { match ->
        val v = match.groupValues[1].toLong()
        when (match.groupValues[2]) {
            "h" -> total += v * 3600
            "m" -> total += v * 60
            "s" -> total += v
        }
    }
    return total
}

fun main() {
    println("═══════════════════════════════════════════════════════════════")
    println("  José 301 — E2E Pipeline (Scene → Sentinel → Harbor → Recorder)")
    println("═══════════════════════════════════════════════════════════════")
    println()
    println("  Perfil: ${joseProfile.profile.riskLevel} / ${joseProfile.profile.mode}")
    println("  Template: ${joseProfile.profile.templateId?.value ?: "none"}")
    println()
    println("  Politica resuelta:")
    println("    Scene:     ${policyCalibration.scene.dwellThresholds.size} dwell, ${policyCalibration.scene.hysteresis.size} hysteresis")
    println("    Sentinel:  ${policyCalibration.sentinel.alertRules.size} alert rules")
    println("    Recorder:  ${policyCalibration.recorder.transitionWindows.size} transition windows")
    println()
    println("  Reglas de residente:")
    joseProfile.stateOverrides.forEach { (state, override) ->
        println("    ${state.name}: alertAfter=${override.alertAfter}")
    }
    println()

    val sittingExceeded = policyCalibration.scene.dwellThresholds[StateKind.SITTING_IN_BED]?.exceeded
        ?: java.time.Duration.ofMinutes(15)
    val staffOffset = staffArrivalAfter("30m", sittingExceeded)

    // ── 1. José se sienta 25 min → DwellExceeded → episodio ──────────────
    // Sitting: warning@7m30, exceeded@15m. Se sienta 25 min → episodio se abre y cierra.

    ctx.pipeline("José se sienta en la cama — dwell exceeded abre episodio") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h40m")

        thenSceneEventPresent(SceneEvent.TransitionDetected::class)
        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenSignalPresent(SentinelSignal.EpisodeOpened::class)
        thenSignalPresent(SentinelSignal.EpisodeClosed::class)
        thenEpisodeOpenCount(1)
        thenHarborCommandPresent(NoticeCommand.Dispatch::class)
    }.report()

    // ── 2. José va al baño 10 min → DwellExceeded bathroom ───────────────
    // Bathroom: warning@5m, exceeded@10m. En baño 10 min → episodio.

    ctx.pipeline("José va al baño y tarda — dwell exceeded bathroom") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "2h47m")
        obs(STANDING, "2h48m")
        obs(IN_BATHROOM, "2h50m")
        obs(IN_ROOM, "3h00m")
        obs(IN_BED, "3h02m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenSignalPresent(SentinelSignal.EpisodeOpened::class)
        thenHarborCommandPresent(NoticeCommand.Dispatch::class)
    }.report()

    // ── 3. José se sienta 3 veces → 2 episodios (el 3ro no alcanza umbral) ─
    // Ciclos: 17min (→episodio), 15min (→episodio), 10min (sin episodio).

    ctx.pipeline("José se sienta 3 veces — 2 episodios") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "1h15m")
        obs(IN_BED, "1h32m")
        obs(SITTING_IN_BED, "3h00m")
        obs(IN_BED, "3h15m")
        obs(SITTING_IN_BED, "5h00m")
        obs(IN_BED, "5h10m")

        thenEpisodeOpenCount(2)
        thenHarborCommandCount(4)
    }.report()

    // ── 4. José camina al baño → dwell bathroom 25 min ───────────────────

    ctx.pipeline("José camina al baño — dwell bathroom 25 min") {
        obs(IN_BED, "0s")
        obs(STANDING, "1h00m")
        obs(IN_BATHROOM, "1h05m")
        obs(IN_ROOM, "1h30m")
        obs(IN_BED, "1h35m")

        thenSceneEventPresent(SceneEvent.DwellExceeded::class)
        thenSentinelAbrioYCerroEpisodio()
        thenRecorderCommandPresent(RecordingStarted::class)
    }.report()

    // ── 5. LYING→STANDING sin dwell → grabación por transición ───────────

    ctx.pipeline("LYING→STANDING activa grabación") {
        obs(IN_BED, "0s")
        obs(STANDING, "180s")
        obs(IN_BED, "600s")

        thenRecorderCommandPresent(RecordingStarted::class)
    }.report()

    // ── 6. Staff asiste durante incidente (dwell exceeded antes del staff) ─

    ctx.pipeline("Staff asiste a José durante incidente") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, staffOffset)

        thenSentinelAbrioYCerroEpisodio()
        thenHarborNotificoYResolvio()
    }.report()

    // ── 7. Staff asiste y deja residente solo ────────────────────────────

    ctx.pipeline("Staff asiste y deja residente solo") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, staffOffset)
        val postStaff = parseOffset(staffOffset) + 300 // 5 min after staff arrives
        obs(STAFF_LEFT, "${postStaff / 60}m")
        obs(IN_BED, "${(postStaff / 60) + 5}m")

        thenSentinelAbrioYCerroEpisodio()
        thenHarborNotificoYResolvio()
    }.report()

    // ── 8. Staff se lleva al residente ───────────────────────────────────

    ctx.pipeline("Staff se lleva al residente") {
        obs(IN_BED, "0s")
        obs(SITTING_IN_BED, "30m")
        obs(STAFF_ENTERED, staffOffset)
        val postStaff = parseOffset(staffOffset) + 300
        obs(STANDING, "${postStaff / 60}m")
        obs(STAFF_LEFT, "${(postStaff / 60) + 5}m")

        thenSentinelAbrioYCerroEpisodio()
        thenHarborNotificoYResolvio()
    }.report()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ PIPELINE E2E COMPLETA")
    println("═══════════════════════════════════════════════════════════════")
}
