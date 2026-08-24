package com.manahive.sentinel.service

import com.manahive.contracts.scene.StateKind
import com.manahive.contracts.policy.TriggerOn
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.messaging.NatsClientConfiguration
import org.springframework.context.annotation.Import
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
 *
 * NOTE: Fatigue is NOT configured here. Fatigue is a delivery concern (Harbor).
 * Sentinel ALWAYS opens episodes when a rule matches.
 */
@SpringBootApplication
@Import(NatsClientConfiguration::class)
class SentinelApplication {

    @Bean
    fun sentinelCalibration(): SentinelCalibration = sentinelCalibration {
        resident(ResidentId("default"))

        // Sin una sola regla, este servicio consume del bus y no puede emitir
        // nada: los episodios no se abren nunca y el silencio parece normal.
        // Es el "verde vacuo" que el roadmap ya marco con Susan. Este default
        // es provisional — la calibracion real llega por politica, resuelta
        // desde el catalogo del nivel del residente.
        rule("r-sitting", StateKind.SITTING_IN_BED, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
        }

        rule("r-standing", StateKind.STANDING, TriggerOn.ENTRY) {
            severity = Severity.WARNING
            closureCondition = ClosureCondition.SAFE_ONLY
            reversible = true
            umbrellaEvents(StateKind.SITTING_IN_BED)
        }
    }

    @Bean
    fun sentinelEvaluator(calibration: SentinelCalibration): SentinelEvaluator =
        createSentinelEvaluator(calibration)
}

fun main(args: Array<String>) {
    runApplication<SentinelApplication>(*args)
}
