package com.manahive.runtime

import com.fasterxml.jackson.module.kotlin.readValue
import com.manahive.contracts.EventEnvelope
import com.manahive.kernel.BedId
import com.manahive.kernel.EventRef
import com.manahive.kernel.SystemClock
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.alarm.AlertKey
import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.kernel.AlertId
import com.manahive.harbor.NoticeCommand
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsObjectMapper
import com.manahive.contracts.notice.NoticeEvent
import com.manahive.messaging.Subjects
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.EvidenceRecord
import com.manahive.recorder.batch.toJson
import com.manahive.serialization.SceneEventSerializer
import com.manahive.serialization.SentinelSignalSerializer
import org.slf4j.LoggerFactory

/**
 * NATS adapter for [EventPublisher].
 *
 * Converts domain events to EventEnvelope + serialized payload,
 * publishes to JetStream. The core doesn't know about NATS —
 * this adapter is the only class that touches JetStream.
 *
 * AlarmEvent creation lives here because it's a publishing concern:
 * the core produces NoticeCommand + SentinelSignal, and this adapter
 * combines them into AlarmEvent for the bus.
 */
class NatsEventPublisher(
    private val events: BusEvents,
) : EventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper

    override fun publishSceneEvent(bed: BedId, event: SceneEvent) {
        emit(Subjects.sceneEvent(bed), "SceneEvent", event.at, SceneEventSerializer.toJson(event))
    }

    override fun publishSentinelSignal(bed: BedId, signal: SentinelSignal) {
        emit(Subjects.sentinelSignal(bed), "SentinelSignal", signal.at, SentinelSignalSerializer.toJson(signal))
    }

    override fun publishNoticeCommand(bed: BedId, signal: SentinelSignal, command: NoticeCommand) {
        if (command is NoticeCommand.Dispatch && signal is SentinelSignal.EpisodeOpened) {
            val event = AlarmEvent.AlertRaised(
                alert = AlertId("alert-${signal.episode.value}-${signal.at.epochSecond}"),
                at = signal.at,
                key = AlertKey(bed = signal.bed, rule = signal.rule, episode = signal.episode),
                severity = signal.severity,
                origin = EventRef(stream = Subjects.sentinelSignal(signal.bed), seq = 0),
            )
            emit(Subjects.alarmEvent(event.alert), "AlarmEvent", event.at, mapper.writeValueAsString(event))
        }
    }

    override fun publishNoticeEvent(bed: BedId, event: NoticeEvent) {
        emit(Subjects.noticeEvent(event.noticeId), "NoticeEvent", event.at, mapper.writeValueAsString(event))
    }

    override fun publishRecordingCommand(bed: BedId, command: RecordingCommand) {
        emit(Subjects.recordingCommand(bed), "RecordingCommand", SystemClock.instant(), mapper.writeValueAsString(command))
    }

    override fun publishEvidenceRecord(bed: BedId, record: EvidenceRecord) {
        emit(Subjects.evidenceRecord(bed), "EvidenceRecord", record.at, mapper.writeValueAsString(record.toJson()))
    }

    private fun emit(subject: String, type: String, at: java.time.Instant, payload: String): EventRef? {
        val js = events.connection?.jetStream() ?: run {
            log.warn("No hay conexión JetStream, no se publica {}", type)
            return null
        }
        return try {
            val envelope = EventEnvelope(
                eventId = java.util.UUID.randomUUID().toString(),
                type = type,
                version = 1,
                occurredAt = at,
                source = "night-watch-runtime",
                payloadJson = payload,
            )
            val ack = js.publish(subject, mapper.writeValueAsBytes(envelope))
            EventRef(stream = ack.stream, seq = ack.seqno)
        } catch (e: Exception) {
            log.error("No se pudo publicar {} en {}: {}", type, subject, e.message)
            null
        }
    }
}
