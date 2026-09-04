package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.kernel.BedId
import com.manahive.kernel.ManualClock
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.runtime.CompositePublisher
import com.manahive.runtime.Census
import com.manahive.runtime.FileEventWriter
import com.manahive.runtime.NightWatchRuntime
import com.manahive.runtime.NightWatchServiceCore
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * José E1 — NightWatchServiceCore + FileEventWriter + ManualClock → .out
 *
 * One shared ManualClock: runtime and core see the same time.
 * When clock advances, sweep detects dwell/comeback correctly.
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

    // ONE shared clock — runtime and core share the same reference
    val clock = ManualClock(START)
    val runtime = NightWatchRuntime(clock)

    val writer = FileEventWriter(File("output-jose-e1"), START)
    val publisher = CompositePublisher(writer)
    val core = NightWatchServiceCore(runtime, census, publisher, clock)

    // Load profile
    val cpStream = object {}::class.java.getResourceAsStream("/profiles/jose.json")
        ?: error("No se encontró profiles/jose.json en el classpath")
    val profile = mapper.readValue<ResidentProfileDto>(cpStream)
    cpStream.close()
    core.onProfileChanged(profile)

    println("═══════════════════════════════════════════════════════════════")
    println("  José E1 — NightWatchServiceCore + ManualClock → .out")
    println("═══════════════════════════════════════════════════════════════")
    println()

    data class Event(val offset: Duration, val kind: ObservationKind, val confidence: Double)

    val events = listOf(
        Event(Duration.ZERO,           ObservationKind.IN_BED,         0.95),
        Event(Duration.ofMinutes(75),  ObservationKind.SITTING_IN_BED, 0.92),
        Event(Duration.ofMinutes(92),  ObservationKind.IN_BED,         0.94),
        Event(Duration.ofMinutes(350), ObservationKind.SITTING_IN_BED, 0.90),
        Event(Duration.ofMinutes(354), ObservationKind.IN_BED,         0.93),
    )

    for (event in events) {
        val eventTime = START + event.offset
        clock.setTo(eventTime)

        val obs = Observation(
            monitor = monitor,
            bed = bed,
            kind = event.kind,
            confidence = event.confidence,
            observedAt = eventTime,
        )
        core.onObservation(obs)
    }

    writer.flush()

    println("═══════════════════════════════════════════════════════════════")
    println("  ✅ DONE — .out files written")
    println("═══════════════════════════════════════════════════════════════")
}
