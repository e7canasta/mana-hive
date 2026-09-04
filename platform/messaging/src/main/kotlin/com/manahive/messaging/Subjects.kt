package com.manahive.messaging

import com.manahive.kernel.AlertId
import com.manahive.kernel.BedId
import com.manahive.kernel.NoticeId
import com.manahive.kernel.ResidentId

/**
 * The subject taxonomy of the bus. Version is part of the subject: a breaking
 * change is a NEW subject, and old consumers keep working until retired.
 */
public object Subjects {
    public fun perceptionObservation(bed: BedId): String = "perception.observation.v1.${bed.value}"
    public fun sceneEvent(bed: BedId): String = "scene.fact.v1.${bed.value}"
    public fun sentinelSignal(bed: BedId): String = "sentinel.signal.v1.${bed.value}"
    public fun alarmEvent(alert: AlertId): String = "alarm.event.v1.${alert.value}"
    public fun effectiveRules(resident: ResidentId): String = "hub.policy.effective-rules.v1.${resident.value}"
    public fun policyChangeDetected(): String = "hub.policy.change.v1"

    /**
     * La novedad del perfil completo de un residente.
     *
     * Cae bajo [POLICY_WILDCARD] a proposito: viaja por el stream de politica
     * que ya existe, sin topologia nueva. Lleva el perfil **entero**, nunca un
     * delta — si el mensaje se pierde, el arranque en frio lo recupera.
     */
    public fun residentProfile(): String = "hub.policy.profile.v1"
    public fun recordingCommand(bed: BedId): String = "recorder.command.v1.${bed.value}"
    public fun evidenceRecord(bed: BedId): String = "evidence.record.v1.${bed.value}"
    public fun noticeEvent(noticeId: NoticeId): String = "notice.event.v1.${noticeId.value}"
    public fun hubEpisode(bed: BedId): String = "hub.episode.v1.${bed.value}"

    public const val CENSUS_SNAPSHOT: String = "hub.census.snapshot.v1"

    public const val PERCEPTION_WILDCARD: String = "perception.observation.v1.>"
    public const val SCENE_WILDCARD: String = "scene.fact.v1.>"
    public const val SENTINEL_WILDCARD: String = "sentinel.signal.v1.>"
    public const val ALARM_WILDCARD: String = "alarm.event.v1.>"
    public const val POLICY_WILDCARD: String = "hub.policy.>"
    public const val RECORDER_WILDCARD: String = "recorder.command.v1.>"
    public const val EVIDENCE_WILDCARD: String = "evidence.record.v1.>"
    public const val NOTICE_WILDCARD: String = "notice.event.v1.>"
    public const val HUB_EPISODE_WILDCARD: String = "hub.episode.v1.>"
    public const val HUB_SCENE_WILDCARD: String = "hub.scene.v1.>"
}
