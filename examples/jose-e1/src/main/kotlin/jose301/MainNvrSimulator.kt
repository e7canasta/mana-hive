package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import com.manahive.kernel.BedId
import java.time.Instant
import java.util.UUID

/**
 * NVR Simulator — escucha comandos de grabación y emite ClipCreated.
 *
 * Mismo patrón que MainNatsEventRecorder: connect, subscribe, publish.
 *
 * Flujo:
 *   1. Recibe RecordingStarted → log
 *   2. Recibe RecordingStopped → log + emit ClipCreated
 *   3. Publica ClipCreated a recorder.command.v1.<bed>
 *
 * Run:
 *   Terminal 1: ./gradlew :engines:night-watch-runtime:bootRun
 *   Terminal 2: ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNvrSimulatorKt
 *   Terminal 3: ./gradlew :examples:jose-e1:run -Pmain=jose301.MainColdBootProfileKt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val conn = NatsConfig.createConnection()
    val js = conn.jetStream()

    println("═══════════════════════════════════════════════════════════════")
    println("  NVR Simulator")
    println("  Subscribe: recorder.command.v1.>")
    println("  Publish:   recorder.command.v1.<bed> (ClipCreated)")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val dispatcher = conn.createDispatcher { msg ->
        try {
            val subject = msg.subject
            val envelope = mapper.readValue<EventEnvelope>(String(msg.data))
            if (envelope.source == "nvr-simulator") return@createDispatcher
            val payload = mapper.readTree(envelope.payloadJson)
            val type = payload.get("type")?.asText() ?: "unknown"
            val isStarted = payload.has("config") && !payload.has("end")
            val isStopped = payload.has("end")
            val cmdType = if (isStarted) "RecordingStarted" else if (isStopped) "RecordingStopped" else "unknown"
            println("  [RAW] subject=$subject cmd=$cmdType payload=${envelope.payloadJson.take(200)}")

            when {
                isStarted -> {
                    val bed = payload.get("target")?.get("bed")?.asText() ?: "?"
                    val monitor = payload.get("target")?.get("monitor")?.asText() ?: "?"
                    println("  ▶ STARTED  bed=$bed monitor=$monitor")
                }

                isStopped -> {
                    val bed = payload.get("target")?.get("bed")?.asText() ?: "?"
                    val monitor = payload.get("target")?.get("monitor")?.asText() ?: "?"
                    val end = payload.get("end")?.asText() ?: Instant.now().toString()
                    val episodeNode = payload.get("context")?.get("episode")
                    val episode = if (episodeNode != null && !episodeNode.isNull) episodeNode.asText() else null

                    println("  ◼ STOPPED  bed=$bed monitor=$monitor episode=$episode")

                    val start = payload.get("config")?.get("start")?.asText() ?: end
                    val clipPayload = mapOf(
                        "type" to "ClipCreated",
                        "target" to mapOf("bed" to bed, "monitor" to monitor),
                        "episode" to episode,
                        "start" to start,
                        "end" to end,
                        "path" to "/clips/$bed-${episode ?: "standalone"}-${System.currentTimeMillis()}.mp4",
                        "size" to mapOf("bytes" to 15_000_000L),
                        "at" to Instant.now().toString(),
                    )

                    val clipEnvelope = EventEnvelope(
                        eventId = UUID.randomUUID().toString(),
                        type = "ClipCreated",
                        version = 1,
                        occurredAt = Instant.now(),
                        source = "nvr-simulator",
                        payloadJson = mapper.writeValueAsString(clipPayload),
                    )

                    js.publish(
                        Subjects.recordingCommand(BedId(bed)),
                        mapper.writeValueAsBytes(clipEnvelope),
                    )
                    val clipPath = "/clips/$bed-${episode ?: "standalone"}-${System.currentTimeMillis()}.mp4"
                    println("  ✓ CLIP     bed=$bed episode=$episode path=$clipPath")
                }
            }
        } catch (e: Exception) {
            println("  ✗ Error: ${e.message}")
        }
    }
    dispatcher.subscribe("recorder.command.v1.>")

    println("  Listening...")
    Thread.sleep(2000)
    println()

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("  NVR Simulator stopped.")
        conn.closeDispatcher(dispatcher)
        conn.close()
    })

    Thread.currentThread().join()
}
