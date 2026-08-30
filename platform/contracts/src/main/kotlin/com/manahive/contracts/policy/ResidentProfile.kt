package com.manahive.contracts.policy

import com.manahive.contracts.common.Channel
import com.manahive.kernel.ResidentId
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * El cuidado de un residente, completo y en una sola pieza.
 *
 * Reemplaza a [AlarmProfile] + [PolicyOverride]. La diferencia no es de forma
 * sino de naturaleza: aquel era una plantilla mas una bolsa de parches que habia
 * que resolver por precedencia; este es un documento. Llega entero, pisa al
 * anterior, y no hay nada que mergear.
 *
 * ## Por que un documento y no capas
 *
 * Con capas, para saber que rige sobre un residente hay que *ejecutar* el
 * resolver, y nadie puede abrir una pantalla y leer su cuidado. Con capas, un
 * parche puede cambiar un numero pero no puede borrar una regla. Y con capas,
 * un parche entra por la ventana y saltea las invariantes que el DSL protege:
 * asi convivian un umbral de permanencia en Scene y una regla de entrada en
 * Sentinel para el mismo estado.
 *
 * ## Inmutable, y por que importa
 *
 * Una version nueva no muta a la anterior: la sucede. La 7 sigue existiendo y
 * sigue siendo consultable. Es la unica forma de contestar "con que reglas se
 * decidio la noche del 27" sin reconstruir nada — y esa pregunta llega despues
 * de un incidente, que es cuando peor se reconstruye.
 *
 * ## La identidad de los estados es abierta
 *
 * [subjects], [Subject.aspects] y [Aspect.states] se indexan por nombre y no por
 * enum. Las observaciones las emite el edge server y llegan las tratemos o no:
 * un enum cerrado solo puede representar lo que se compilo, y lo que llegue
 * nuevo se fuerza contra el vecino mas parecido o se descarta en silencio. Un
 * estado que llega y el perfil no menciona se observa y no alerta, que es
 * exactamente lo que significa un bloque vacio en el DSL.
 */
public data class ResidentProfile(
    public val residentId: ResidentId,
    public val version: Int,
    /** La version que reemplaza. Null solo en la primera. */
    public val supersedes: Int?,
    public val validFrom: Instant,
    public val provenance: Provenance,
    /** Ventanas horarias con nombre. [ALWAYS] esta siempre implicita. */
    public val windows: List<PolicyWindow> = emptyList(),
    public val subjects: Map<String, Subject> = emptyMap(),
) {
    init {
        require(version >= 1) { "version debe ser >= 1, es $version" }
        require(supersedes == null || supersedes < version) {
            "una version solo puede suceder a una anterior: $supersedes >= $version"
        }
    }

    /** El aspecto en [subject]/[aspect], o null si no esta declarado. */
    public fun aspect(subject: String, aspect: String): Aspect? =
        subjects[subject]?.aspects?.get(aspect)

    /** Recorre todos los aspectos del perfil con su sujeto. */
    public fun eachAspect(): Sequence<Triple<String, String, Aspect>> = sequence {
        subjects.forEach { (subjectName, subject) ->
            subject.aspects.forEach { (aspectName, aspect) ->
                yield(Triple(subjectName, aspectName, aspect))
            }
        }
    }

    public companion object {
        /** El sujeto que es el residente mismo. */
        public const val RESIDENT: String = "resident"

        /** La ventana implicita: rige siempre, no hace falta declararla. */
        public const val ALWAYS: String = "always"
    }
}

/**
 * De donde nacio este perfil y quien lo firmo.
 *
 * La plantilla es **procedencia, no resolucion**: no se aplica ninguna regla a
 * partir de ella. Se guarda para poder decir "esto nacio de FALL_RISK 2.1.0 y
 * despues el director lo edito", que es una pregunta de gestion legitima y hoy
 * no tiene respuesta.
 *
 * [reason] no puede estar vacio. El hub ya lo exige, con un mensaje que vale la
 * pena conservar: un cambio de vigilancia que nadie puede explicar.
 */
public data class Provenance(
    public val template: TemplateId?,
    public val templateVersion: String?,
    public val authoredBy: String,
    public val authoredAt: Instant,
    public val reason: String,
) {
    init {
        require(reason.isNotBlank()) {
            "un cambio de vigilancia que nadie puede explicar: el motivo es obligatorio"
        }
    }
}

/**
 * Una ventana horaria con nombre.
 *
 * Cruzar medianoche es lo normal, no un caso raro: la noche es el turno que este
 * sistema vigila, y `22:00`–`07:00` es la ventana tipica.
 *
 * Las ventanas **no son una capa de precedencia**. Cada regla declara a cual
 * pertenece, y la resolucion produce una calibracion por ventana que se emite en
 * el borde horario. A las 22:00 cambian las reglas y ese cambio queda en el log,
 * porque es un hecho clinico y no un detalle de implementacion.
 */
public data class PolicyWindow(
    public val id: String,
    public val from: LocalTime,
    public val to: LocalTime,
) {
    /** Si esta ventana cruza medianoche. */
    public val crossesMidnight: Boolean get() = to <= from

    public fun isActiveAt(time: LocalTime): Boolean =
        if (crossesMidnight) time >= from || time < to else time in from..<to
}

/**
 * Un sujeto con estado dentro del mundo del residente.
 *
 * Previstos: `resident`, `bed`, `wheelchair`, `walker`, `staff`. El mapa es
 * abierto a proposito: cuando el edge server empiece a emitir un sujeto nuevo se
 * declara en el perfil y el motor no cambia.
 */
public data class Subject(
    public val kind: AspectKind,
    public val aspects: Map<String, Aspect> = emptyMap(),
)

/**
 * Las dos clases de sujeto. La diferencia es una sola y esta acotada.
 *
 * - [DAG]: estados mutuamente excluyentes con transiciones legales entre ellos.
 *   La estabilidad vive **en la arista**: para creerle a un cambio de LYING a
 *   BED_EDGE hay que sostenerlo N milisegundos.
 *
 * - [FLAGS]: no hay precedente. La silla esta o no esta, y no viene "desde"
 *   ningun lado. La estabilidad vive **en el estado**: es un debounce.
 *
 * Todo lo demas —permanencia, severidad, cierre, notificacion, grabacion—
 * funciona igual en los dos.
 */
public enum class AspectKind { DAG, FLAGS }

/**
 * Un eje de estado dentro de un sujeto.
 *
 * Un sujeto puede tener varios aspectos **ortogonales**: la silla esta presente
 * *y* fuera de alcance a la vez; el residente tiene postura *y* ubicacion. Meter
 * dos ejes en un solo enum es el error que hoy hace que "parada en el bano" no se
 * pueda representar.
 *
 * @property unknownAfter si el aspecto lleva mas de esto sin observarse, avisar a
 *   mantenimiento. Es un problema tecnico y nunca abre un episodio de cuidado.
 * @property states los estados con regla. Uno que no figura aca se observa y no
 *   alerta.
 */
public data class Aspect(
    public val unknownAfter: Duration? = null,
    public val confidence: Map<String, Double> = emptyMap(),
    public val transitions: List<ProfileTransition> = emptyList(),
    public val states: Map<String, ProfileStateRule> = emptyMap(),
) {
    init {
        confidence.forEach { (state, c) ->
            require(c in 0.0..1.0) { "confianza de $state fuera de 0.0..1.0: $c" }
        }
    }
}

/**
 * Una transicion legal entre dos estados de un aspecto [AspectKind.DAG].
 *
 * @property stableFor histeresis: cuanto hay que sostener el destino antes de
 *   creerle al cambio. Evita el flapping por ruido del sensor.
 */
public data class ProfileTransition(
    public val from: String,
    public val to: String,
    public val stableFor: Duration,
    public val record: RecordWindow? = null,
)

/**
 * Que hace el sistema mientras un aspecto esta en un estado determinado.
 *
 * Las tres formas de vigilar son **excluyentes por razon clinica**: se vigila por
 * el instante de entrar, o por cuanto lleva adentro, o por cuanto lleva sin
 * volver. Nunca por dos a la vez. Esta invariante la protegia el DSL con un
 * `require`, pero los overrides la salteaban escribiendo directo en el mapa
 * resuelto; aca la unica puerta de entrada es esta.
 *
 * @property closesEpisodes entrar a este estado cierra los episodios abiertos.
 *   Lo usa `staff.presence.PRESENT`, y es lo que le da referente real a las
 *   condiciones de cierre que mencionan al personal: sin esto el sistema promete
 *   "cierra cuando llega el staff" sin tener forma de saber que llego.
 */
public data class ProfileStateRule(
    public val observeOnly: Boolean = false,
    public val closesEpisodes: Boolean = false,
    /** Debounce, para aspectos [AspectKind.FLAGS]. */
    public val stableFor: Duration? = null,
    public val onEntry: List<ProfileRule> = emptyList(),
    public val dwell: List<ProfileRule> = emptyList(),
    public val comeBack: List<ProfileRule> = emptyList(),
) {
    init {
        val formas = listOfNotNull(
            "onEntry".takeIf { onEntry.isNotEmpty() },
            "dwell".takeIf { dwell.isNotEmpty() },
            "comeBack".takeIf { comeBack.isNotEmpty() },
        )
        require(formas.size <= 1) {
            "excluyentes: un estado se vigila por entrada, por permanencia o por " +
                "no-retorno, nunca por varias a la vez. Declaradas: $formas"
        }
        require(!(observeOnly && formas.isNotEmpty())) {
            "observeOnly con reglas declaradas: $formas"
        }
    }

    /** Si este estado no alerta nunca. */
    public val silent: Boolean
        get() = observeOnly || (onEntry.isEmpty() && dwell.isEmpty() && comeBack.isEmpty())
}

/**
 * Una regla vigente durante una ventana horaria.
 *
 * Una regla mira **un solo estado**, a proposito. La composicion no ocurre aca:
 * ocurre en el episodio. Cuando pasan varias cosas juntas —y de noche pasan— la
 * severidad decide sola: algo de nivel menor entra al episodio abierto como
 * neutro y no notifica, algo del mismo nivel ya es parte de ese episodio, algo
 * mayor lo eleva. Por eso no hacen falta condiciones cruzadas.
 *
 * @property warningAfter preaviso. Si se omite, se deriva al 50% de [alertAfter].
 * @property alertAfter el plazo. Obligatorio en permanencia y no-retorno; en
 *   [ProfileStateRule.onEntry] no aplica, porque no hay plazo que esperar.
 * @property notify a quien avisar. Null significa que no se notifica: el evento
 *   queda registrado y visible, pero no despierta a nadie.
 */
public data class ProfileRule(
    public val window: String = ResidentProfile.ALWAYS,
    public val warningAfter: Duration? = null,
    public val alertAfter: Duration? = null,
    public val severity: Severity = Severity.WARNING,
    public val closure: ClosureCondition = ClosureCondition.STAFF_OR_SAFE,
    public val notify: NotifyRule? = null,
    public val record: RecordWindow? = null,
) {
    init {
        val w = warningAfter
        val a = alertAfter
        require(w == null || a == null || w < a) {
            "el preaviso tiene que llegar antes del plazo: $w >= $a"
        }
    }

    /** El umbral que esta regla define, o null si dispara al entrar. */
    public fun threshold(): DwellThreshold? =
        alertAfter?.let { DwellThreshold.of(warningAfter, it) }
}

/**
 * A quien avisar y con cuanta urgencia.
 *
 * Vive en la politica y no en el codigo. Hoy los canales por severidad estan
 * cableados en un adapter, o sea que la politica de notificacion no esta en la
 * politica: nadie que no lea Kotlin puede saber a quien se le avisa.
 */
public data class NotifyRule(
    public val channels: Set<Channel>,
    public val escalateAfter: Duration,
) {
    init {
        require(channels.isNotEmpty()) { "sin canales: omiti notify si no hay que avisar" }
    }
}

/** Ventana de video alrededor del momento en que la regla dispara. */
public data class RecordWindow(
    public val before: Duration,
    public val after: Duration,
    public val quality: RecordQuality = RecordQuality.HIGH,
)

public enum class RecordQuality { LOW, STANDARD, HIGH }
