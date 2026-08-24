package com.manahive.sentinel.service

import com.manahive.sentinel.FatigueBudget
import com.manahive.sentinel.SentinelCalibration
import com.manahive.sentinel.SentinelEvaluator
import com.manahive.sentinel.createSentinelEvaluator
import com.manahive.sentinel.sentinelCalibration
import com.manahive.kernel.ResidentId
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * Imperative shell of the sentinel. Wires:
 *  in  <- scene.fact.v1.>                        (durable "sentinel")
 *  in  <- hub.policy.effective-rules.v1.>        (rules cache, by resident)
 *  out -> sentinel.signal.v1.<bed>               (incident / occurrence / suppression)
 * Episode state is persisted locally between facts and rebuilt from the scene
 * stream on cold start. Judgment lives in sentinel-domain.
 */
@SpringBootApplication
class SentinelApplication {

    @Bean
    fun sentinelCalibration(): SentinelCalibration = sentinelCalibration {
        resident(ResidentId("default"))
        fatigue {
            maxPerShift = 12
        }
    }

    @Bean
    fun sentinelEvaluator(calibration: SentinelCalibration): SentinelEvaluator =
        createSentinelEvaluator(calibration)
}

fun main(args: Array<String>) {
    runApplication<SentinelApplication>(*args)
}
