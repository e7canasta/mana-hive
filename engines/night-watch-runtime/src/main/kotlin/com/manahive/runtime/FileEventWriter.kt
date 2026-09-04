package com.manahive.runtime

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.kernel.BedId
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import com.manahive.contracts.notice.NoticeEvent
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.EvidenceRecord
import com.manahive.recorder.batch.toJson
import com.manahive.batchio.SceneEventWriter
import com.manahive.batchio.SentinelSignalWriter
import com.manahive.batchio.HarborCommandWriter
import com.manahive.recorder.batch.RecordingEventWriter
import com.manahive.recorder.batch.writeTo
import com.manahive.serialization.SceneEventSerializer
import com.manahive.serialization.SentinelSignalSerializer
import com.manahive.serialization.NoticeCommandSerializer
import java.io.File
import java.time.Instant

/**
 * Writes runtime output to .out files + events.jsonl.
 *
 * Collects domain events, then on [flush]:
 * - Uses the batch writers for .out files (human-readable)
 * - Writes events.jsonl with all events in order (machine-readable)
 */
class FileEventWriter(
    private val outputDir: File,
    private val startTime: Instant,
) : EventPublisher {

    private val mapper = jacksonObjectMapper()
    private val events = mutableListOf<Map<String, Any>>()

    private val sceneEvents = mutableListOf<SceneEvent>()
    private val sentinelSignals = mutableListOf<SentinelSignal>()
    private val harborCommands = mutableListOf<NoticeCommand>()
    private val recorderCommands = mutableListOf<RecordingCommand>()
    private val evidenceRecords = mutableListOf<EvidenceRecord>()

    override fun publishSceneEvent(bed: BedId, event: SceneEvent) {
        sceneEvents += event
        events += mapOf(
            "type" to "SceneEvent",
            "bed" to bed.value,
            "at" to event.at.toString(),
            "offset" to java.time.Duration.between(startTime, event.at).toString(),
            "payload" to mapper.readTree(SceneEventSerializer.toJson(event)),
        )
    }

    override fun publishSentinelSignal(bed: BedId, signal: SentinelSignal) {
        sentinelSignals += signal
        events += mapOf(
            "type" to "SentinelSignal",
            "bed" to bed.value,
            "at" to signal.at.toString(),
            "offset" to java.time.Duration.between(startTime, signal.at).toString(),
            "payload" to mapper.readTree(SentinelSignalSerializer.toJson(signal)),
        )
    }

    override fun publishNoticeCommand(bed: BedId, signal: SentinelSignal, command: NoticeCommand) {
        harborCommands += command
    }

    override fun publishNoticeEvent(bed: BedId, event: NoticeEvent) {
        events += mapOf(
            "type" to "NoticeEvent",
            "bed" to bed.value,
            "at" to event.at.toString(),
            "offset" to java.time.Duration.between(startTime, event.at).toString(),
            "payload" to mapper.valueToTree<JsonNode>(event),
        )
    }

    override fun publishRecordingCommand(bed: BedId, command: RecordingCommand, occurredAt: Instant) {
        recorderCommands += command
        events += mapOf(
            "type" to "RecordingCommand",
            "bed" to bed.value,
            "at" to occurredAt.toString(),
            "offset" to java.time.Duration.between(startTime, occurredAt).toString(),
            "payload" to command.toString(),
        )
    }

    override fun publishEvidenceRecord(bed: BedId, record: EvidenceRecord) {
        evidenceRecords += record
        events += mapOf(
            "type" to "EvidenceRecord",
            "bed" to bed.value,
            "at" to record.at.toString(),
            "offset" to java.time.Duration.between(startTime, record.at).toString(),
            "payload" to mapper.valueToTree<JsonNode>(record.toJson()),
        )
    }

    fun flush() {
        outputDir.mkdirs()
        SceneEventWriter.write(File(outputDir, "scene.out"), sceneEvents, startTime)
        SentinelSignalWriter.write(File(outputDir, "sentinel.out"), sentinelSignals, startTime)
        HarborCommandWriter.write(File(outputDir, "harbor.out"), harborCommands)
        RecordingEventWriter.write(File(outputDir, "recorder.out"), recorderCommands, startTime)
        evidenceRecords.writeTo(File(outputDir, "evidence.out"))
        writeJsonl()
    }

    private fun writeJsonl() {
        File(outputDir, "events.jsonl").bufferedWriter().use { writer ->
            for (event in events) {
                writer.write(mapper.writeValueAsString(event))
                writer.newLine()
            }
        }
    }
}
