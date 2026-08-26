package com.manahive.hub

import com.manahive.contracts.policy.AlarmProfile
import com.manahive.contracts.policy.DwellThreshold
import com.manahive.contracts.policy.WatchLevel
import com.manahive.contracts.scene.StateKind
import com.manahive.hub.policy.InMemoryPolicyLayerStore
import com.manahive.hub.policy.PolicyEventPublisher
import com.manahive.hub.policy.PolicyService
import com.manahive.kernel.ResidentId
import com.manahive.kernel.StaffId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant

/**
 * Que un cambio de política llegue al bus.
 *
 * Los tests corren con `nats.enabled=false`, así que el publicador real
 * ([com.manahive.hub.nats.PolicyNatsEgress]) no existe como bean y el
 * `ObjectProvider` de [PolicyService] devolvía null: **ninguna escritura
 * ejercitaba el camino de publicación**. El criterio 6 de SPEC-06 pasaba a
 * ojo. Este doble ocupa ese lugar sin necesitar un NATS corriendo, y de paso
 * ejercita la resolución real del provider, no una construcción a mano.
 */
/** Anota lo publicado en vez de mandarlo a ningún lado. */
class RecordingPublisher : PolicyEventPublisher {
    val published: MutableList<Pair<ResidentId, AlarmProfile>> = mutableListOf()
    override fun publishPolicyChange(residentId: ResidentId, snapshot: AlarmProfile, at: Instant) {
        published += residentId to snapshot
    }
}

@TestConfiguration
class PublisherDoubles {
    @Bean
    fun recordingPublisher(): RecordingPublisher = RecordingPublisher()
}

@SpringBootTest(classes = [HubApplication::class])
@Import(PublisherDoubles::class)
@ActiveProfiles("test")
class PolicyPublishSpec {

    @Autowired lateinit var policyService: PolicyService
    @Autowired lateinit var layerStore: InMemoryPolicyLayerStore
    @Autowired lateinit var publisher: RecordingPublisher

    private val jose = ResidentId("jose")
    private val nurse = StaffId("nurse-1")
    private val t0: Instant = Instant.parse("2026-08-22T00:00:00Z")

    @BeforeEach
    fun setUp() {
        layerStore.clear()
        publisher.published.clear()
    }

    @Test
    fun `cambiar el nivel publica el cambio al bus`() {
        policyService.changeWatchLevel(jose, WatchLevel.CRITICAL, nurse, t0, "post-operatorio")

        assertEquals(1, publisher.published.size)
        assertEquals(jose, publisher.published.single().first)
    }

    @Test
    fun `el snapshot publicado lleva el nivel nuevo, no el anterior`() {
        policyService.changeWatchLevel(jose, WatchLevel.STANDARD, nurse, t0, "ingreso")
        policyService.changeWatchLevel(jose, WatchLevel.CRITICAL, nurse, t0.plusSeconds(60), "caída")

        // Lo que viaja al bus es el estado vigente tras el fold. Publicar el
        // snapshot previo dejaría a los motores una decisión atrás, en silencio.
        assertEquals(2, publisher.published.size)

        // OJO: AlarmProfile no tiene campo de nivel. El nivel —que es lo que
        // elige el catálogo -- viaja únicamente como el string de templateId,
        // porque el fold hace `LevelTemplate(id = event.level.label)`. Quien
        // consuma PolicyChangeDetected tiene que volver de ese label al enum.
        // Se fija acá para que romper esa convención rompa un test y no una
        // recalibración en producción.
        val ultimo = publisher.published.last().second
        assertEquals(WatchLevel.CRITICAL.label, ultimo.templateId?.value)
    }

    @Test
    fun `agregar y revocar un ajuste publican cada uno`() {
        policyService.changeWatchLevel(jose, WatchLevel.STANDARD, nurse, t0, "ingreso")
        policyService.addAdjustment(
            jose, "adj-1", StateKind.SITTING_IN_BED,
            DwellThreshold(Duration.ofMinutes(13), Duration.ofMinutes(15)),
            nurse, t0.plusSeconds(60), "se sienta mucho de noche",
        )
        policyService.revokeAdjustment(jose, "adj-1", nurse, t0.plusSeconds(120))

        assertEquals(3, publisher.published.size)
        assertTrue(publisher.published.all { it.first == jose })
    }
}
