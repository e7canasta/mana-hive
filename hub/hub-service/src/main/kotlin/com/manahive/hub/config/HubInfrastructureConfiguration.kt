package com.manahive.hub.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.manahive.contracts.ledger.LedgerPort
import com.manahive.contracts.ledger.WatermarkPort
import com.manahive.contracts.policy.PolicyCatalog
import com.manahive.contracts.policy.RawPolicyStore
import com.manahive.contracts.policy.SemanticBucketStore
import com.manahive.hub.ledger.EventStore
import com.manahive.hub.ledger.InMemoryLedger
import com.manahive.hub.ledger.InMemoryWatermarkStore
import com.manahive.hub.ledger.StreamCatalog
import com.manahive.hub.ledger.WatermarkCatalog
import com.manahive.hub.policy.InMemoryPolicyCatalog
import com.manahive.hub.policy.InMemoryPolicyLayerStore
import com.manahive.hub.policy.InMemoryRawPolicyStore
import com.manahive.hub.policy.InMemorySemanticBucketStore
import com.manahive.messaging.BusConnector
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

/**
 * Declares the in-memory beans that the hub needs to boot.
 *
 * Every interface the controllers and listeners inject is satisfied here with
 * its in-memory counterpart. For production, swap these for database-backed
 * implementations — the ports don't change.
 *
 * La conexión al bus y sus streams vienen de [NatsClientConfiguration], que es
 * la misma que importan los cinco motores: un solo lugar donde se decide cómo
 * se conecta este sistema. Los tests la apagan con `nats.enabled=false`.
 */
@Configuration
public class HubInfrastructureConfiguration {

    // ── Jackson ────────────────────────────────────────────────────────────

    @Bean
    @Primary
    /**
     * El mismo mapper que usa el bus, no uno propio.
     *
     * Este bean era un `jacksonObjectMapper()` pelado, sin `JavaTimeModule`, y
     * `NatsIngestListener` lo recibe inyectado: **todo** lo que llegaba del bus
     * moría con *"Java 8 date/time type Instant not supported"*, porque cada
     * `EventEnvelope` lleva un `occurredAt`. El hub es el System of Record y no
     * estaba grabando nada; el error quedaba en un log que nadie miraba.
     */
    public fun objectMapper(): ObjectMapper = NatsObjectMapper.mapper

    // ── Ledger ────────────────────────────────────────────────────────────

    @Bean
    public fun ledger(): LedgerPort = InMemoryLedger()

    @Bean
    public fun watermarkStore(): WatermarkPort = InMemoryWatermarkStore()

    @Bean
    public fun eventStore(ledger: LedgerPort): EventStore = EventStore(ledger)

    @Bean
    public fun streamCatalog(): StreamCatalog = StreamCatalog.DEFAULT

    @Bean
    public fun watermarkCatalog(): WatermarkCatalog = WatermarkCatalog.DEFAULT

    // ── Policy stores ─────────────────────────────────────────────────────

    @Bean
    public fun policyLayerStore(): InMemoryPolicyLayerStore = InMemoryPolicyLayerStore()

    @Bean
    public fun rawPolicyStore(): RawPolicyStore = InMemoryRawPolicyStore()

    @Bean
    public fun semanticBucketStore(): SemanticBucketStore = InMemorySemanticBucketStore()

    @Bean
    public fun policyCatalog(): PolicyCatalog = InMemoryPolicyCatalog()

    // ── Bus ───────────────────────────────────────────────────────────────

    @Bean
    public fun busEvents(): BusEvents = BusEvents()

    /**
     * Conexión al bus que no bloquea el arranque y reintenta para siempre.
     *
     * El hub es el System of Record: si no arranca porque NATS todavía no está,
     * la API de política tampoco responde y nadie puede dar de alta a un
     * residente. En 24/7 la caída del bus tiene que degradar la ingesta, no
     * tumbar el servicio.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = ["nats.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    public fun busConnector(
        @Value("\${nats.url:nats://localhost:4222}") url: String,
        events: BusEvents,
    ): BusConnector = BusConnector(url, events)
}
