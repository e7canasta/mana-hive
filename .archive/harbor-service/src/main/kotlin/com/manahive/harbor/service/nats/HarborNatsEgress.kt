package com.manahive.harbor.service.nats

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.alarm.AlarmEvent
import com.manahive.contracts.sentinel.SentinelSignal
import com.manahive.harbor.NoticeCommand
import com.manahive.kernel.AlertId
import com.manahive.kernel.EpisodeId
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.JetStream
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.util.UUID

/**
 * Publishes AlarmEvents to alarm.event.v1.<alert> JetStream.
 *
 * Fowler: "Driving adapter" — outputs domain results to external systems.
 * Vernon: "Publishing domain events" — the harbor's output becomes bus messages.
 */
@Component
@ConditionalOnProperty(name = ["nats.enabled"], havingValue = "true", matchIfMissing = true)
public class HarborNatsEgress(
    private val connection: Connection,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = NatsObjectMapper.mapper
    private lateinit var jetStream: JetStream

    @PostConstruct
    public fun start() {
        try {
            jetStream = connection.jetStream()
            log.info("Harbor NATS egress started")
        } catch (e: Exception) {
            log.warn("NATS not available, egress disabled: {}", e.message)
        }
    }

    /**
     * Convert a NoticeCommand to an AlarmEvent and publish it.
     */
    public fun publishAlarmEvent(command: NoticeCommand, signal: SentinelSignal, now: java.time.Instant) {
        val alertId = AlertId("alert-${UUID.randomUUID()}")
        val event = when (command) {
            is NoticeCommand.Dispatch -> AlarmEvent.AlertRaised(
                alert = alertId,
                at = now,
                key = com.manahive.contracts.alarm.AlertKey(
                    bed = signal.bed,
                    rule = com.manahive.kernel.RuleId("rule-${UUID.randomUUID()}"),
                    episode = EpisodeId("episode-${UUID.randomUUID()}"),
                ),
                severity = signal.toSeverity(),
                origin = com.manahive.kernel.EventRef(
                    stream = "sentinel",
                    seq = 0,
                ),
            )
            is NoticeCommand.Resolve -> AlarmEvent.ResolvedManually(
                alert = alertId,
                at = now,
                by = com.manahive.kernel.StaffId("system"),
                cause = command.resolution.name,
            )
            is NoticeCommand.Escalate -> AlarmEvent.Escalated(
                alert = alertId,
                at = now,
                toStep = 2,
                cause = com.manahive.contracts.alarm.EscalationCause.NO_DELIVERY,
            )
            else -> null
        }

        if (event != null) {
            publishEvent(event)
        }
    }

    /**
     * Publish an AlarmEvent to alarm.event.v1.<alert>.
     */
    public fun publishEvent(event: AlarmEvent) {
        try {
            val subject = Subjects.alarmEvent(event.alert)
            val envelope = EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                type = event::class.simpleName ?: "AlarmEvent",
                version = 1,
                occurredAt = event.at,
                source = "harbor",
                payloadJson = mapper.writeValueAsString(event),
            )
            val data = mapper.writeValueAsBytes(envelope)
            jetStream.publish(subject, data)
            log.debug("Published alarm event to {}: {}", subject, event::class.simpleName)
        } catch (e: Exception) {
            log.error("Failed to publish alarm event: {}", e.message)
        }
    }

    private fun SentinelSignal.toSeverity(): com.manahive.contracts.policy.Severity = when (this) {
        is SentinelSignal.EpisodeOpened -> severity
        is SentinelSignal.UmbrellaEvent -> originalSeverity
        is SentinelSignal.AutoRecovery -> com.manahive.contracts.policy.Severity.INFO
        is SentinelSignal.EpisodeClosed -> com.manahive.contracts.policy.Severity.INFO
        is SentinelSignal.SuppressedWithRecord -> com.manahive.contracts.policy.Severity.INFO
        is SentinelSignal.DwellPreWarning -> com.manahive.contracts.policy.Severity.INFO
        is SentinelSignal.ComeBackPreWarning -> com.manahive.contracts.policy.Severity.INFO
    }
}
