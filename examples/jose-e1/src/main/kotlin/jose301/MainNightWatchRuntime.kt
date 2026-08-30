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
 * Main 4: José E1 contra NightWatchRuntime (pool) — pool completo.
 *
 * Mismo escenario que MainResidentRuntime.
 * NightWatchRuntime es solo un ConcurrentHashMap sobre ResidentRuntime.
 * Los resultados deben ser idénticos.
 *
 * Flujo:
 *   Census: bed-4 → jose
 *   ProfileCalibrator: JSON → calibraciones → runtime.register
 *   Observation arrives by bed → census.lookup → runtime.onObservation(resident, obs)
 */
fun main() {
    val START = Instant.parse("2024-01-15T23:00:00Z")
    val mapper = jacksonObjectMapper()

    val bed = BedId("bed-4")
    val resident = ResidentId("jose")
    val night = NightId("1")
    val monitor = MonitorId("cam-1")

    // ── Census: cama → residente ──
    val census = Census()
    census.register(bed, resident, night, monitor)

    // ── Pool de runtimes ──
    val runtime = NightWatchRuntime()

    // ── Arranque en frío: JSON → calibraciones → register ──
    val calibrator = ProfileCalibrator(runtime, census)
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
        ?: error("No se encontró profiles/jose.json en el classpath")
    val dto = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    calibrator.accept(dto)

    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 — NightWatchRuntime (pool completo)")
    println("  JSON → ProfileCalibrator → Census → Pool → 4 motores")
    println("═══════════════════════════════════════════════════════════════")
    println()

    var checks = 0
    var failures = 0

    fun check(label: String, condition: Boolean) {
        checks++
        if (condition) {
            println("  ✅ $label")
        } else {
            failures++
            println("  ❌ $label")
        }
    }

    fun obs(at: Instant, kind: ObservationKind) = Observation(
        sourceEventId = "jose-${at.epochSecond}",
        monitor = monitor,
        bed = bed,
        kind = kind,
        confidence = 0.95,
        observedAt = at,
    )

    // ── Verificar pool ──
    check("runtime.size=1", runtime.size == 1)
    val rt = runtime.get(resident)!!
    check("rt.residentId=jose", rt.residentId == resident)
    check("rt.bed=bed-4", rt.bed == bed)

    println()

    // ── Observación 1: Jose se sienta (23:15:00, t=0s) ──
    val out1 = runtime.onObservation(resident, obs(START, ObservationKind.SITTING_IN_BED))

    check("obs1: sceneFacts=1 (TransitionDetected)", out1.sceneFacts.size == 1)
    val t1 = out1.sceneFacts.filterIsInstance<TransitionDetected>().firstOrNull()
    check("obs1: from=Lying", t1?.from == Lying)
    check("obs1: to=SittingInBed", t1?.to == SittingInBed)

    check("obs1: signals=1 (EpisodeOpened)", out1.signals.size == 1)
    check("obs1: signal is EpisodeOpened", out1.signals.first() is EpisodeOpened)
    val opened = out1.signals.first() as EpisodeOpened
    check("obs1: severity=NORMAL", opened.severity.name == "NORMAL")
    check("obs1: rule=comeback-lying", opened.rule.value == "comeback-lying")

    check("obs1: harborCommands=0 (severity=NORMAL)", out1.harborCommands.isEmpty())

    check("obs1: recorderCommands=1 (RecordingStarted)", out1.recorderCommands.size == 1)
    check("obs1: command is RecordingStarted", out1.recorderCommands.first() is RecordingStarted)
    check("obs1: openEpisodeCount=1", rt.openEpisodeCount() == 1)

    println()

    // ── Sweep 1: 12 minutos después (23:27:00) → WARNING ──
    val sweep1 = START.plus(12, ChronoUnit.MINUTES)
    val results = runtime.tickAll(sweep1)
    val outS1 = results[resident]!!

    check("sweep1 (12m): signals=1 (UmbrellaEvent)", outS1.signals.size == 1)
    val umbrella1 = outS1.signals.first() as UmbrellaEvent
    check("sweep1: severity=WARNING", umbrella1.originalSeverity.name == "WARNING")

    check("sweep1: harborCommands=1 (Dispatch)", outS1.harborCommands.size == 1)

    println()

    // ── Sweep 2: 15 minutos después (23:30:00) → EXCEEDED ──
    val sweep2 = START.plus(15, ChronoUnit.MINUTES)
    val results2 = runtime.tickAll(sweep2)
    val outS2 = results2[resident]!!

    check("sweep2 (15m): signals=1 (UmbrellaEvent)", outS2.signals.size == 1)
    val umbrella2 = outS2.signals.first() as UmbrellaEvent
    check("sweep2: severity=EXCEEDED", umbrella2.originalSeverity.name == "EXCEEDED")

    check("sweep2: harborCommands=1 (Dispatch URGENT)", outS2.harborCommands.size == 1)

    println()

    // ── Observación 2: Jose vuelve a la cama (23:32:00, t=17m) ──
    val out2 = runtime.onObservation(resident, obs(START.plus(17, ChronoUnit.MINUTES), ObservationKind.IN_BED))

    check("obs2: sceneFacts=1 (TransitionDetected)", out2.sceneFacts.size == 1)
    val t2 = out2.sceneFacts.filterIsInstance<TransitionDetected>().firstOrNull()
    check("obs2: from=SittingInBed", t2?.from == SittingInBed)
    check("obs2: to=Lying", t2?.to == Lying)

    check("obs2: signals=1 (EpisodeClosed)", out2.signals.size == 1)
    check("obs2: signal is EpisodeClosed", out2.signals.first() is EpisodeClosed)
    val closed = out2.signals.first() as EpisodeClosed
    check("obs2: cause=AUTO_RECOVERY", closed.cause.name == "AUTO_RECOVERY")

    check("obs2: harborCommands=1 (Resolve)", out2.harborCommands.size == 1)

    check("obs2: recorderCommands=1 (RecordingStopped)", out2.recorderCommands.size == 1)
    check("obs2: command is RecordingStopped", out2.recorderCommands.first() is RecordingStopped)
    check("obs2: openEpisodeCount=0 (closed)", rt.openEpisodeCount() == 0)

    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  Resultado: $checks checks, $failures failures")
    println("═══════════════════════════════════════════════════════════════")
}
