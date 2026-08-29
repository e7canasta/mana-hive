package com.manahive.runtime

import com.manahive.contracts.policy.WatchLevel
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.NightId
import com.manahive.kernel.ResidentId
import com.manahive.messaging.BusConnector
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling                         // sin esto el @Scheduled del barrido no corre
class NightWatchApplication {

    @Bean
    fun objectMapper() = NatsObjectMapper.mapper

    @Bean
    fun census(): Census {
        // POC defaults. In production, loaded from the housing census.
        val census = Census()
        census.register(BedId("bed-4"), ResidentId("jose"), NightId("night-jose-301"), MonitorId("CAMERA_MAIN"))
        census.register(BedId("bed-5"), ResidentId("elena"), NightId("night-elena-401"), MonitorId("CAMERA_ROOM_401"))
        return census
    }

    @Bean
    fun runtime(): NightWatchRuntime = NightWatchRuntime()

    @Bean
    fun runtimeStatus(): RuntimeStatusHolder = RuntimeStatusHolder()

    /**
     * La conexión al bus, con el servicio escuchando sus cambios de estado.
     *
     * Cada vez que el bus vuelve hay que **re-suscribirse**: las suscripciones
     * de una conexión caída no reviven solas. Y cada vez que se cae, el estado
     * del servicio tiene que reflejarlo, porque un runtime sin bus no está
     * vigilando a nadie aunque el proceso siga vivo.
     */
    @Bean
    fun busEvents(): BusEvents = BusEvents()

    /**
     * Arranca la conexión al bus sin bloquear el arranque del servicio.
     * La conexión aparece en [BusEvents.connection] cuando el bus está.
     */
    @Bean
    fun busConnector(
        @Value("\${nats.url:nats://localhost:4222}") url: String,
        events: BusEvents,
    ): BusConnector = BusConnector(url, events)
}

fun main(args: Array<String>) {
    runApplication<NightWatchApplication>(*args)
}
