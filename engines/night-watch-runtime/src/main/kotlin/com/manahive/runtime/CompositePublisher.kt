package com.manahive.runtime

import com.manahive.kernel.BedId
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import com.manahive.contracts.notice.NoticeEvent
import com.manahive.recorder.RecordingCommand
import com.manahive.recorder.EvidenceRecord

/**
 * Composite [EventPublisher] that fans out to multiple publishers.
 *
 * Usage:
 * ```kotlin
 * val publisher = CompositePublisher(
 *     NatsEventPublisher(busEvents),
 *     FileEventWriter(outputDir, startTime),
 * )
 * ```
 */
class CompositePublisher(
    private vararg val delegates: EventPublisher,
) : EventPublisher {

    override fun publishSceneEvent(bed: BedId, event: SceneEvent) {
        delegates.forEach { it.publishSceneEvent(bed, event) }
    }

    override fun publishSentinelSignal(bed: BedId, signal: SentinelSignal) {
        delegates.forEach { it.publishSentinelSignal(bed, signal) }
    }

    override fun publishNoticeCommand(bed: BedId, signal: SentinelSignal, command: NoticeCommand) {
        delegates.forEach { it.publishNoticeCommand(bed, signal, command) }
    }

    override fun publishNoticeEvent(bed: BedId, event: NoticeEvent) {
        delegates.forEach { it.publishNoticeEvent(bed, event) }
    }

    override fun publishRecordingCommand(bed: BedId, command: RecordingCommand) {
        delegates.forEach { it.publishRecordingCommand(bed, command) }
    }

    override fun publishEvidenceRecord(bed: BedId, record: EvidenceRecord) {
        delegates.forEach { it.publishEvidenceRecord(bed, record) }
    }
}
