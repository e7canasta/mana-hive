package com.manahive.vigia.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

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
class VigiaApplication

fun main(args: Array<String>) {
    runApplication<VigiaApplication>(*args)
}
