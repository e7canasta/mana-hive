package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.kernel.BedId
import com.manahive.kernel.ManualClock
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.Census
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.ProfileCalibrator
import com.manahive.batchio.SceneEventWriter
import com.manahive.batchio.SentinelSignalWriter
import com.manahive.batchio.HarborCommandWriter
import com.manahive.recorder.batch.RecordingEventWriter
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * José E1 — NightWatchRuntime + ManualClock + escritura de .out
 *
 * Mismos eventos que events.dat, mismo START que Shared.kt.
 * Produce los mismos .out que MainPipeline para comparar.
 */
fun main() {
    val mapper = jacksonObjectMapper()
    val START = Instant.parse("2024-01-15T22:00:00Z")

    val bed = BedId("bed-4")
    val resident = ResidentId("jose")
    val night = NightId("night-jose-301")
    val monitor = MonitorId("m1")

    val census = Census()
    census.register(bed, resident, night, monitor)

    val clock = ManualClock(START)
    val runtime = NightWatchRuntime(clock)

    val calibrator = ProfileCalibrator(runtime, census)
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
        ?: error("No se encontró profiles/jose.json en el classpath")
    calibrator.accept(mapper.readValue<ResidentProfileDto>(cpStream).also { cpStream.close() })

    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 — Runtime + ManualClock → .out")
    println("═══════════════════════════════════════════════════════════════")
    println()

    // ── Eventos del events.dat ─────────────────────────────────────
    data class Event(val offset: Duration, val kind: ObservationKind, val confidence: Double)

    val events = listOf(
        Event(Duration.ZERO,               ObservationKind.IN_BED,         0.95),
        Event(Duration.ofMinutes(75),      ObservationKind.SITTING_IN_BED, 0.92),
        Event(Duration.ofMinutes(92),      ObservationKind.IN_BED,         0.94),
        Event(Duration.ofMinutes(350),     ObservationKind.SITTING_IN_BED, 0.90),
        Event(Duration.ofMinutes(354),     ObservationKind.IN_BED,         0.93),
    )

    val sceneEvents = mutableListOf<SceneEvent>()
    val sentinelSignals = mutableListOf<SentinelSignal>()
    val harborCommands = mutableListOf<com.manahive.harbor.NoticeCommand>()
    val harborSignals = mutableListOf<SentinelSignal>()
    val recorderCommands = mutableListOf<com.manahive.recorder.RecordingCommand>()

    for (event in events) {
        val eventTime = START + event.offset
        clock.setTo(eventTime)

        // Observe: sweep runs automatically BEFORE inside onObservation
        val obs = Observation(
            monitor = monitor,
            bed = bed,
            kind = event.kind,
            confidence = event.confidence,
            observedAt = eventTime,
        )

        val out = runtime.onObservation(resident, obs)
        sceneEvents.addAll(out.sceneFacts)
        sentinelSignals.addAll(out.signals)
        out.harborCommands.forEach { harborCommands.add(it.command); harborSignals.add(it.signal) }
        recorderCommands.addAll(out.recorderCommands)
    }

    // ── Escribir .out ─────────────────────────────────────────────
    val outputDir = File("output-jose-e1")
    outputDir.mkdirs()

    SceneEventWriter.write(File(outputDir, "scene.out"), sceneEvents, START)
    SentinelSignalWriter.write(File(outputDir, "sentinel.out"), sentinelSignals, START)
    HarborCommandWriter.write(File(outputDir, "harbor.out"), harborCommands)
    RecordingEventWriter.write(File(outputDir, "recorder.out"), recorderCommands, START)

    println("  Output files:")
    println("    scene.out:    ${File(outputDir, "scene.out").absolutePath}")
    println("    sentinel.out: ${File(outputDir, "sentinel.out").absolutePath}")
    println("    harbor.out:   ${File(outputDir, "harbor.out").absolutePath}")
    println("    recorder.out: ${File(outputDir, "recorder.out").absolutePath}")
    println()

    // ── Resumen ───────────────────────────────────────────────────
    println("  Scene:     ${sceneEvents.size} events")
    println("  Sentinel:  ${sentinelSignals.size} signals")
    println("  Harbor:    ${harborCommands.size} commands")
    println("  Recorder:  ${recorderCommands.size} commands")
    println()
    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE")
    println("═══════════════════════════════════════════════════════════════")
}
