package com.manahive.politica.profile

import com.manahive.contracts.common.Channel
import com.manahive.contracts.policy.Aspect
import com.manahive.contracts.policy.AspectKind
import com.manahive.contracts.policy.ClosureCondition
import com.manahive.contracts.policy.NotifyRule
import com.manahive.contracts.policy.PolicyWindow
import com.manahive.contracts.policy.ProfileRule
import com.manahive.contracts.policy.ProfileStateRule
import com.manahive.contracts.policy.ProfileTransition
import com.manahive.contracts.policy.Provenance
import com.manahive.contracts.policy.RecordQuality
import com.manahive.contracts.policy.RecordWindow
import com.manahive.contracts.policy.ResidentProfile
import com.manahive.contracts.policy.Severity
import com.manahive.contracts.policy.Subject
import com.manahive.contracts.policy.TemplateId
import com.manahive.kernel.ResidentId
import com.manahive.profile.api.AspectDto
import com.manahive.profile.api.NotifyDto
import com.manahive.profile.api.ProfileProblem
import com.manahive.profile.api.ProfileValidation
import com.manahive.profile.api.RecordDto
import com.manahive.profile.api.ResidentProfileDto
import com.manahive.profile.api.RuleDto
import com.manahive.profile.api.StateRuleDto
import com.manahive.profile.api.SubjectDto
import com.manahive.profile.api.SubjectKind
import com.manahive.profile.api.TransitionDto
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * El resultado de intentar traducir un perfil que llego de afuera.
 *
 * Es un tipo y no una excepcion porque un perfil malo **no es un error del
 * programa**: es un dato que llego mal, y quien lo mando merece la lista entera
 * de lo que tiene que corregir, con la ruta exacta de cada problema. Una
 * excepcion contesta el primero y esconde los otros seis.
 */
public sealed interface ProfileMapping {

    /** El perfil valido, ya en el idioma del dominio. */
    public data class Accepted(public val profile: ResidentProfile) : ProfileMapping

    /**
     * El perfil no se acepta, y estos son **todos** los motivos.
     *
     * Nada se aplica parcialmente: un perfil se toma entero o no se toma. Aceptar
     * la mitad seria reintroducir por la puerta de atras el modelo de parches
     * que este rediseno vino a sacar.
     */
    public data class Rejected(public val problems: List<ProfileProblem>) : ProfileMapping {
        init { require(problems.isNotEmpty()) { "un rechazo sin motivo no es un rechazo" } }
    }
}

/**
 * Traduce el perfil de frontera al perfil de dominio.
 *
 * ## Por que hay dos tipos y no uno
 *
 * [ResidentProfileDto] esta hecho de primitivos a proposito: quien lo implementa
 * del otro lado no tiene por que conocer nuestro kernel. [ResidentProfile] esta
 * hecho de tipos con invariantes. El mapper es el unico lugar donde conviven, y
 * por eso es el unico lugar donde puede fallar una traduccion.
 *
 * ## Valida antes de construir
 *
 * Los tipos del dominio se defienden con `require`, o sea que construirlos con
 * datos malos **tira**. Por eso [map] corre [ProfileValidation] primero y no
 * construye nada si hay problemas: el que valida es el mismo codigo que corre el
 * equipo externo contra el jar, asi que un perfil que paso alla pasa aca.
 *
 * Lo que la validacion no cubre —que un nombre de severidad exista de este
 * lado— se reporta como un problema mas, con su ruta. Nunca como excepcion: si
 * los dos lados divergieran, el sintoma tiene que ser un perfil rechazado con
 * una explicacion, no un motor que se cae a las tres de la manana.
 */
public object ProfileMapper {

    public fun map(dto: ResidentProfileDto): ProfileMapping {
        val problems = ProfileValidation.validate(dto).toMutableList()

        // El id del residente no lo mira la validacion de frontera con nuestro
        // criterio: ResidentId exige no-blanco y tirar aca seria una excepcion
        // por un dato, justo lo que este tipo evita.
        if (dto.residentId.isBlank()) {
            problems += ProfileProblem("residentId", "vacio")
        }

        val validFrom = dto.validFrom.asInstant("validFrom", problems)
        val authoredAt = dto.provenance?.authoredAt?.asInstant("provenance.authoredAt", problems)

        if (dto.provenance?.authoredBy.isNullOrBlank()) {
            problems += ProfileProblem("provenance.authoredBy", "vacio: un perfil lo firma alguien")
        }

        val windows = dto.windows.mapIndexedNotNull { i, w ->
            val from = w.from.asLocalTime("windows[$i].from", problems)
            val to = w.to.asLocalTime("windows[$i].to", problems)
            if (from == null || to == null) null else PolicyWindow(w.id, from, to)
        }

        val subjects = dto.subjects.mapValues { (name, subject) ->
            mapSubject("subjects.$name", subject, problems)
        }

        if (problems.isNotEmpty()) return ProfileMapping.Rejected(problems)

        return ProfileMapping.Accepted(
            ResidentProfile(
                residentId = ResidentId(dto.residentId),
                version = dto.version,
                supersedes = dto.supersedes,
                validFrom = validFrom!!,
                provenance = Provenance(
                    template = dto.provenance?.template?.let(::TemplateId),
                    templateVersion = dto.provenance?.templateVersion,
                    authoredBy = dto.provenance?.authoredBy ?: "unknown",
                    authoredAt = authoredAt!!,
                    reason = dto.provenance?.reason ?: "sin motivo",
                ),
                windows = windows,
                subjects = subjects,
            ),
        )
    }

    private fun mapSubject(
        at: String,
        dto: SubjectDto,
        problems: MutableList<ProfileProblem>,
    ): Subject = Subject(
        kind = when (dto.kind) {
            SubjectKind.DAG -> AspectKind.DAG
            SubjectKind.FLAGS -> AspectKind.FLAGS
            else -> {
                problems += ProfileProblem(
                    "$at.kind",
                    "debe ser '${SubjectKind.DAG}' o '${SubjectKind.FLAGS}', es '${dto.kind}'",
                )
                AspectKind.FLAGS
            }
        },
        aspects = dto.aspects.mapValues { (name, aspect) ->
            mapAspect("$at.aspects.$name", aspect, problems)
        },
    )

    /**
     * `unknownIsInitial` no viaja al dominio: la validacion exige que sea `true`,
     * asi que del otro lado del mapper es una constante y no un campo. Un campo
     * que solo puede tener un valor invita a que alguien, algun dia, le ponga el
     * otro.
     */
    private fun mapAspect(
        at: String,
        dto: AspectDto,
        problems: MutableList<ProfileProblem>,
    ): Aspect = Aspect(
        unknownAfter = dto.unknownAfter?.asDuration("$at.unknownAfter", problems),
        confidence = dto.confidence,
        transitions = dto.transitions.mapIndexedNotNull { i, t ->
            mapTransition("$at.transitions[$i]", t, problems)
        },
        states = dto.states.mapValues { (name, rule) ->
            mapState("$at.states.$name", rule, problems)
        },
    )

    private fun mapTransition(
        at: String,
        dto: TransitionDto,
        problems: MutableList<ProfileProblem>,
    ): ProfileTransition? {
        val stableFor = dto.stableFor.asDuration("$at.stableFor", problems) ?: return null
        return ProfileTransition(
            from = dto.from,
            to = dto.to,
            stableFor = stableFor,
            record = dto.record?.let { mapRecord("$at.record", it, problems) },
        )
    }

    private fun mapState(
        at: String,
        dto: StateRuleDto,
        problems: MutableList<ProfileProblem>,
    ): ProfileStateRule {
        val onEntry = dto.onEntry.mapIndexed { i, r -> mapRule("$at.onEntry[$i]", r, problems) }
        val dwell = dto.dwell.mapIndexed { i, r -> mapRule("$at.dwell[$i]", r, problems) }
        val comeBack = dto.comeBack.mapIndexed { i, r -> mapRule("$at.comeBack[$i]", r, problems) }

        // ProfileStateRule se defiende con un require sobre la exclusividad de
        // las tres formas de vigilar. La validacion de frontera ya la reporto
        // con su ruta; construir igual aca solo cambiaria ese reporte por un
        // stack trace.
        val excluyentes = listOf(onEntry, dwell, comeBack).count { it.isNotEmpty() } > 1
        if (excluyentes || (dto.observeOnly && (onEntry + dwell + comeBack).isNotEmpty())) {
            return ProfileStateRule(observeOnly = dto.observeOnly, closesEpisodes = dto.closesEpisodes)
        }

        return ProfileStateRule(
            observeOnly = dto.observeOnly,
            closesEpisodes = dto.closesEpisodes,
            stableFor = dto.stableFor?.asDuration("$at.stableFor", problems),
            onEntry = onEntry,
            dwell = dwell,
            comeBack = comeBack,
        )
    }

    private fun mapRule(
        at: String,
        dto: RuleDto,
        problems: MutableList<ProfileProblem>,
    ): ProfileRule {
        val warningAfter = dto.warningAfter?.asDuration("$at.warningAfter", problems)
        val alertAfter = dto.alertAfter?.asDuration("$at.alertAfter", problems)

        // ProfileRule exige preaviso < plazo. La validacion ya lo reporto con su
        // ruta; si esta roto, se suelta el preaviso para poder seguir juntando
        // los problemas que falten en vez de cortar en el primero.
        val preavisoCoherente = warningAfter == null || alertAfter == null || warningAfter < alertAfter

        return ProfileRule(
            window = dto.window,
            warningAfter = warningAfter.takeIf { preavisoCoherente },
            alertAfter = alertAfter,
            severity = dto.severity.asEnum<Severity>("$at.severity", problems) ?: Severity.WARNING,
            closure = dto.closure.asEnum<ClosureCondition>("$at.closure", problems)
                ?: ClosureCondition.STAFF_OR_SAFE,
            notify = dto.notify?.let { mapNotify("$at.notify", it, problems) },
            record = dto.record?.let { mapRecord("$at.record", it, problems) },
        )
    }

    private fun mapNotify(
        at: String,
        dto: NotifyDto,
        problems: MutableList<ProfileProblem>,
    ): NotifyRule? {
        val channels = dto.channels.mapNotNull { it.asEnum<Channel>("$at.channels", problems) }.toSet()
        val escalateAfter = dto.escalateAfter.asDuration("$at.escalateAfter", problems)
        if (channels.isEmpty()) {
            problems += ProfileProblem("$at.channels", "vacio: omiti notify entero si no hay que avisar")
            return null
        }
        return escalateAfter?.let { NotifyRule(channels = channels, escalateAfter = it) }
    }

    private fun mapRecord(
        at: String,
        dto: RecordDto,
        problems: MutableList<ProfileProblem>,
    ): RecordWindow? {
        val before = dto.before.asDuration("$at.before", problems)
        val after = dto.after.asDuration("$at.after", problems)
        val quality = dto.quality.asEnum<RecordQuality>("$at.quality", problems)
        if (before == null || after == null || quality == null) return null
        return RecordWindow(before = before, after = after, quality = quality)
    }

    // ── parseo, siempre reportando en vez de tirar ───────────────────────────

    /**
     * El valor del enum que se llama igual que [this], o un problema con la ruta.
     *
     * Va por nombre y no por un `when` a mano porque los dos lados de la frontera
     * declaran los mismos nombres a proposito. Cuando divergen —y ya paso: el
     * dominio gano `HIGH` y el contrato publicado se quedo en tres niveles— el
     * sintoma correcto es un perfil rechazado que dice cual es el valor invalido
     * y cuales son los validos, no un `valueOf` que tira.
     */
    private inline fun <reified E : Enum<E>> String.asEnum(
        at: String,
        problems: MutableList<ProfileProblem>,
    ): E? {
        val match = enumValues<E>().firstOrNull { it.name == this }
        if (match == null) {
            problems += ProfileProblem(
                at,
                "desconocido de este lado: '$this'. Validos: ${enumValues<E>().map { e -> e.name }}",
            )
        }
        return match
    }

    private fun String.asDuration(at: String, problems: MutableList<ProfileProblem>): Duration? =
        try {
            Duration.parse(this)
        } catch (e: DateTimeParseException) {
            problems += ProfileProblem(
                at,
                "no es una duracion ISO-8601: '$this'. Ejemplos: PT1.5S, PT30S, PT10M",
            )
            null
        }

    private fun String.asInstant(at: String, problems: MutableList<ProfileProblem>): Instant? =
        try {
            Instant.parse(this)
        } catch (e: DateTimeParseException) {
            problems += ProfileProblem(at, "no es un instante ISO-8601: '$this'. Ejemplo: 2026-08-29T22:00:00Z")
            null
        }

    private fun String.asLocalTime(at: String, problems: MutableList<ProfileProblem>): LocalTime? =
        try {
            LocalTime.parse(this)
        } catch (e: DateTimeParseException) {
            problems += ProfileProblem(at, "no es una hora HH:mm: '$this'")
            null
        }
}
