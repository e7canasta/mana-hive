package com.manahive.hub

import com.manahive.contracts.EventEnvelope
import com.manahive.contracts.policy.PolicyChangeDetected
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.policy.catalogFor
import com.manahive.hub.policy.InMemoryPolicyLayerStore
import com.manahive.hub.policy.PolicyService
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import com.manahive.messaging.NatsConfig
import com.manahive.messaging.NatsObjectMapper
import com.manahive.messaging.Subjects
import io.nats.client.Connection
import io.nats.client.PushSubscribeOptions
import io.nats.client.api.ConsumerConfiguration
import io.nats.client.api.DeliverPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.Duration
import java.time.Instant

/**
 * SPEC-06, criterio 8: el cambio de política sale del hub y un motor puede
 * recalibrar con lo que llegó.
 *
 * Es el único test que ejercita el salto real por el bus. El resto de la suite
 * corre con `nats.enabled=false` y un doble, así que probaba que
 * `PolicyService` llama al publicador — no que el mensaje viaja. Con esa
 * cobertura sola, dos defectos reales pasaron: `NatsObjectMapper` sin
 * `JavaTimeModule` (todo `Instant` reventaba al serializar) y los streams de
 * JetStream que nadie creaba (*503 No Responders*). En los dos casos el egress
 * atrapaba la excepción, la escritura contestaba 200, y el bus quedaba vacío.
 *
 * Se salta —no falla— si no hay un NATS escuchando: `check` no puede exigir
 * infraestructura. Para correrlo:
 *
 *     nats-server -js -sd /tmp/natsdata
 */
@SpringBootTest(classes = [HubApplication::class], properties = ["nats.enabled=true"])
@ActiveProfiles("test")
@EnabledIf("com.manahive.hub.PolicyBusIntegrationSpec#hayNats")
class PolicyBusIntegrationSpec {

    public companion object {
        /**
         * Se evalúa antes de que Spring levante el contexto, que es la única
         * forma de saltear limpio: con `nats.enabled=true` y sin servidor, el
         * contexto no carga y el test fallaría en vez de saltearse.
         */
        @JvmStatic
        public fun hayNats(): Boolean = try {
            NatsConfig.createConnection().use { true }
        } catch (_: Exception) {
            false
        }
    }

    @Autowired lateinit var policyService: PolicyService
    @Autowired lateinit var layerStore: InMemoryPolicyLayerStore

    /** La conexión del propio hub: los streams los creó su bean de topología. */
    @Autowired lateinit var connection: Connection

    @Test
    fun `un cambio de nivel viaja por el bus y el motor puede recalibrar con el`() {
        run {
            val conn = connection

            val jose = ResidentId("jose-bus-${System.nanoTime()}")
            val jsm = conn.jetStreamManagement()

            // Secuencia del stream ANTES de publicar. Sirve para dos cosas:
            // comprobar que JetStream persistió el mensaje, y arrancar el
            // consumidor exactamente en él sin depender de corridas previas.
            val seqPrevia = jsm.getStreamInfo("POLICY").streamState.lastSequence

            // El cambio entra por donde entra de verdad: el servicio que usa el
            // controlador. Nada de construir el egress a mano — así el test
            // cubre también que el hub cablee su publicador y su topología.
            layerStore.clear()
            policyService.changeWatchLevel(
                jose, WatchLevel.CRITICAL, StaffId("nurse-1"),
                Instant.parse("2026-08-22T00:00:00Z"), "post-operatorio",
            )

            // JetStream lo persistió: la secuencia del stream avanzó.
            val seqNueva = jsm.getStreamInfo("POLICY").streamState.lastSequence
            assertEquals(seqPrevia + 1, seqNueva, "el mensaje no quedó en el stream POLICY")

            // Y acá está lo que core NATS no puede hacer: el consumidor se crea
            // DESPUÉS de publicar y aun así recibe el mensaje, leyéndolo del
            // stream. Es la propiedad por la que el bus es JetStream y no
            // pub/sub a secas — un suscriptor core que no estaba escuchando en
            // ese instante no habría visto nada.
            val opciones = PushSubscribeOptions.builder()
                .stream("POLICY")
                .configuration(
                    ConsumerConfiguration.builder()
                        .deliverPolicy(DeliverPolicy.ByStartSequence)
                        .startSequence(seqNueva)
                        .build(),
                )
                .build()
            val subscription = conn.jetStream()
                .subscribe(Subjects.policyChangeDetected(), opciones)

            val message = subscription.nextMessage(Duration.ofSeconds(5))
            assertNotNull(message, "el stream POLICY no entregó el mensaje persistido")
            message!!.ack()

            val envelope = NatsObjectMapper.mapper.readValue(message.data, EventEnvelope::class.java)
            assertEquals("PolicyChangeDetected", envelope.type)

            val change = NatsObjectMapper.mapper.readValue(
                envelope.payloadJson, PolicyChangeDetected::class.java,
            )
            assertEquals(jose, change.residentId)

            // Y ahora la otra mitad del criterio: que con lo que llegó se pueda
            // recalibrar. El nivel viaja como el label dentro de templateId —
            // ver la deuda anotada en el roadmap.
            val nivel = WatchLevel.entries.single { it.label == change.snapshot.templateId?.value }
            assertEquals(WatchLevel.CRITICAL, nivel)

            val calibracion = assertDoesNotThrow {
                com.manahive.politica.PolicyResolver.resolve(catalogFor(nivel), change.snapshot).value
            }
            assertEquals(true, calibracion.sentinel.alertRules.isNotEmpty())
        }
    }
}
