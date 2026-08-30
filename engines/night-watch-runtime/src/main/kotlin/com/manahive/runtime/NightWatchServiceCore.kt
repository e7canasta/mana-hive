package com.manahive.runtime

import com.manahive.contracts.alarm.AlertKey
import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.catalogFor
import com.manahive.contracts.scene.SceneEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.contracts.notice.NoticeEvent
import com.manahive.contracts.notice.NoticeContext
import com.manahive.contracts.notice.NoticeResolution
import com.manahive.kernel.NoticeId
import com.manahive.harbor.NoticeCommand
import com.manahive.kernel.AlertId
import com.manahive.kernel.EventRef
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.contracts.policy.LevelTemplate
import com.manahive.contracts.policy.PolicyLayers
import com.manahive.contracts.policy.toAlarmProfile
import com.manahive.politica.PolicyResolver
import com.manahive.kernel.Clock
import com.manahive.kernel.ManualClock
import com.manahive.kernel.SystemClock
import com.manahive.recorder.RecordingCommand
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Pure orchestration: no NATS, no Spring, no I/O.
 *
 * Depends on [NightWatchRuntime], [Census], [ProfileCalibrator], [EventPublisher], and [Clock].
 * All side effects go through [EventPublisher] — the core doesn't know where events go.
 *
 * The [Clock] is a shared mutable reference — when it advances, everyone sees the new time.
 */
class NightWatchServiceCore(
    private val runtime: NightWatchRuntime,
    private val census: Census,
    private val publisher: EventPublisher,
    private var clock: Clock,
) : NightWatchServiceContract, TimeSink {

    private val log = LoggerFactory.getLogger(javaClass)
    internal val calibrator = ProfileCalibrator(runtime, census)

    val residentCount: Int get() = runtime.size

    override fun onObservation(obs: Observation) {
        log.info("[OBS] Received {} for bed {} at {}", obs.kind, obs.bed.value, obs.observedAt)
        val entry = census.lookup(obs.bed)
        if (entry == null) {
            log.debug("No census entry for bed {}, ignoring", obs.bed.value)
            return
        }
        log.info("[OBS] Census hit: resident={}", entry.resident.value)
        val out = runtime.onObservation(entry.resident, obs)
        log.info("[OBS] Runtime produced: sceneFacts={}, signals={}, harborCmds={}, recorderCmds={}",
            out.sceneFacts.size, out.signals.size, out.harborCommands.size, out.recorderCommands.size)
        publish(obs.bed, out)
    }

    override fun onPolicyChange(change: PolicyChangeDetected) {
        val raw = change.snapshot.templateId?.value.orEmpty()
        val level = parseWatchLevel(raw)
        if (level == null) {
            log.error(
                "Nivel irreconocible '{}' para {}: no se recalibra.",
                raw, change.residentId.value,
            )
            return
        }
        val catalog = catalogFor(level)
        val layers = PolicyLayers(
            level = level,
            template = LevelTemplate(id = change.snapshot.templateId?.value ?: level.label, level = level),
            adjustments = emptyList(),
            windows = emptyList(),
        )
        val profile = layers.toAlarmProfile(change.residentId, change.at)
        val calibration = PolicyResolver.resolve(catalog, profile.value).value
        val existing = runtime.get(change.residentId)
        val bedId = existing?.bed ?: census.bedFor(change.residentId)?.bed ?: com.manahive.kernel.BedId("unknown")
        val monitorId = existing?.monitor ?: census.bedFor(change.residentId)?.monitor ?: com.manahive.kernel.MonitorId("unknown")
        val calibrations = EngineCalibrations.from(calibration, bedId, monitorId)

        if (existing == null) {
            val bed = census.bedFor(change.residentId)
            if (bed == null) {
                log.error(
                    "Política para {} pero no está en el censo",
                    change.residentId.value,
                )
                return
            }
            runtime.register(change.residentId, bed.bed, bed.night, bed.monitor, calibrations)
            log.info("Alta de {} en nivel {}", change.residentId.value, level)
        } else {
            runtime.recalibrate(change.residentId, calibrations)
            log.info("Recalibrated {} to level {}", change.residentId.value, level)
        }
    }

    override fun onProfileChanged(profile: ResidentProfileDto) {
        calibrator.accept(profile)
    }

    override fun sweep() {
        calibrator.reprojectOnWindowEdge()
        if (runtime.size == 0) return
        val now = clock.instant()
        log.info("[SWEEP] Running at {}", now)
        val results = runtime.tickAll(now)
        for ((residentId, out) in results) {
            log.info("[SWEEP] {}: sceneFacts={}, signals={}, harborCmds={}",
                residentId.value, out.sceneFacts.size, out.signals.size, out.harborCommands.size)
            for (fact in out.sceneFacts) {
                log.info("[SWEEP]   fact: {}", fact::class.simpleName)
            }
            for (signal in out.signals) {
                log.info("[SWEEP]   signal: {}", signal::class.simpleName)
            }
            runtime.get(residentId)?.let { publish(it.bed, out) }
        }
    }

    fun sweepAt(now: Instant) {
        calibrator.reprojectOnWindowEdge()
        if (runtime.size == 0) return
        val results = runtime.tickAll(now)
        for ((residentId, out) in results) {
            runtime.get(residentId)?.let { publish(it.bed, out) }
        }
    }

    override fun advanceTime(duration: java.time.Duration) {
        val c = clock
        if (c is ManualClock) {
            c.advance(duration)
            log.info("[TIME] Advanced by {} → now {}", duration, c.instant())
        } else {
            log.warn("[TIME] advanceTime ignored: clock is SystemClock, not ManualClock")
        }
    }

    override fun setTime(instant: Instant) {
        val c = clock
        if (c is ManualClock) {
            c.setTo(instant)
            log.info("[TIME] Set to {}", instant)
        } else {
            log.warn("[TIME] setTime ignored: clock is SystemClock, not ManualClock")
        }
    }

    override fun useManual(startAt: Instant) {
        clock = ManualClock(startAt)
        log.info("[TIME] ═══ SWITCHED TO ManualClock at {} ═══", startAt)
    }

    override fun useSystem() {
        clock = SystemClock
        log.info("[TIME] ═══ SWITCHED TO SystemClock ═══")
    }

    private fun publish(bed: com.manahive.kernel.BedId, out: Outbound) {
        for (fact in out.sceneFacts) {
            publisher.publishSceneEvent(bed, fact)
        }

        for (signal in out.signals) {
            publisher.publishSentinelSignal(bed, signal)
        }

        for ((signal, command) in out.harborCommands) {
            publisher.publishNoticeEvent(bed, toNoticeEvent(signal, command))
        }

        for (command in out.recorderCommands) {
            publisher.publishRecordingCommand(bed, command)
        }

        log.info("[EVIDENCE] evidenceRecords={}", out.evidenceRecords.size)
        for (record in out.evidenceRecords) {
            publisher.publishEvidenceRecord(bed, record)
        }
    }

    private fun parseWatchLevel(value: String): WatchLevel? =
        WatchLevel.entries.firstOrNull { it.label == value || it.name == value }

    /**
     * Convert domain NoticeCommand + SentinelSignal → contracts NoticeEvent.
     *
     * The domain NoticeCommand is internal to Harbor.
     * The contracts NoticeEvent is what goes on the bus for hub.
     */
    private fun toNoticeEvent(signal: SentinelSignal, command: NoticeCommand): NoticeEvent {
        val episode = when (signal) {
            is SentinelSignal.EpisodeOpened -> signal.episode
            is SentinelSignal.EpisodeClosed -> signal.episode
            is SentinelSignal.AutoRecovery -> signal.episode
            is SentinelSignal.UmbrellaEvent -> signal.episode
            else -> com.manahive.kernel.EpisodeId("unknown")
        }
        val noticeId = NoticeId.fromEpisode(episode)
        return when (command) {
            is NoticeCommand.Dispatch -> {
                val opened = signal as? SentinelSignal.EpisodeOpened
                NoticeEvent.Dispatch(
                    noticeId = noticeId,
                    at = signal.at,
                    bed = signal.bed,
                    episode = episode,
                    resident = opened?.resident,
                    severity = opened?.severity ?: com.manahive.contracts.policy.Severity.WARNING,
                    channels = command.channels,
                    recipients = emptyList(),
                    message = "${opened?.rule ?: "unknown"}: ${signal.bed.value}",
                    context = NoticeContext(
                        episode = episode,
                        rule = opened?.rule ?: com.manahive.kernel.RuleId("unknown"),
                        baseline = opened?.trigger?.name ?: "unknown",
                        trigger = opened?.trigger?.name ?: "unknown",
                        duration = java.time.Duration.ZERO,
                        description = opened?.rule?.value ?: "notice",
                    ),
                )
            }
            is NoticeCommand.Resolve -> {
                val resolution = when (command.resolution) {
                    com.manahive.harbor.Resolution.STAFF_PRESENT -> NoticeResolution.STAFF_PRESENT
                    com.manahive.harbor.Resolution.AUTO_RECOVERY -> NoticeResolution.AUTO_RECOVERY
                    com.manahive.harbor.Resolution.SUPERSEDED -> NoticeResolution.SUPERSEDED
                }
                NoticeEvent.Resolved(
                    noticeId = noticeId,
                    at = command.at,
                    resolution = resolution,
                    resolvedBy = null,
                    duration = java.time.Duration.ZERO,
                )
            }
            else -> NoticeEvent.Dispatch(
                noticeId = noticeId,
                at = signal.at,
                bed = signal.bed,
                episode = episode,
                resident = null,
                severity = com.manahive.contracts.policy.Severity.WARNING,
                channels = emptySet(),
                recipients = emptyList(),
                message = command::class.simpleName ?: "unknown",
                context = NoticeContext(
                    episode = episode,
                    rule = com.manahive.kernel.RuleId("unknown"),
                    baseline = "unknown",
                    trigger = "unknown",
                    duration = java.time.Duration.ZERO,
                    description = command::class.simpleName ?: "unknown",
                ),
            )
        }
    }
}
