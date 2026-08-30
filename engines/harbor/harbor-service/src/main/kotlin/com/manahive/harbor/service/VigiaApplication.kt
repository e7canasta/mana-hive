package com.manahive.harbor.service

import com.manahive.messaging.NatsClientConfiguration
import org.springframework.context.annotation.Import
import com.manahive.contracts.policy.Severity
import com.manahive.harbor.Channel
import com.manahive.harbor.HarborCalibration
import com.manahive.harbor.HarborEngine
import com.manahive.harbor.createHarborEngine
import com.manahive.harbor.harborCalibration
import com.manahive.kernel.ResidentId
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Duration

/**
 * Imperative shell of the vigia. Wires:
 *  in  <- sentinel.signal.v1.>  IncidentDeclared  (durable "vigia")
 *  in  <- scene.fact.v1.>       StaffPresenceDetected (closes the loop)
 *  out -> alarm.event.v1.<alert>
 *  out -> delivery adapters (release 1: console; later push/tablets)
 * Hosts the AlertLifecycle process: escalation deadlines are DERIVED from
 * DeliveryOrdered.occurredAt and evaluated on sweep — a restart never loses
 * an escalation. Decisions live in vigia-domain.
 */
@SpringBootApplication
@Import(NatsClientConfiguration::class)
class VigiaApplication {

    @Bean
    fun harborCalibration(): HarborCalibration = harborCalibration {
        resident(ResidentId("default"))
        notice {
            channels = setOf(Channel.CONSOLE)
            escalationTimeout = Duration.ofMinutes(30)
        }
        alert {
            channels = setOf(Channel.PUSH, Channel.TABLET)
            escalationTimeout = Duration.ofMinutes(5)
        }
        call {
            channels = setOf(Channel.PUSH, Channel.TABLET)
            escalationTimeout = Duration.ofMinutes(2)
        }
        incident {
            channels = setOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD, Channel.CONSOLE)
            escalationTimeout = Duration.ZERO
        }
    }

    @Bean
    fun harborEngine(calibration: HarborCalibration): HarborEngine =
        createHarborEngine(calibration)
}

fun main(args: Array<String>) {
    runApplication<VigiaApplication>(*args)
}
