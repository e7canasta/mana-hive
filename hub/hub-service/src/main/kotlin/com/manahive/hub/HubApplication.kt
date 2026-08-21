package com.manahive.hub

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The hub is the SYSTEM OF RECORD: every event published by every engine is
 * ingested from the bus into the Postgres ledger — one global order, forensic
 * audit, golden replay, and the re-seed source when an engine needs to
 * rebuild state. The bus buffers and transports; the hub remembers.
 *
 * Bounded contexts inside (Spring Modulith modules, one package each):
 *  - housing:   structure + census, the 1:1 bed/resident invariant INSIDE
 *  - policy:    clinical policy per resident (event-sourced) + PolicyResolver
 *  - chronicle: incidents, human verdicts (ground truth), evidence
 *  - inquiry:   cross-context read models + the moviola (forensic join)
 */
@SpringBootApplication
class HubApplication

fun main(args: Array<String>) {
    runApplication<HubApplication>(*args)
}
