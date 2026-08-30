package com.manahive.politica.profile

import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.RecordQuality
import com.manahive.contracts.policy.Severity
import com.manahive.profile.api.Channel
import com.manahive.profile.api.Closure
import com.manahive.profile.api.ProfileExamples
import com.manahive.profile.api.Quality
import com.manahive.profile.api.RuleDto
import com.manahive.profile.api.StateRuleDto
import com.manahive.profile.api.SubjectDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import com.manahive.profile.api.Severity as WireSeverity

private val ELENA = ProfileExamples.ELENA

class ProfileMapperSpec : DescribeSpec({

    describe("el perfil canonico cruza la frontera") {

        val mapped = ProfileMapper.map(ELENA).shouldBeInstanceOf<ProfileMapping.Accepted>().profile

        it("conserva la identidad de la version") {
            mapped.residentId.value shouldBe "elena"
            mapped.version shouldBe 8
            mapped.supersedes shouldBe 7
            mapped.validFrom shouldBe Instant.parse("2026-08-29T22:00:00Z")
        }

        it("la plantilla llega como procedencia y nada mas") {
            mapped.provenance.template?.value shouldBe "FALL_RISK"
            mapped.provenance.templateVersion shouldBe "2.1.0"
            mapped.provenance.reason shouldBe
                "Post-caida del 27/8: adelanto el aviso de bano y exijo barandas."
        }

        it("las duraciones ISO-8601 llegan sin perder la fraccion") {
            val posture = mapped.aspect("resident", "posture")!!
            posture.transitions.first { it.to == "BED_EDGE" }.stableFor shouldBe
                Duration.ofMillis(1500)
        }

        it("las ventanas horarias cruzan medianoche") {
            val night = mapped.windows.single()
            night.from shouldBe LocalTime.of(22, 0)
            night.to shouldBe LocalTime.of(7, 0)
            night.crossesMidnight shouldBe true
            night.isActiveAt(LocalTime.of(3, 0)) shouldBe true
            night.isActiveAt(LocalTime.of(12, 0)) shouldBe false
        }

        it("la identidad de los estados sigue siendo abierta del otro lado") {
            mapped.aspect("bed", "left")!!.states.keys shouldContain "DOWN"
            mapped.aspect("wheelchair", "presence")!!.states.keys shouldContain "OUT_OF_REACH"
        }
    }

    describe("los dos lados de la frontera nombran lo mismo") {

        // Este bloque existe porque la deriva ya paso: el dominio gano HIGH y el
        // contrato publicado se quedo en tres niveles, asi que un perfil que el
        // director podia escribir en la pantalla no se podia expresar en el jar.
        // El mapper traduce por nombre, de modo que una divergencia no es un
        // detalle estetico: es un perfil valido que se rechaza.

        it("las severidades") {
            WireSeverity.ALL shouldContainExactlyInAnyOrder Severity.entries.map { it.name }
        }

        it("el orden de gravedad, que decide si un evento eleva un episodio") {
            Severity.entries.forEach { WireSeverity.rank(it.name) shouldBe it.rank }
        }

        it("quien tiene que ir a la habitacion") {
            Severity.entries.forEach {
                WireSeverity.requiresAttendance(it.name) shouldBe it.requiresAttendance
            }
        }

        it("las condiciones de cierre") {
            Closure.ALL shouldContainExactlyInAnyOrder ClosureCondition.entries.map { it.name }
        }

        it("los canales") {
            Channel.ALL shouldContainExactlyInAnyOrder
                com.manahive.contracts.common.Channel.entries.map { it.name }
        }

        it("las calidades de grabacion") {
            Quality.ALL shouldContainExactlyInAnyOrder RecordQuality.entries.map { it.name }
        }
    }

    describe("un perfil que no cierra se rechaza entero") {

        it("devuelve todos los problemas, no el primero") {
            val roto = ELENA.copy(
                version = 3, // supersedes = 7 ya no puede preceder
                provenance = ELENA.provenance.copy(reason = "  ", authoredAt = "ayer"),
                validFrom = "cuando sea",
            )
            val problems = ProfileMapper.map(roto)
                .shouldBeInstanceOf<ProfileMapping.Rejected>().problems

            problems.map { it.path } shouldContainExactlyInAnyOrder listOf(
                "supersedes",
                "provenance.reason",
                "validFrom",
                "provenance.authoredAt",
            )
        }

        it("una severidad que no existe de este lado se rechaza con su ruta, no tira") {
            val problems = ProfileMapper.map(conRegla(RuleDto(alertAfter = "PT5M", severity = "URGENTISIMO")))
                .shouldBeInstanceOf<ProfileMapping.Rejected>().problems

            problems.map { it.path } shouldContain
                "subjects.resident.aspects.location.states.IN_BATHROOM.dwell[0].severity"
        }

        it("una duracion que no es ISO-8601 se rechaza con su ruta") {
            val problems = ProfileMapper.map(conRegla(RuleDto(alertAfter = "5 minutos")))
                .shouldBeInstanceOf<ProfileMapping.Rejected>().problems

            problems.map { it.path } shouldContain
                "subjects.resident.aspects.location.states.IN_BATHROOM.dwell[0].alertAfter"
        }

        it("un perfil a medias no se acepta a medias") {
            // La unica alternativa a rechazar entero es aplicar la parte que
            // cierra, y eso es volver al modelo de parches por la puerta de atras.
            ProfileMapper.map(conRegla(RuleDto(alertAfter = "PT5M", closure = "CUANDO_SE_PUEDA")))
                .shouldBeInstanceOf<ProfileMapping.Rejected>()
        }
    }
})

/** El perfil canonico con la regla de bano reemplazada. */
private fun conRegla(rule: RuleDto) = ELENA.copy(
    subjects = ELENA.subjects + ("resident" to SubjectDto(
        kind = ELENA.subjects.getValue("resident").kind,
        aspects = ELENA.subjects.getValue("resident").aspects + ("location" to
            ELENA.subjects.getValue("resident").aspects.getValue("location").copy(
                states = mapOf("IN_BATHROOM" to StateRuleDto(dwell = listOf(rule))),
            )),
    )),
)
