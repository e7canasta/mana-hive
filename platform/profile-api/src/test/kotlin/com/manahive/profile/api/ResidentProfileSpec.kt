package com.manahive.profile.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * El contrato de frontera, ejercitado sobre el perfil canonico.
 *
 * El perfil vive en [ProfileExamples] y no aca: los tests no se publican en el
 * jar, y el equipo que implementa el sistema de registro compila contra el jar.
 */
private val ELENA = ProfileExamples.ELENA

class ResidentProfileSpec : DescribeSpec({

    describe("el perfil canonico") {
        it("valida limpio") {
            ProfileValidation.validate(ELENA).shouldBeEmpty()
        }

        it("el staff cierra episodios — le da referente real a STAFF_ONLY") {
            ELENA.subjects.getValue("staff")
                .aspects.getValue("presence")
                .states.getValue("PRESENT")
                .closesEpisodes shouldBe true
        }

        it("todo aspecto arranca en desconocido") {
            ELENA.subjects.values
                .flatMap { it.aspects.values }
                .all { it.unknownIsInitial } shouldBe true
        }
    }

    describe("las invariantes que el modelo viejo no podia proteger") {

        it("entrada y permanencia son excluyentes") {
            val roto = conEstado(
                "BED_EDGE",
                StateRuleDto(
                    onEntry = listOf(RuleDto(severity = Severity.CRITICAL)),
                    dwell = listOf(RuleDto(alertAfter = "PT5M")),
                ),
            )
            ProfileValidation.validate(roto).map { it.message }
                .any { it.startsWith("excluyentes") } shouldBe true
        }

        it("un aspecto no puede arrancar afirmando un estado que nadie observo") {
            val roto = ELENA.copy(
                subjects = ELENA.subjects + ("bed" to SubjectDto(
                    kind = SubjectKind.FLAGS,
                    aspects = mapOf("left" to AspectDto(unknownIsInitial = false)),
                )),
            )
            ProfileValidation.validate(roto).map { it.path }
                .shouldContain("subjects.bed.aspects.left.unknownIsInitial")
        }

        it("el preaviso no puede llegar despues del plazo") {
            val roto = conEstado(
                "STANDING",
                StateRuleDto(dwell = listOf(RuleDto(warningAfter = "PT10M", alertAfter = "PT5M"))),
            )
            ProfileValidation.validate(roto).map { it.path }
                .shouldContain("subjects.resident.aspects.posture.states.STANDING.dwell[0].warningAfter")
        }

        it("una regla de entrada no tiene plazo que esperar") {
            val roto = conEstado(
                "BED_EDGE",
                StateRuleDto(onEntry = listOf(RuleDto(alertAfter = "PT5M"))),
            )
            ProfileValidation.validate(roto).map { it.path }
                .shouldContain("subjects.resident.aspects.posture.states.BED_EDGE.onEntry[0].alertAfter")
        }

        it("una ventana sin declarar no rige") {
            val roto = conEstado(
                "STANDING",
                StateRuleDto(dwell = listOf(RuleDto(window = "siesta", alertAfter = "PT5M"))),
            )
            ProfileValidation.validate(roto).map { it.message }
                .any { it.contains("siesta") } shouldBe true
        }

        it("un perfil sin motivo no se acepta") {
            val roto = ELENA.copy(provenance = ELENA.provenance.copy(reason = "  "))
            ProfileValidation.validate(roto).map { it.path }.shouldContain("provenance.reason")
        }

        it("una version no puede suceder a una posterior") {
            ProfileValidation.validate(ELENA.copy(version = 3)).map { it.path }
                .shouldContain("supersedes")
        }
    }

    describe("las dos clases de aspecto") {

        it("un aspecto de flags no tiene transiciones") {
            val roto = ELENA.copy(
                subjects = ELENA.subjects + ("wheelchair" to SubjectDto(
                    kind = SubjectKind.FLAGS,
                    aspects = mapOf(
                        "presence" to AspectDto(
                            transitions = listOf(
                                TransitionDto("PRESENT", "OUT_OF_REACH", "PT1S"),
                            ),
                        ),
                    ),
                )),
            )
            ProfileValidation.validate(roto).map { it.message }
                .any { it.contains("no tiene transiciones") } shouldBe true
        }

        it("come-back es redundante en un aspecto binario") {
            val roto = ELENA.copy(
                subjects = ELENA.subjects + ("bed" to SubjectDto(
                    kind = SubjectKind.FLAGS,
                    aspects = mapOf(
                        "left" to AspectDto(
                            states = mapOf(
                                "UP" to StateRuleDto(comeBack = listOf(RuleDto(alertAfter = "PT1M"))),
                            ),
                        ),
                    ),
                )),
            )
            ProfileValidation.validate(roto).map { it.message }
                .any { it.contains("redundante") } shouldBe true
        }
    }

    describe("la severidad es el mecanismo de composicion") {
        it("ordena para decidir si un evento eleva el episodio abierto") {
            Severity.rank(Severity.CRITICAL) shouldBe 3
            (Severity.rank(Severity.INFO) < Severity.rank(Severity.CRITICAL)) shouldBe true
        }

        it("HIGH avisa al mismo turno que WARNING, pero pide que alguien vaya") {
            Severity.requiresAttendance(Severity.WARNING) shouldBe false
            Severity.requiresAttendance(Severity.HIGH) shouldBe true
        }

        it("una severidad que el contrato no conoce no se ordena") {
            Severity.rank("URGENTISIMO") shouldBe -1
        }
    }
})

/** El perfil canonico con un solo estado de postura reemplazado. */
private fun conEstado(state: String, rule: StateRuleDto): ResidentProfileDto {
    val resident = ELENA.subjects.getValue("resident")
    val posture = resident.aspects.getValue("posture")
    return ELENA.copy(
        subjects = ELENA.subjects + ("resident" to resident.copy(
            aspects = resident.aspects + ("posture" to posture.copy(
                states = posture.states + (state to rule),
            )),
        )),
    )
}
