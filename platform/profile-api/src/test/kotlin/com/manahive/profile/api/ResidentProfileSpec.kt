package com.manahive.profile.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * El perfil de Elena, completo, tal como tiene que llegar.
 *
 * Este spec es el ejemplo canonico del contrato: si alguien quiere saber que le
 * tenemos que recibir, lee esto y no un JSON suelto en un documento que se
 * desactualiza.
 */
private val ELENA = ResidentProfileDto(
    profileId = "elena@v8",
    residentId = "elena",
    version = 8,
    supersedes = 7,
    validFrom = "2026-08-29T22:00:00Z",
    provenance = ProvenanceDto(
        template = "FALL_RISK",
        templateVersion = "2.1.0",
        authoredBy = "dr-mendez",
        authoredAt = "2026-08-29T14:31:12Z",
        reason = "Post-caida del 27/8: adelanto el aviso de bano y exijo barandas.",
    ),
    windows = listOf(TimeWindowDto(id = "night", from = "22:00", to = "07:00")),
    subjects = mapOf(
        "resident" to SubjectDto(
            kind = SubjectKind.DAG,
            aspects = mapOf(
                "posture" to AspectDto(
                    confidence = mapOf("BED_EDGE" to 0.90, "STANDING" to 0.85),
                    transitions = listOf(
                        TransitionDto(
                            from = "LYING", to = "BED_EDGE", stableFor = "PT1.5S",
                            record = RecordDto(before = "PT30S", after = "PT2M"),
                        ),
                        TransitionDto(from = "BED_EDGE", to = "STANDING", stableFor = "PT1.5S"),
                    ),
                    states = mapOf(
                        "LYING" to StateRuleDto(
                            comeBack = listOf(
                                RuleDto(
                                    warningAfter = "PT10M", alertAfter = "PT20M",
                                    severity = Severity.CRITICAL, closure = Closure.STAFF_OR_SAFE,
                                    notify = NotifyDto(listOf(Channel.PUSH, Channel.TABLET), "PT5M"),
                                ),
                            ),
                        ),
                        "BED_EDGE" to StateRuleDto(
                            onEntry = listOf(
                                RuleDto(
                                    severity = Severity.CRITICAL, closure = Closure.STAFF_AND_SAFE,
                                    notify = NotifyDto(
                                        listOf(Channel.PUSH, Channel.TABLET, Channel.WARD_BOARD), "PT0S",
                                    ),
                                    record = RecordDto("PT30S", "PT2M", Quality.HIGH),
                                ),
                            ),
                        ),
                        "STANDING" to StateRuleDto(observeOnly = true),
                    ),
                ),
                "location" to AspectDto(
                    states = mapOf(
                        "IN_BATHROOM" to StateRuleDto(
                            dwell = listOf(
                                RuleDto(
                                    window = "always", warningAfter = "PT10M", alertAfter = "PT15M",
                                    severity = Severity.WARNING, closure = Closure.SAFE_ONLY,
                                ),
                                RuleDto(
                                    window = "night", warningAfter = "PT5M", alertAfter = "PT8M",
                                    severity = Severity.CRITICAL, closure = Closure.STAFF_OR_SAFE,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        "bed" to SubjectDto(
            kind = SubjectKind.FLAGS,
            aspects = mapOf(
                "railLeft" to AspectDto(
                    states = mapOf(
                        "DOWN" to StateRuleDto(
                            stableFor = "PT3S",
                            dwell = listOf(
                                RuleDto(
                                    window = "night", alertAfter = "PT1M",
                                    severity = Severity.CRITICAL, closure = Closure.STAFF_AND_SAFE,
                                    notify = NotifyDto(listOf(Channel.PUSH, Channel.TABLET), "PT2M"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        "wheelchair" to SubjectDto(
            kind = SubjectKind.FLAGS,
            aspects = mapOf(
                "presence" to AspectDto(
                    unknownAfter = "PT30M",
                    states = mapOf(
                        "OUT_OF_REACH" to StateRuleDto(
                            stableFor = "PT5S",
                            dwell = listOf(
                                RuleDto(
                                    warningAfter = "PT2M", alertAfter = "PT5M",
                                    severity = Severity.WARNING, closure = Closure.SAFE_ONLY,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        "staff" to SubjectDto(
            kind = SubjectKind.FLAGS,
            aspects = mapOf(
                "presence" to AspectDto(
                    states = mapOf("PRESENT" to StateRuleDto(observeOnly = true, closesEpisodes = true)),
                ),
            ),
        ),
    ),
)

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
                    aspects = mapOf("railLeft" to AspectDto(unknownIsInitial = false)),
                )),
            )
            ProfileValidation.validate(roto).map { it.path }
                .shouldContain("subjects.bed.aspects.railLeft.unknownIsInitial")
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
                        "railLeft" to AspectDto(
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
            Severity.rank(Severity.CRITICAL) shouldBe 2
            (Severity.rank(Severity.INFO) < Severity.rank(Severity.CRITICAL)) shouldBe true
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
