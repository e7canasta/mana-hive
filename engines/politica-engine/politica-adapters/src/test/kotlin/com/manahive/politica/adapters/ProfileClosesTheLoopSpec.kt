package com.manahive.politica.adapters

import com.manahive.contracts.policy.RecordQuality
import com.manahive.contracts.scene.SceneState
import com.manahive.kernel.BedId
import com.manahive.kernel.MonitorId
import com.manahive.kernel.RuleId
import com.manahive.politica.profile.ProfileMapper
import com.manahive.politica.profile.ProfileMapping
import com.manahive.politica.profile.ProfileProjection
import com.manahive.profile.api.ProfileExamples
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration

/**
 * Los tres huecos que quedaban abiertos, cerrados.
 *
 * Eran los tres de la misma familia: **el perfil lo decia y el motor no lo
 * escuchaba**. El cierre por presencia del personal estaba cableado en el
 * evaluador, la ventana de video se inventaba en el adapter, y el preaviso de un
 * campo era un no-op.
 */
class ProfileClosesTheLoopSpec : DescribeSpec({

    val perfil = ProfileMapper.map(ProfileExamples.ELENA)
        .shouldBeInstanceOf<ProfileMapping.Accepted>().profile

    describe("el perfil gobierna quien cierra un episodio") {

        val policy = ProfileProjection.project(perfil, "night").value

        it("el estado del personal viaja como estado que cierra") {
            policy.sentinel.closingStates shouldContain "staff.presence.PRESENT"
        }

        it("y llega al motor con su identidad completa") {
            val sentinel = policy.toSentinelCalibration()
            sentinel.closesEpisodes("staff.presence", "PRESENT") shouldBe true
        }

        it("un estado que el perfil no marco no cierra nada") {
            // Antes cerraba porque el codigo decia que cerraba. Ahora cierra
            // porque el documento lo dice, y solo lo que el documento dice.
            val sentinel = policy.toSentinelCalibration()
            sentinel.closesEpisodes("staff.presence", "ABSENT") shouldBe false
            sentinel.closesEpisodes("bed.left", "UP") shouldBe false
        }
    }

    describe("la ventana de video la pide la regla, no la inventa el adapter") {

        val policy = ProfileProjection.project(perfil).value

        it("el borde de la cama pide 30s antes y 2m despues, en alta") {
            val ventana = policy.recorder.ruleWindows.getValue(RuleId("alert-bed_edge"))
            ventana.before shouldBe Duration.ofSeconds(30)
            ventana.after shouldBe Duration.ofMinutes(2)
            ventana.quality shouldBe RecordQuality.HIGH
        }

        it("y el recorder recibe esa ventana, no una constante") {
            val rec = policy.toRecordingCalibration(BedId("301"), MonitorId("mon-1"))
            val regla = rec.rules.single { it.id == "rec-alert-bed_edge" }
            regla.recordingWindow.before shouldBe Duration.ofSeconds(30)
            regla.recordingWindow.after shouldBe Duration.ofMinutes(2)
            // La calidad sale del perfil: HIGH -> FULL. Antes se deducia de la
            // severidad, o sea que la politica de video no estaba en la politica.
            regla.quality.name shouldBe "FULL"
        }

        it("una regla que no pide video no produce orden de grabar de campo") {
            // La baranda de Elena no declara `record`: requiresNvr false y sin
            // ventana. Media orden —"grabá" sin cuánto— era lo que había antes.
            ProfileProjection.project(perfil, "night").value
                .sentinel.sceneStateRules.getValue("bed.left").requiresNvr shouldBe false
        }
    }

    describe("lo que el perfil todavia dice y nadie escucha") {

        it("queda una sola cosa, y es una capacidad que no existe") {
            // `unknownAfter` no es un hueco de transporte: es avisarle a
            // mantenimiento que un sensor lleva media hora mudo, y no hay canal
            // de mantenimiento. Inventarle un slot que nadie lee seria construir
            // otro stub como el que acabamos de sacar.
            ProfileProjection.unrepresentable(perfil).map { it.path } shouldBe
                listOf("subjects.wheelchair.aspects.presence.unknownAfter")
        }

        it("y de noche tampoco aparece nada nuevo") {
            ProfileProjection.unrepresentable(perfil, "night").map { it.path } shouldBe
                listOf("subjects.wheelchair.aspects.presence.unknownAfter")
        }
    }

    describe("el perfil de Elena no pierde nada mas") {
        it("la explicacion ya no lista un renglon de intransportables largo") {
            val pasos = ProfileProjection.project(perfil, "night").explanation
            pasos.single { it.rule == "unrepresentable" }
                .observed shouldBe "1 cosas que el perfil dice y la calibracion no transporta"
        }
    }

    describe("el personal se sigue observando y no alerta") {
        it("cerrar no es alertar") {
            ProfileProjection.project(perfil, "night").value
                .sentinel.sceneStateRules.keys.contains("staff.presence") shouldBe false
        }
    }

    describe("nada de esto rompio el resto") {
        it("la baranda sigue llegando con su regla") {
            ProfileProjection.project(perfil, "night").value
                .toSentinelCalibration()
                .sceneStateRuleFor(SceneState.BED_LEFT)!!
                .state shouldBe "DOWN"
        }

        it("y de dia sigue sin alertar") {
            ProfileProjection.project(perfil).value
                .sentinel.sceneStateRules.keys.filter { it == "bed.left" }.shouldBeEmpty()
        }
    }
})
