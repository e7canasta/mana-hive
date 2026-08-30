package com.manahive.profile.api

/**
 * El perfil completo de un residente, tal como lo entrega el sistema de registro.
 *
 * Este tipo es el contrato de frontera. Deliberadamente usa primitivos —String,
 * Int, Map— y no los value types del dominio: quien lo implementa del otro lado
 * no tiene por que conocer nuestro kernel, y una frontera que exige tipos
 * ajenos no es una frontera, es un acoplamiento.
 *
 * ## La regla que gobierna todo
 *
 * Cada version es **completa e inmutable**. No hay deltas, no hay parches, no
 * hay capas de precedencia. Llega un perfil, se pisa el anterior, se reinterpreta
 * todo. La version anterior sigue existiendo y sigue siendo consultable, porque
 * la pregunta del auditor —"con que reglas se decidio la noche del 27"— no se
 * puede contestar reconstruyendo.
 *
 * ## Duraciones
 *
 * Todas las duraciones son ISO-8601: `PT1.5S`, `PT30S`, `PT10M`, `PT2H`.
 * Sin ambiguedad de unidad, y `java.time.Duration.parse` las lee directo.
 *
 * @property profileId identificador de esta version concreta, p.ej. `elena@v8`
 * @property version entero monotonico por residente
 * @property supersedes la version que esta reemplaza; null solo en la primera
 * @property validFrom instante ISO-8601 desde el que rige esta version
 * @property windows ventanas horarias declaradas; ver [TimeWindowDto]
 * @property subjects los sujetos con estado de este residente, por nombre
 */
public data class ResidentProfileDto(
    public val profileId: String,
    public val residentId: String,
    public val version: Int,
    public val supersedes: Int?,
    public val validFrom: String,
    public val provenance: ProvenanceDto,
    public val windows: List<TimeWindowDto> = emptyList(),
    public val subjects: Map<String, SubjectDto> = emptyMap(),
)

/**
 * De donde nacio este perfil y quien lo firmo.
 *
 * La plantilla es **procedencia, no resolucion**: no se aplica ninguna regla a
 * partir de ella. Se guarda para poder decir "esto nacio de FALL_RISK 2.1.0 y
 * despues el director lo edito", que es una pregunta de gestion legitima.
 *
 * [reason] es obligatorio y no puede estar vacio. El hub ya lo exige hoy, con un
 * mensaje que vale la pena conservar: *un cambio de vigilancia que nadie puede
 * explicar*. Un perfil sin motivo es un perfil que nadie va a poder defender.
 */
public data class ProvenanceDto(
    public val template: String?,
    public val templateVersion: String?,
    public val authoredBy: String,
    public val authoredAt: String,
    public val reason: String,
)

/**
 * Una ventana horaria con nombre.
 *
 * Una ventana que cruza medianoche es lo normal, no un caso raro: la noche es el
 * turno que este sistema vigila. `from = "22:00"`, `to = "07:00"` es valido.
 *
 * Las ventanas **no son una capa de precedencia**. Cada regla declara a que
 * ventana pertenece, y el motor resuelve una calibracion por ventana y la emite
 * en el borde horario. A las 22:00 cambian las reglas, y ese cambio queda en el
 * log — porque es un hecho clinico, no un detalle de implementacion.
 *
 * La ventana `always` esta siempre implicita y no hace falta declararla.
 */
public data class TimeWindowDto(
    public val id: String,
    public val from: String,
    public val to: String,
)

/**
 * Un sujeto con estado dentro del mundo del residente.
 *
 * Los sujetos previstos son `resident`, `bed`, `wheelchair`, `walker` y `staff`,
 * pero el mapa es abierto a proposito: cuando el edge server empiece a emitir un
 * sujeto nuevo, se declara en el perfil y el motor no cambia.
 *
 * @property kind [SubjectKind.DAG] o [SubjectKind.FLAGS] — ver [SubjectKind]
 */
public data class SubjectDto(
    public val kind: String,
    public val aspects: Map<String, AspectDto> = emptyMap(),
)

/**
 * Las dos clases de sujeto. La diferencia es una sola y esta acotada.
 *
 * - [DAG]: sus estados son mutuamente excluyentes y hay transiciones legales
 *   entre ellos. La estabilidad vive **en la arista**: para creerle a un cambio
 *   de LYING a BED_EDGE hay que sostenerlo N milisegundos.
 *
 * - [FLAGS]: no hay precedente. La silla esta o no esta, y no viene "desde"
 *   ningun lado. La estabilidad vive **en el estado**: es un debounce.
 *
 * Todo lo demas —permanencia, severidad, cierre, notificacion, grabacion—
 * funciona igual en ambos.
 *
 * Nota: en un aspecto de dos valores, "no volvio a X" es lo mismo que "estuvo en
 * ¬X", asi que [StateRuleDto.comeBack] es redundante y no se usa. Come-back
 * existe porque un aspecto DAG tiene muchos valores y el complemento de uno no
 * es un estado.
 */
public object SubjectKind {
    public const val DAG: String = "dag"
    public const val FLAGS: String = "flags"
}

/**
 * Un eje de estado dentro de un sujeto.
 *
 * Un sujeto puede tener varios aspectos **ortogonales**: la silla esta presente
 * *y* fuera de alcance a la vez; el residente tiene postura *y* ubicacion. Meter
 * dos ejes en un solo enum es el error que hoy hace que "parada en el bano" no
 * se pueda representar.
 *
 * @property unknownIsInitial el aspecto arranca en desconocido y no en un valor
 *   afirmado. Debe ser `true`: un default que afirma "la baranda esta baja" sin
 *   que ningun sensor haya mirado es un falso positivo garantizado al arrancar.
 * @property unknownAfter si el aspecto lleva mas de esta duracion en desconocido,
 *   avisar a mantenimiento. Es un problema tecnico, no clinico: nunca abre un
 *   episodio de cuidado.
 * @property confidence confianza minima por estado para aceptar una observacion
 * @property transitions solo para aspectos [SubjectKind.DAG]
 * @property states los estados con regla. Un estado que no figura aca se observa
 *   y no alerta — que es exactamente lo que hoy significa un bloque vacio.
 */
public data class AspectDto(
    public val unknownIsInitial: Boolean = true,
    public val unknownAfter: String? = null,
    public val confidence: Map<String, Double> = emptyMap(),
    public val transitions: List<TransitionDto> = emptyList(),
    public val states: Map<String, StateRuleDto> = emptyMap(),
)

/**
 * Una transicion legal entre dos estados de un aspecto [SubjectKind.DAG].
 *
 * @property stableFor histeresis: cuanto hay que sostener el estado destino
 *   antes de creerle al cambio. Evita el flapping por ruido del sensor.
 * @property record si la transicion en si misma merece video, la ventana
 */
public data class TransitionDto(
    public val from: String,
    public val to: String,
    public val stableFor: String,
    public val record: RecordDto? = null,
)

/**
 * Que hace el sistema cuando el residente —o la cama, o la silla— esta en un
 * estado determinado.
 *
 * Las tres formas de vigilar un estado son excluyentes entre si por razon
 * clinica, y esta invariante se valida: un estado se vigila por el instante de
 * entrar ([onEntry]) **o** por cuanto lleva adentro ([dwell]) **o** por cuanto
 * lleva sin volver ([comeBack]), nunca por dos a la vez.
 *
 * @property observeOnly lo veo, lo anoto, no alerto. Es el default de todo
 *   estado no declarado, y es un valor legitimo: el nivel STANDARD entero es esto.
 * @property closesEpisodes entrar a este estado cierra los episodios abiertos.
 *   Hoy lo usa `staff.presence.PRESENT`: es lo que le da referente real a la
 *   condicion de cierre STAFF_ONLY, que sin esto es una promesa sin mecanismo.
 * @property stableFor debounce para aspectos [SubjectKind.FLAGS]
 * @property onEntry regla que dispara al entrar al estado
 * @property dwell reglas por permanencia; una por ventana horaria
 * @property comeBack reglas por no-retorno; una por ventana horaria
 */
public data class StateRuleDto(
    public val observeOnly: Boolean = false,
    public val closesEpisodes: Boolean = false,
    public val stableFor: String? = null,
    public val onEntry: List<RuleDto> = emptyList(),
    public val dwell: List<RuleDto> = emptyList(),
    public val comeBack: List<RuleDto> = emptyList(),
)

/**
 * Una regla vigente durante una ventana horaria.
 *
 * Una regla mira **un solo estado**, a proposito. La composicion no ocurre aca:
 * ocurre en el episodio. Cuando pasan varias cosas juntas —y de noche pasan— la
 * severidad decide sola: algo de nivel menor entra al episodio abierto como
 * neutro y no notifica; algo del mismo nivel ya es parte de ese episodio; algo
 * mayor lo eleva. Por eso no hacen falta condiciones cruzadas.
 *
 * @property window id de la ventana en que rige; `"always"` si rige siempre
 * @property warningAfter preaviso. Si se omite, se deriva al 50% de [alertAfter]
 * @property alertAfter el plazo. Obligatorio en [StateRuleDto.dwell] y
 *   [StateRuleDto.comeBack]; se ignora en [StateRuleDto.onEntry], que dispara
 *   en el instante de entrar y no tiene plazo que esperar
 * @property severity ver [Severity]
 * @property closure ver [Closure]
 * @property notify a quien y por donde. Si se omite, no se notifica: el evento
 *   queda registrado y visible, pero no despierta a nadie
 * @property record video asociado a esta regla
 */
public data class RuleDto(
    public val window: String = "always",
    public val warningAfter: String? = null,
    public val alertAfter: String? = null,
    public val severity: String = Severity.WARNING,
    public val closure: String = Closure.STAFF_OR_SAFE,
    public val notify: NotifyDto? = null,
    public val record: RecordDto? = null,
)

/**
 * A quien avisar y con cuanta urgencia.
 *
 * Esto vive en la politica y no en el codigo del motor. Hoy los canales por
 * severidad estan hardcodeados en un adapter, o sea que la politica de
 * notificacion no esta en la politica — y nadie que no lea Kotlin puede saber
 * a quien se le avisa.
 *
 * @property escalateAfter cuanto se espera sin confirmacion antes de escalar.
 *   `PT0S` es inmediato.
 */
public data class NotifyDto(
    public val channels: List<String>,
    public val escalateAfter: String,
)

/** Ventana de video alrededor del momento en que la regla dispara. */
public data class RecordDto(
    public val before: String,
    public val after: String,
    public val quality: String = Quality.HIGH,
)

/**
 * Que tan grave es lo que paso.
 *
 * Es la unica dimension que decide si algo notifica cuando ya hay un episodio
 * abierto, asi que no es una etiqueta decorativa: es el mecanismo de composicion.
 */
public object Severity {
    /** Queda en el registro. No se entera nadie y no va nadie. */
    public const val INFO: String = "INFO"

    /** Se entera el personal de turno. Nadie tiene que ir. */
    public const val WARNING: String = "WARNING"

    /**
     * Se entera el personal de turno y **alguien tiene que ir**, sin urgencia.
     *
     * Es el escalon donde vive la mayor parte del trabajo nocturno de un
     * geriatrico: la baranda que quedo baja, el andador fuera de alcance, el
     * bano que se esta estirando. Sin el, todo eso o se subestima como aviso o
     * se infla a critico — y un sistema que grita siempre deja de escucharse.
     */
    public const val HIGH: String = "HIGH"

    /** Se entera todo el mundo, ya, y hay que ir ahora. */
    public const val CRITICAL: String = "CRITICAL"

    public val ALL: Set<String> = setOf(INFO, WARNING, HIGH, CRITICAL)

    /**
     * Orden de gravedad. Sirve para decidir si un evento eleva un episodio
     * abierto, lo integra como neutro, o no cambia nada.
     *
     * Coincide con `com.manahive.contracts.policy.Severity.rank` a proposito:
     * si los dos ordenes divergieran, un perfil valido de este lado produciria
     * una composicion de episodios distinta del otro.
     */
    public fun rank(severity: String): Int = when (severity) {
        INFO -> 0; WARNING -> 1; HIGH -> 2; CRITICAL -> 3
        else -> -1
    }

    /**
     * Si este nivel espera que alguien vaya a la habitacion.
     *
     * Es la segunda pregunta que contesta la severidad, y la que separa a [HIGH]
     * de [WARNING]: los dos avisan al mismo turno, pero solo uno pide que se
     * levanten.
     */
    public fun requiresAttendance(severity: String): Boolean =
        severity == HIGH || severity == CRITICAL
}

/**
 * Que cierra el episodio.
 *
 * El cierre es una **dimension aparte**, no una regla mas: nada es excluyente
 * entre si salvo esto. Para el MVP hay dos disparadores —que entre personal a la
 * habitacion, y que el residente llegue a un estado seguro— y esta enumeracion
 * dice cual de los dos alcanza.
 */
public object Closure {
    /** Cierra cuando el residente vuelve a un estado seguro, sin intervencion. */
    public const val SAFE_ONLY: String = "SAFE_ONLY"

    /**
     * Hacen falta las dos cosas: que entre el personal y que la situacion vuelva
     * a ser segura. Es el cierre de una caida — no alcanza con que alguien pase
     * por la habitacion, ni con que la persona se levante sola.
     */
    public const val STAFF_AND_SAFE: String = "STAFF_AND_SAFE"

    /** Cualquiera de las dos alcanza. */
    public const val STAFF_OR_SAFE: String = "STAFF_OR_SAFE"

    public val ALL: Set<String> = setOf(SAFE_ONLY, STAFF_AND_SAFE, STAFF_OR_SAFE)
}

/** Canales por los que se puede avisar. */
public object Channel {
    public const val PUSH: String = "PUSH"
    public const val TABLET: String = "TABLET"
    public const val WARD_BOARD: String = "WARD_BOARD"
    public const val CONSOLE: String = "CONSOLE"

    public val ALL: Set<String> = setOf(PUSH, TABLET, WARD_BOARD, CONSOLE)
}

/** Calidad de la grabacion asociada a una regla. */
public object Quality {
    public const val LOW: String = "LOW"
    public const val STANDARD: String = "STANDARD"
    public const val HIGH: String = "HIGH"

    public val ALL: Set<String> = setOf(LOW, STANDARD, HIGH)
}
