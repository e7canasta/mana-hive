package com.manahive.runtime

import com.manahive.messaging.BusEvents
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/**
 * Salud del servicio para Actuator.
 *
 * "El proceso está vivo" no alcanza en 24/7: un runtime sin bus está vivo y no
 * vigila a nadie. Esto reporta lo que de verdad importa — si está consumiendo,
 * hace cuánto está así, y a cuántos residentes cubre.
 */
@Component
class NightWatchHealthIndicator(
    private val status: RuntimeStatusHolder,
    private val runtime: NightWatchRuntime,
    private val events: BusEvents,
) : HealthIndicator {

    override fun health(): Health {
        val s = status.current
        val builder = if (s.healthy) Health.up() else Health.down()
        return builder
            .withDetail("state", s.state.name)
            .withDetail("detail", s.detail)
            .withDetail("since", s.since.toString())
            .withDetail("forSeconds", Duration.between(s.since, Instant.now()).seconds)
            .withDetail("bus", events.connection?.status?.name ?: "SIN_CONEXION")
            .withDetail("residents", runtime.size)
            .build()
    }
}

/**
 * Estado operativo del runtime, para preguntar desde afuera sin leer logs.
 *
 * Responde la pregunta de la guardia: *¿está vigilando, a cuántos, y desde
 * cuándo?* Y por residente: cuántos episodios tiene abiertos ahora.
 */
@RestController
class RuntimeStatusController(
    private val status: RuntimeStatusHolder,
    private val runtime: NightWatchRuntime,
    private val events: BusEvents,
) {

    @GetMapping("/api/runtime/status")
    fun status(): ResponseEntity<RuntimeStatusResponse> {
        val s = status.current
        val body = RuntimeStatusResponse(
            state = s.state.name,
            detail = s.detail,
            since = s.since.toString(),
            forSeconds = Duration.between(s.since, Instant.now()).seconds,
            bus = events.connection?.status?.name ?: "SIN_CONEXION",
            residents = runtime.residents().map { (id, rt) ->
                ResidentStatusResponse(
                    resident = id.value,
                    bed = rt.bed.value,
                    openEpisodes = rt.openEpisodeCount(),
                    dispatched = rt.dispatchedCount(),
                )
            }.sortedBy { it.resident },
        )
        // Un runtime sin bus no está sirviendo: que el código HTTP lo diga.
        val code = if (s.healthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(code).body(body)
    }
}

data class RuntimeStatusResponse(
    val state: String,
    val detail: String,
    val since: String,
    val forSeconds: Long,
    val bus: String,
    val residents: List<ResidentStatusResponse>,
)

data class ResidentStatusResponse(
    val resident: String,
    val bed: String,
    val openEpisodes: Int,
    val dispatched: Int,
)
