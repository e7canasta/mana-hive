package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.PersonState.Lying
import com.manahive.contracts.scene.PersonState.SittingInBed
import com.manahive.contracts.scene.SceneEvent.TransitionDetected
import com.manahive.contracts.sentinel.SentinelSignal.EpisodeClosed
import com.manahive.contracts.sentinel.SentinelSignal.EpisodeOpened
import com.manahive.contracts.sentinel.SentinelSignal.UmbrellaEvent
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.recorder.RecordingStarted
import com.manahive.recorder.RecordingStopped
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Main 3: José E1 directo contra NightWatchRuntime.
 *
 * Arranque en frío desde JSON → ProfileCalibrator → calibraciones → runtime.
 * Sin NATS, sin Spring, sin publicación. Solo motores puros.
 *
 * Flujo E1 completo:
 *   obs(IN_BED)     → SignalRecovered (Unknown → Lying)
 *   obs(SITTING)    → TransitionDetected(Lying → SittingInBed) + EpisodeOpened
 *   tick(12m)       → UmbrellaEvent(WARNING) + Dispatch
 *   tick(15m)       → UmbrellaEvent(EXCEEDED) + Dispatch URGENT
 *   obs(IN_BED)     → TransitionDetected(SittingInBed → Lying) + EpisodeClosed
 */
fun main() {
    val START = Instant.parse("2024-01-15T23:00:00Z")
    val mapper = jacksonObjectMapper()

    val bed = BedId("bed-4")
    val resident = ResidentId("jose")
    val night = NightId("1")
    val monitor = MonitorId("cam-1")

    val census = Census()
    census.register(bed, resident, night, monitor)

    val runtime = NightWatchRuntime()
    val calibrator = ProfileCalibrator(runtime, census)
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
        ?: error("No se encontró profiles/jose.json en el classpath")
    calibrator.accept(mapper.readValue<ResidentProfileDto>(cpStream).also { cpStream.close() })

    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 — Arranque en frío → NightWatchRuntime (motores puros)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    var checks = 0
    var failures = 0

    fun check(label: String, condition: Boolean) {
        checks++
        if (condition) println("  ✅ $label") else { failures++; println("  ❌ $label") }
    }

    fun obs(at: Instant, kind: ObservationKind) = Observation(
        sourceEventId = "jose-${at.epochSecond}", monitor = monitor, bed = bed,
        kind = kind, confidence = 0.95, observedAt = at,
    )

    val rt = runtime.get(resident)!!
    check("runtime.size=1", runtime.size == 1)
    check("rt.bed=bed-4", rt.bed == bed)
    println()

    // ── Obs 1: señal recuperada (23:00:00) ──
    println("── Obs 1: IN_BED → SignalRecovered ((Unknown → Lying)) ──")
    val out0 = runtime.onObservation(resident, obs(START.minus(15, ChronoUnit.MINUTES), ObservationKind.IN_BED))
    check("obs0: sceneFacts=1 (SignalRecovered)", out0.sceneFacts.isNotEmpty())
    check("obs0: signals=0 (no episode aún)", out0.signals.isEmpty())
    println()

    // ── Obs 2: Jose se sienta (23:15:00) → transición + episodio ──
    println("── Obs 2a: SITTING_IN_BED → hysteresis confirmation 1 ──")
    val out2a = runtime.onObservation(resident, obs(START, ObservationKind.SITTING_IN_BED))
    out2a.sceneFacts.forEach { println("  🔍 2a fact: ${it::class.simpleName}") }
    out2a.signals.forEach { println("  🔍 2a signal: ${it::class.simpleName}") }

    println("── Obs 2b: SITTING_IN_BED → hysteresis confirmation 2 ──")
    val out2b = runtime.onObservation(resident, obs(START.plus(2, ChronoUnit.MINUTES), ObservationKind.SITTING_IN_BED))
    println("  🔍 2b facts=${out2b.sceneFacts.size}, signals=${out2b.signals.size}, harbor=${out2b.harborCommands.size}, recorder=${out2b.recorderCommands.size}")
    out2b.sceneFacts.forEach { println("  🔍 2b fact: ${it::class.simpleName}") }
    out2b.signals.forEach { println("  🔍 2b signal: ${it::class.simpleName}") }
    check("obs2b: signals=1 (EpisodeOpened)", out2b.signals.size == 1)
    val opened = out2b.signals.first() as EpisodeOpened
    check("obs2b: severity=NORMAL", opened.severity.name == "NORMAL")
    check("obs2b: harborCommands=0 (no dispatch en NORMAL)", out2b.harborCommands.isEmpty())
    check("obs2b: recorderCommands=1 (RecordingStarted)", out2b.recorderCommands.size == 1)
    check("obs2b: command is RecordingStarted", out2b.recorderCommands.first() is RecordingStarted)
    check("obs2b: openEpisodeCount=1", rt.openEpisodeCount() == 1)
    println()

    // ── Sweep: 12 min → WARNING ──
    println("── Sweep: 12m → UmbrellaEvent(WARNING) + Dispatch ──")
    val sweep1 = START.plus(12, ChronoUnit.MINUTES)
    val outS1 = runtime.tickAll(sweep1)[resident]!!
    check("sweep1 (12m): signals=1 (UmbrellaEvent)", outS1.signals.size == 1)
    check("sweep1: severity=WARNING", (outS1.signals.first() as UmbrellaEvent).originalSeverity.name == "WARNING")
    check("sweep1: harborCommands=1", outS1.harborCommands.size == 1)
    println()

    // ── Sweep: 15 min → EXCEEDED ──
    println("── Sweep: 15m → UmbrellaEvent(EXCEEDED) + Dispatch URGENT ──")
    val sweep2 = START.plus(15, ChronoUnit.MINUTES)
    val outS2 = runtime.tickAll(sweep2)[resident]!!
    check("sweep2 (15m): signals=1 (UmbrellaEvent)", outS2.signals.size == 1)
    check("sweep2: severity=EXCEEDED", (outS2.signals.first() as UmbrellaEvent).originalSeverity.name == "EXCEEDED")
    check("sweep2: harborCommands=1 (URGENT)", outS2.harborCommands.size == 1)
    println()

    // ── Obs 3: Jose vuelve a la cama (23:32:00) → cierre ──
    println("── Obs 3: IN_BED → TransitionDetected + EpisodeClosed ──")
    val out2 = runtime.onObservation(resident, obs(START.plus(17, ChronoUnit.MINUTES), ObservationKind.IN_BED))
    check("obs2: sceneFacts=1 (TransitionDetected)", out2.sceneFacts.size == 1)
    val t2 = out2.sceneFacts.filterIsInstance<TransitionDetected>().firstOrNull()
    check("obs2: from=SittingInBed", t2?.from == SittingInBed)
    check("obs2: to=Lying", t2?.to == Lying)
    check("obs2: signals=1 (EpisodeClosed)", out2.signals.size == 1)
    check("obs2: cause=AUTO_RECOVERY", (out2.signals.first() as EpisodeClosed).cause.name == "AUTO_RECOVERY")
    check("obs2: harborCommands=1 (Resolve)", out2.harborCommands.size == 1)
    check("obs2: recorderCommands=1 (RecordingStopped)", out2.recorderCommands.size == 1)
    check("obs2: openEpisodeCount=0", rt.openEpisodeCount() == 0)

    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  Resultado: $checks checks, $failures failures")
    println("═══════════════════════════════════════════════════════════════")
}
