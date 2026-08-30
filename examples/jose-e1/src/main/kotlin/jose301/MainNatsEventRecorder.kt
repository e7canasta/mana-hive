package jose301

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Dispatcher
import java.io.File
import java.time.Instant

/**
 * Records all events published by the service to files.
 *
 * Subscribes to NATS subjects and writes raw events to:
 * - events-raw.jsonl (all events in order)
 * - scene-events.jsonl
 * - sentinel-signals.jsonl
 * - alarm-events.jsonl
 * - recording-commands.jsonl
 *
 * Usage:
 *   ./gradlew :examples:jose-e1:run -Pmain=jose301.MainNatsEventRecorderKt
 */
fun main() {
    val mapper = NatsObjectMapper.mapper
    val outputDir = File("output-jose-e1/nats-recording")
    outputDir.mkdirs()

    val sceneFile = File(outputDir, "scene-events.jsonl").bufferedWriter()
    val sentinelFile = File(outputDir, "sentinel-signals.jsonl").bufferedWriter()
    val alarmFile = File(outputDir, "alarm-events.jsonl").bufferedWriter()
    val recorderFile = File(outputDir, "recording-commands.jsonl").bufferedWriter()
    val rawFile = File(outputDir, "events-raw.jsonl").bufferedWriter()

    println("═══════════════════════════════════════════════════════════════")
    println("  NATS Event Recorder")
    println("  Subscribing to all event subjects...")
    println("  Output: ${outputDir.absolutePath}")
    println("═══════════════════════════════════════════════════════════════")
    println()

    val conn = NatsConfig.createConnection()
    val dispatchers = mutableListOf<Dispatcher>()

    fun writeEvent(file: java.io.BufferedWriter, subject: String, type: String, payload: String) {
        val line = mapper.writeValueAsString(mapOf(
            "subject" to subject,
            "type" to type,
            "receivedAt" to Instant.now().toString(),
            "payload" to mapper.readTree(payload),
        ))
        file.write(line)
        file.newLine()
        file.flush()
    }

    // Subscribe to all event subjects
    val subjects = mapOf(
        Subjects.SCENE_WILDCARD to ("SceneEvent" to sceneFile),
        Subjects.SENTINEL_WILDCARD to ("SentinelSignal" to sentinelFile),
        Subjects.ALARM_WILDCARD to ("AlarmEvent" to alarmFile),
        Subjects.RECORDER_WILDCARD to ("RecordingCommand" to recorderFile),
    )

    for ((subject, typeAndFile) in subjects) {
        val (type, file) = typeAndFile
        val dispatcher = conn.createDispatcher { msg ->
            try {
                val envelope = mapper.readValue<EventEnvelope>(String(msg.data))
                writeEvent(rawFile, msg.subject, envelope.type, envelope.payloadJson)
                writeEvent(file, msg.subject, envelope.type, envelope.payloadJson)
                println("  ✓ ${envelope.type} on ${msg.subject}")
            } catch (e: Exception) {
                println("  ✗ Error processing ${msg.subject}: ${e.message}")
            }
        }
        dispatcher.subscribe(subject)
        dispatchers.add(dispatcher)
        println("  Subscribed to $subject (${type})")
    }

    println()
    println("  Listening... Run the scenario client in another terminal.")
    println("  Press Ctrl+C to stop.")
    println()

    // Keep running until interrupted
    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("  Flushing files...")
        sceneFile.close()
        sentinelFile.close()
        alarmFile.close()
        recorderFile.close()
        rawFile.close()
        dispatchers.forEach { conn.closeDispatcher(it) }
        conn.close()
        println("  Done. Files in: ${outputDir.absolutePath}")
    })

    Thread.currentThread().join()
}
