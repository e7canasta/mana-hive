package com.manahive.runtime

import com.manahive.kernel.BedId
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.notice.NoticeEvent
import com.manahive.harbor.NoticeCommand
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.EvidenceRecord
import java.time.Instant

/**
 * Publishes domain events to any destination.
 *
 * The runtime produces domain events. This interface is the single exit point.
 * The adapter (NATS, file, MQTT) unwraps them for the channel internally.
 */
interface EventPublisher {
    fun publishSceneEvent(bed: BedId, event: SceneEvent)
    fun publishSentinelSignal(bed: BedId, signal: SentinelSignal)
    fun publishNoticeCommand(bed: BedId, signal: SentinelSignal, command: NoticeCommand)
    fun publishNoticeEvent(bed: BedId, event: NoticeEvent)
    fun publishRecordingCommand(
        bed: BedId,
        command: RecordingCommand,
        occurredAt: Instant = command.at,
    )
    fun publishEvidenceRecord(bed: BedId, record: EvidenceRecord)
}
