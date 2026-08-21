package com.manahive.sentinel.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Imperative shell of the sentinel. Wires:
 *  in  <- scene.fact.v1.>                        (durable "sentinel")
 *  in  <- hub.policy.effective-rules.v1.>        (rules cache, by resident)
 *  out -> sentinel.signal.v1.<bed>               (incident / occurrence / suppression)
 * Episode state is persisted locally between facts and rebuilt from the scene
 * stream on cold start. Judgment lives in sentinel-domain.
 */
@SpringBootApplication
class SentinelApplication

fun main(args: Array<String>) {
    runApplication<SentinelApplication>(*args)
}
