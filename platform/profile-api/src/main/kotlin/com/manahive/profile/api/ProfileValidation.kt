package com.manahive.profile.api

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Un problema encontrado en un perfil, con la ruta exacta donde esta.
 *
 * La ruta importa: `subjects.bed.aspects.railLeft.states.DOWN.dwell[0]` le dice
 * al que implementa donde mirar. "Perfil invalido" no le dice nada.
 */
public data class ProfileProblem(
    public val path: String,
    public val message: String,
)

/**
 * Valida un perfil antes de aceptarlo.
 *
 * Existe de este lado a proposito: el equipo que implementa el sistema de
 * registro compila contra este jar y puede correr la misma validacion que vamos
 * a correr nosotros, en vez de descubrir en produccion que un campo no cierra.
 *
 * Devuelve **todos** los problemas, no el primero: quien esta armando un perfil
 * quiere la lista entera, no una carrera de un error por vez.
 */
public object ProfileValidation {

    public fun validate(profile: ResidentProfileDto): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()

        if (profile.residentId.isBlank()) {
            problems += ProfileProblem("residentId", "vacio")
        }
        if (profile.version < 1) {
            problems += ProfileProblem("version", "debe ser >= 1, es ${profile.version}")
        }
        if (profile.supersedes != null && profile.supersedes >= profile.version) {
            problems += ProfileProblem(
                "supersedes",
                "una version solo puede suceder a una anterior: ${profile.supersedes} >= ${profile.version}",
            )
        }
        if (profile.provenance.reason.isBlank()) {
            problems += ProfileProblem(
                "provenance.reason",
                "un cambio de vigilancia que nadie puede explicar: el motivo es obligatorio",
            )
        }

        val windowIds = mutableSetOf(ALWAYS)
        profile.windows.forEachIndexed { i, w ->
            val at = "windows[$i]"
            if (!windowIds.add(w.id)) {
                problems += ProfileProblem("$at.id", "ventana duplicada: ${w.id}")
            }
            problems += timeProblem("$at.from", w.from)
            problems += timeProblem("$at.to", w.to)
        }

        profile.subjects.forEach { (subjectName, subject) ->
            val at = "subjects.$subjectName"
            if (subject.kind !in setOf(SubjectKind.DAG, SubjectKind.FLAGS)) {
                problems += ProfileProblem(
                    "$at.kind",
                    "debe ser '${SubjectKind.DAG}' o '${SubjectKind.FLAGS}', es '${subject.kind}'",
                )
            }
            subject.aspects.forEach { (aspectName, aspect) ->
                problems += validateAspect("$at.aspects.$aspectName", subject, aspect, windowIds)
            }
        }

        return problems
    }

    private fun validateAspect(
        at: String,
        subject: SubjectDto,
        aspect: AspectDto,
        windowIds: Set<String>,
    ): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()

        if (!aspect.unknownIsInitial) {
            problems += ProfileProblem(
                "$at.unknownIsInitial",
                "un aspecto que arranca en un valor afirmado alerta antes de que ningun sensor haya mirado",
            )
        }
        aspect.unknownAfter?.let { problems += durationProblem("$at.unknownAfter", it) }

        aspect.confidence.forEach { (state, c) ->
            if (c !in 0.0..1.0) {
                problems += ProfileProblem("$at.confidence.$state", "debe estar entre 0.0 y 1.0, es $c")
            }
        }

        if (aspect.transitions.isNotEmpty() && subject.kind == SubjectKind.FLAGS) {
            problems += ProfileProblem(
                "$at.transitions",
                "un aspecto de flags no tiene transiciones: no hay precedente, el estado esta o no esta. " +
                    "La estabilidad va en states.<estado>.stableFor",
            )
        }
        aspect.transitions.forEachIndexed { i, t ->
            problems += durationProblem("$at.transitions[$i].stableFor", t.stableFor)
            t.record?.let { problems += validateRecord("$at.transitions[$i].record", it) }
        }

        aspect.states.forEach { (stateName, rule) ->
            problems += validateState("$at.states.$stateName", subject, rule, windowIds)
        }
        return problems
    }

    private fun validateState(
        at: String,
        subject: SubjectDto,
        rule: StateRuleDto,
        windowIds: Set<String>,
    ): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()

        // La invariante clinica: un estado se vigila por entrada, por permanencia
        // o por no-retorno, nunca por dos a la vez. Hoy el DSL la protege con un
        // require, pero los overrides entran por la ventana y la saltean; en el
        // modelo nuevo la unica puerta es esta.
        val formas = listOfNotNull(
            "onEntry".takeIf { rule.onEntry.isNotEmpty() },
            "dwell".takeIf { rule.dwell.isNotEmpty() },
            "comeBack".takeIf { rule.comeBack.isNotEmpty() },
        )
        if (formas.size > 1) {
            problems += ProfileProblem(
                at,
                "excluyentes: un estado se vigila por entrada, por permanencia o por no-retorno, " +
                    "nunca por varias a la vez. Declaradas: $formas",
            )
        }
        if (rule.observeOnly && formas.isNotEmpty()) {
            problems += ProfileProblem(at, "observeOnly con reglas declaradas: $formas")
        }

        if (subject.kind == SubjectKind.FLAGS && rule.comeBack.isNotEmpty()) {
            problems += ProfileProblem(
                "$at.comeBack",
                "redundante en un aspecto de flags: 'no volvio a X' es lo mismo que 'estuvo en el otro valor'. " +
                    "Usa dwell sobre el estado complementario",
            )
        }
        rule.stableFor?.let { problems += durationProblem("$at.stableFor", it) }

        rule.onEntry.forEachIndexed { i, r ->
            problems += validateRule("$at.onEntry[$i]", r, windowIds, requiereAlertAfter = false)
            if (r.alertAfter != null) {
                problems += ProfileProblem(
                    "$at.onEntry[$i].alertAfter",
                    "una regla de entrada dispara en el instante de entrar: no hay plazo que esperar",
                )
            }
        }
        rule.dwell.forEachIndexed { i, r ->
            problems += validateRule("$at.dwell[$i]", r, windowIds, requiereAlertAfter = true)
        }
        rule.comeBack.forEachIndexed { i, r ->
            problems += validateRule("$at.comeBack[$i]", r, windowIds, requiereAlertAfter = true)
        }

        val ventanas = (rule.onEntry + rule.dwell + rule.comeBack).map { it.window }
        ventanas.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { w ->
            problems += ProfileProblem(at, "dos reglas del mismo tipo compiten por la ventana '$w'")
        }
        return problems
    }

    private fun validateRule(
        at: String,
        rule: RuleDto,
        windowIds: Set<String>,
        requiereAlertAfter: Boolean,
    ): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()

        if (rule.window !in windowIds) {
            problems += ProfileProblem("$at.window", "ventana no declarada: '${rule.window}'")
        }
        if (rule.severity !in Severity.ALL) {
            problems += ProfileProblem("$at.severity", "desconocida: '${rule.severity}'. Validas: ${Severity.ALL}")
        }
        if (rule.closure !in Closure.ALL) {
            problems += ProfileProblem("$at.closure", "desconocida: '${rule.closure}'. Validas: ${Closure.ALL}")
        }

        val warning = rule.warningAfter?.let { parse("$at.warningAfter", it, problems) }
        val alert = rule.alertAfter?.let { parse("$at.alertAfter", it, problems) }

        if (requiereAlertAfter && rule.alertAfter == null) {
            problems += ProfileProblem("$at.alertAfter", "obligatorio: una regla por tiempo necesita su plazo")
        }
        if (warning != null && alert != null && warning >= alert) {
            problems += ProfileProblem(
                "$at.warningAfter",
                "el preaviso tiene que llegar antes del plazo: $warning >= $alert",
            )
        }

        rule.notify?.let { n ->
            n.channels.forEach { c ->
                if (c !in Channel.ALL) {
                    problems += ProfileProblem("$at.notify.channels", "canal desconocido: '$c'. Validos: ${Channel.ALL}")
                }
            }
            if (n.channels.isEmpty()) {
                problems += ProfileProblem("$at.notify.channels", "vacio: omiti notify entero si no hay que avisar")
            }
            problems += durationProblem("$at.notify.escalateAfter", n.escalateAfter)
        }
        rule.record?.let { problems += validateRecord("$at.record", it) }
        return problems
    }

    private fun validateRecord(at: String, record: RecordDto): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()
        problems += durationProblem("$at.before", record.before)
        problems += durationProblem("$at.after", record.after)
        if (record.quality !in Quality.ALL) {
            problems += ProfileProblem("$at.quality", "desconocida: '${record.quality}'. Validas: ${Quality.ALL}")
        }
        return problems
    }

    private fun parse(at: String, raw: String, into: MutableList<ProfileProblem>): Duration? =
        try {
            Duration.parse(raw)
        } catch (e: DateTimeParseException) {
            into += ProfileProblem(at, "no es una duracion ISO-8601: '$raw'. Ejemplos: PT1.5S, PT30S, PT10M")
            null
        }

    private fun durationProblem(at: String, raw: String): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()
        parse(at, raw, problems)
        return problems
    }

    private fun timeProblem(at: String, raw: String): List<ProfileProblem> =
        try {
            LocalTime.parse(raw)
            emptyList()
        } catch (e: DateTimeParseException) {
            listOf(ProfileProblem(at, "no es una hora HH:mm: '$raw'"))
        }

    /** La ventana implicita: rige siempre, no hace falta declararla. */
    public const val ALWAYS: String = "always"
}
