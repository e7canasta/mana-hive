package com.manahive.profile.api

/**
 * Perfiles de ejemplo, ejecutables.
 *
 * Viven en el source principal y no en un test a proposito: los tests no se
 * publican en el jar, y el equipo que implementa el sistema de registro compila
 * contra el jar. Un ejemplo que solo existe de este lado obliga a copiar un JSON
 * de un documento, que es exactamente la forma en que un contrato se desactualiza.
 */
public object ProfileExamples {

    /**
     * El perfil de Elena, completo, tal como tiene que llegar.
     *
     * Es el ejemplo canonico del contrato: si alguien quiere saber que le tenemos
     * que recibir, lee esto. Ejercita las dos clases de sujeto, las tres formas de
     * vigilar un estado, las dos ventanas horarias, y el estado del personal que
     * cierra episodios.
     */
    public val ELENA: ResidentProfileDto = ResidentProfileDto(
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
                    // El nombre del aspecto no es libre: `sujeto.aspecto` es la
                    // identidad del campo que emite el gemelo, y `bed` + `left`
                    // da `bed.left`, que es exactamente `SceneState.BED_LEFT`.
                    // Una sola convencion en las dos puntas, sin traduccion.
                    "left" to AspectDto(
                        states = mapOf(
                            "DOWN" to StateRuleDto(
                                stableFor = "PT3S",
                                dwell = listOf(
                                    RuleDto(
                                        window = "night", alertAfter = "PT1M",
                                        severity = Severity.HIGH, closure = Closure.STAFF_AND_SAFE,
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
}
