package com.manahive.politica.service

import com.manahive.messaging.NatsClientConfiguration
import org.springframework.context.annotation.Import
import com.manahive.politica.DefaultPolicyChangeProcessor
import com.manahive.politica.PolicyChangeProcessor
import com.manahive.contracts.policy.DagCatalog
import com.manahive.contracts.policy.STANDARD_CATALOG
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * Imperative shell of the politica engine. Wires:
 *  in  <- hub.policy.change.v1       (policy changes from hub)
 *  out -> hub.policy.calibration.v1.<resident>  (scene engine calibration)
 *  out -> hub.policy.response.v1.<resident>     (sentinel response rules)
 *  out -> hub.policy.escalation.v1.<resident>   (harbor escalation config)
 *  out -> hub.policy.recording.v1.<resident>    (recorder config)
 * Domain logic lives in politica-domain.
 */
@SpringBootApplication
@Import(NatsClientConfiguration::class)
class PoliticaApplication {

    @Bean
    fun dagCatalog(): DagCatalog = STANDARD_CATALOG

    @Bean
    fun policyChangeProcessor(catalog: DagCatalog): PolicyChangeProcessor =
        DefaultPolicyChangeProcessor(catalog)
}

fun main(args: Array<String>) {
    runApplication<PoliticaApplication>(*args)
}
