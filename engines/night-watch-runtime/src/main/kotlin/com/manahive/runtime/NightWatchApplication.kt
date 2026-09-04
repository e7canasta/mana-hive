package com.manahive.runtime

import com.manahive.messaging.BusConnector
import com.manahive.messaging.BusEvents
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.runtime.control.ControlEventPublisher
import com.manahive.runtime.control.HiveControlService
import com.manahive.runtime.control.HiveControlServiceImpl
import com.manahive.runtime.control.HubProfileFetcherAdapter
import com.manahive.runtime.control.NatsControlEventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling                         // sin esto el @Scheduled del barrido no corre
class NightWatchApplication {

    @Bean
    fun buildInfo(): InfoContributor = InfoContributor { builder ->
        builder.withDetail("build", mapOf(
            "version" to BUILD_VERSION,
            "component" to "mana-hive",
        ))
    }

    @Bean
    fun objectMapper() = NatsObjectMapper.mapper

    /**
     * El censo sale del disco, no del codigo.
     *
     * Estaba cableado con dos residentes de ejemplo: instalar el sistema en una
     * habitacion real requeria recompilar. Ahora se lee de
     * `manahive.profiles.dir`/census.json, al lado de los perfiles.
     */
    @Bean
    fun census(
        @Value("\${manahive.profiles.dir:profiles}") profilesDir: String,
    ): Census {
        val census = Census()
        CensusSeed(census, java.io.File(profilesDir, "census.json")).load()
        return census
    }

    @Bean
    fun runtime(): NightWatchRuntime = NightWatchRuntime()

    @Bean
    fun runtimeStatus(): RuntimeStatusHolder = RuntimeStatusHolder()

    /**
     * El core: orquestación pura, sin NATS ni Spring.
     *
     * En producción usa SystemClock. En tests se pasa un ManualClock
     * y todos los componentes comparten la misma referencia.
     */
    @Bean
    fun calibrator(
        runtime: NightWatchRuntime,
        census: Census,
    ): ProfileCalibrator = ProfileCalibrator(runtime, census)

    @Bean
    fun nightWatchServiceCore(
        runtime: NightWatchRuntime,
        census: Census,
        publisher: EventPublisher,
        calibrator: ProfileCalibrator,
    ): NightWatchServiceCore = NightWatchServiceCore(runtime, census, publisher, com.manahive.kernel.SystemClock, calibrator)

    @Bean
    fun timeSink(
        core: NightWatchServiceCore,
    ): TimeSink = core

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

    /**
     * NATS adapter for event publishing.
     *
     * The core publishes through [EventPublisher]; this bean provides
     * the NATS implementation. Swap with [FileEventWriter] for tests.
     */
    @Bean
    fun natsEventPublisher(
        events: BusEvents,
    ): EventPublisher {
        // JetStream is created when the bus connects
        // NatsEventPublisher needs it at publish time, not at creation time
        return NatsEventPublisher(events)
    }

    @Bean
    fun controlEventPublisher(events: BusEvents): ControlEventPublisher = NatsControlEventPublisher(events)

    @Bean
    fun hiveControlService(
        calibrator: ProfileCalibrator,
        census: Census,
        runtime: NightWatchRuntime,
        controlEventPublisher: ControlEventPublisher,
        @org.springframework.beans.factory.annotation.Value("\${hub.url:http://hub-service:8080}") hubUrl: String,
    ): HiveControlService {
        // Fowler: DIP — calibrator es singleton compartido (mismo puntero que core)
        return HiveControlServiceImpl(
            calibrator = calibrator,
            runtime = runtime,
            census = census,
            profileFetcher = HubProfileFetcherAdapter(hubUrl),
            controlPublisher = controlEventPublisher,
            clock = java.time.Clock.systemUTC(),
        )
    }
}

const val BUILD_VERSION = "envelope-v2-2026-09-02"

fun main(args: Array<String>) {
    runApplication<NightWatchApplication>(*args)
}
