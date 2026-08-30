package com.manahive.politica.adapters

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.scene.SceneState
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.politica.profile.ProfileMapper
import com.manahive.politica.profile.ProfileMapping
import com.manahive.politica.profile.ProfileProjection
import com.manahive.profile.api.ProfileExamples
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration

/**
 * El recorrido completo: documento del director → calibraciones de los motores.
 *
 * Es la prueba de la fase 4 de SPEC-02 —*"barral bajo un minuto de noche abre
 * episodio en Sentinel"*— hasta donde llega sin bus: entra el perfil de Elena y
 * salen las cuatro calibraciones con la baranda adentro.
 *
 * Antes de esto la cadena se cortaba en tres lugares a la vez, y cada corte
 * bastaba solo para que la baranda no llegara: [com.manahive.contracts.policy.PolicyCalibration]
 * no tenia donde transportarla, el DSL de Sentinel no tenia como declararla, y
 * los adapters no la pasaban.
 */
class ProfileToEnginesSpec : DescribeSpec({

    val perfil = ProfileMapper.map(ProfileExamples.ELENA)
        .shouldBeInstanceOf<ProfileMapping.Accepted>().profile

    describe("de noche, la baranda baja llega hasta el motor") {

        val policy = ProfileProjection.project(perfil, "night").value

        it("Scene recibe el debounce del sensor") {
            val scene = policy.toSceneCalibration()
            scene.sceneHysteresisFor(SceneState.BED_LEFT) shouldBe Duration.ofSeconds(3)
        }

        it("Scene recibe el plazo, que es lo que hace calculable 'lleva un minuto abajo'") {
            val scene = policy.toSceneCalibration()
            scene.sceneDwellFor(SceneState.BED_LEFT)!!.exceeded shouldBe Duration.ofMinutes(1)
        }

        it("Sentinel recibe la regla, con gravedad y condicion de cierre") {
            val sentinel = policy.toSentinelCalibration()
            val regla = sentinel.sceneStateRuleFor(SceneState.BED_LEFT)!!
            regla.state shouldBe "DOWN"
            regla.severity shouldBe Severity.HIGH
            regla.closureCondition shouldBe ClosureCondition.STAFF_AND_SAFE
        }

        it("y la regla entra en la huella de Sentinel") {
            // Si no entrara, dos calibraciones que difieren solo en la baranda
            // serian indistinguibles.
            val conBaranda = policy.toSentinelCalibration()
            val sinBaranda = ProfileProjection.project(perfil).value.toSentinelCalibration()
            (conBaranda.fingerprint == sinBaranda.fingerprint) shouldBe false
        }

        it("la silla fuera de alcance tambien viaja") {
            val scene = policy.toSceneCalibration()
            scene.sceneDwellFor(SceneState.WHEELCHAIR)!!.exceeded shouldBe Duration.ofMinutes(5)
        }
    }

    describe("de dia la baranda no alerta, y eso tambien llega") {
        it("Sentinel no recibe regla de baranda fuera de la ventana de noche") {
            ProfileProjection.project(perfil).value
                .toSentinelCalibration()
                .sceneStateRuleFor(SceneState.BED_LEFT).shouldBeNull()
        }
    }

    describe("la notificacion sale del perfil y no del adapter") {

        val harbor = ProfileProjection.project(perfil, "night").value.toHarborCalibration()

        it("HIGH avisa por los canales que el perfil declaro") {
            // Antes los canales por severidad estaban cableados aca adentro: la
            // politica de notificacion no estaba en la politica, y nadie que no
            // leyera Kotlin podia saber a quien se le avisa.
            harbor.channelsFor(Severity.HIGH) shouldBe
                setOf(com.manahive.contracts.common.Channel.PUSH, com.manahive.contracts.common.Channel.TABLET)
        }

        it("CRITICAL suma el tablero de sala, porque una regla del perfil lo pide") {
            harbor.channelsFor(Severity.CRITICAL) shouldBe setOf(
                com.manahive.contracts.common.Channel.PUSH,
                com.manahive.contracts.common.Channel.TABLET,
                com.manahive.contracts.common.Channel.WARD_BOARD,
            )
        }
    }

    describe("las cuatro calibraciones salen de un solo documento") {
        it("y todas citan al mismo residente") {
            val policy = ProfileProjection.project(perfil, "night").value
            policy.toSceneCalibration()
            policy.toSentinelCalibration().residentId.value shouldBe "elena"
            policy.toHarborCalibration()
            policy.toRecordingCalibration(BedId("301"), MonitorId("mon-1"))
        }
    }
})
