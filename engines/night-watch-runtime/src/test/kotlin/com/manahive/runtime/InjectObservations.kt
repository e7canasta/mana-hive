package com.manahive.runtime

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.perception.Observation
import com.manahive.contracts.perception.ObservationKind
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Nats
import io.nats.client.Options
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Inyecta observaciones por el bus, como lo haria el edge server.
 *
 * No es un test unitario: es el arnes que prueba el **servicio real** corriendo
 * en otro proceso. Se corre a mano con `-Dmanahive.inject=1` mientras
 * `bootRun` esta levantado, y verifica lo unico que no se puede verificar en
 * memoria: que el runtime desplegado escuche el bus y produzca señales.
 */
class InjectObservations {

    @Test
    fun `inyecta y escucha`() {
        if (System.getenv("MANAHIVE_INJECT") == null) return

        val mapper = NatsObjectMapper.mapper
        val cama = BedId("bed-5")
        val camara = MonitorId("CAMERA_ROOM_401")

        Nats.connect(
            Options.Builder().server("nats://localhost:4222")
                .connectionTimeout(Duration.ofSeconds(5)).build(),
        ).use { conn ->
            val crudas = mutableListOf<String>()
            val d = conn.createDispatcher { msg ->
                runCatching {
                    val env = mapper.readValue(msg.data, EventEnvelope::class.java)
                    if (env.type == "SentinelSignal") {
                        crudas += env.payloadJson
                    }
                }
            }
            d.subscribe(Subjects.SENTINEL_WILDCARD)

            val js = conn.jetStream()
            var t = Instant.now()

            fun observar(kind: ObservationKind, avanzar: Long) {
                t = t.plusSeconds(avanzar)
                val obs = Observation(
                    sourceEventId = UUID.randomUUID().toString(),
                    monitor = camara, bed = cama, kind = kind,
                    confidence = 0.95, observedAt = t,
                )
                val env = EventEnvelope(
                    eventId = UUID.randomUUID().toString(),
                    type = "perception.observation", version = 1,
                    occurredAt = t, source = "inyector",
                    payloadJson = mapper.writeValueAsString(obs),
                )
                js.publish(Subjects.perceptionObservation(cama), mapper.writeValueAsBytes(env))
                println("→ ${kind.name}")
                Thread.sleep(400)
            }

            println("── Elena, 03:00: acostada, se sienta al borde, se cae ──")
            observar(ObservationKind.IN_BED, 0)
            observar(ObservationKind.BED_EDGE, 3)
            observar(ObservationKind.ON_FLOOR, 3)

            Thread.sleep(2500)
            conn.closeDispatcher(d)

            println("── SEÑALES RECIBIDAS: ${crudas.size} ──")
            crudas.forEach { println("   ⚑ $it") }
        }
    }
}
