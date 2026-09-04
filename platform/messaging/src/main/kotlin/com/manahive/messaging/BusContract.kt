package com.manahive.messaging

/**
 * El contrato del bus, escrito.
 *
 * [Subjects] dice *donde* va cada cosa. Esto dice *que* va, *quien* lo pone y
 * *quien lo puede leer* — que es lo que un equipo de afuera necesita para
 * integrarse sin leernos el codigo.
 *
 * Todo mensaje viaja dentro de un `EventEnvelope`:
 *
 * ```json
 * {
 *   "eventId":    "uuid",              // clave de idempotencia, va como Nats-Msg-Id
 *   "type":       "SceneEvent",        // discriminador: sin esto no se reconstruye
 *   "version":    1,
 *   "occurredAt": "2026-01-15T22:00:00Z",
 *   "source":     "night-watch-runtime",
 *   "payloadJson": "{ … }"             // el hecho, serializado aparte
 * }
 * ```
 *
 * `payloadJson` es **texto**, no un objeto anidado, a proposito: los hechos son
 * jerarquias selladas y se serializan con su propio serializador —el que sabe
 * escribir el discriminador— y no con Jackson crudo. Un `SceneEvent` sin
 * discriminador no se puede reconstruir del otro lado.
 *
 * Se prueba de punta a punta en `blueprints/nats-e2e`, contra un NATS real.
 */
public object BusContract {

    /**
     * Lo que **recibimos**. Si esto no llega, el sistema esta ciego.
     *
     * | Subject | Payload | Lo pone |
     * |---|---|---|
     * | `perception.observation.v1.{bed}` | `Observation` | el edge server |
     * | `hub.policy.change.v1` | `PolicyChangeDetected` | el sistema de registro |
     * | `hub.census.snapshot.v1` | censo cama↔residente | el sistema de registro |
     *
     * `Observation` es:
     * ```json
     * { "monitor":"CAMERA_MAIN", "bed":"bed-301",
     *   "kind":"ON_FLOOR", "confidence":0.95, "observedAt":"2026-01-15T22:02:40Z" }
     * ```
     * `kind` es un `ObservationKind`; `confidence` va en [0,1] y por debajo del
     * minimo configurado la observacion se descarta con causa, no en silencio.
     *
     * El censo importa mas de lo que parece: una observacion de una cama sin
     * entrada de censo se ignora, porque no hay a quien atribuirsela.
     */
    public object Recibimos {
        public const val OBSERVACIONES: String = Subjects.PERCEPTION_WILDCARD
        public const val CAMBIOS_DE_POLITICA: String = "hub.policy.change.v1"
        public const val CENSO: String = Subjects.CENSUS_SNAPSHOT
    }

    /**
     * Lo que **emitimos**, y con lo que nos comprometemos.
     *
     * | Subject | `type` | Que es |
     * |---|---|---|
     * | `scene.fact.v1.{bed}` | `SceneEvent` | que paso: transiciones, permanencias, señal |
     * | `sentinel.signal.v1.{bed}` | `SentinelSignal` | episodios: abiertos, elevados, cerrados |
     * | `alarm.event.v1.{alert}` | `AlarmEvent` | a quien se le aviso y por que |
     * | `recorder.command.v1.{bed}` | `RecordingCommand` | ordenes de grabacion |
     * | `evidence.record.v1.{bed}` | `EvidenceRecord` | evidencia archivada |
     *
     * Tres compromisos que valen mas que la tabla:
     *
     * 1. **Los hechos de escena van siempre**, haya episodio o no. Es lo unico
     *    que produce un residente en nivel STANDARD —"observar sin alertar"— y
     *    es lo que alimenta su historial. Un sistema que solo publica cuando
     *    algo sale mal no tiene historia clinica, tiene un registro de quejas.
     *
     * 2. **La señal se publica antes que la alarma.** La alarma cita en su
     *    `origin` la secuencia real de la señal que la origino, y para eso la
     *    señal ya tiene que estar en el stream. Sin ese orden, `origin` es un
     *    cero y la alarma no se puede rastrear hasta su episodio.
     *
     * 3. **Los identificadores son los de verdad.** El `AlertId` es
     *    determinista —episodio + instante— para que un replay produzca la misma
     *    alarma y no una nueva. Nada de UUIDs random en la cadena de auditoria.
     */
    public object Emitimos {
        public const val HECHOS_DE_ESCENA: String = Subjects.SCENE_WILDCARD
        public const val EPISODIOS: String = Subjects.SENTINEL_WILDCARD
        public const val ALARMAS: String = Subjects.ALARM_WILDCARD
        public const val GRABACIONES: String = Subjects.RECORDER_WILDCARD
        public const val EVIDENCIA: String = Subjects.EVIDENCE_WILDCARD
    }

    /**
     * Reglas que valen para todo el bus.
     *
     * - **La version esta en el subject.** Un cambio incompatible es un subject
     *   nuevo, nunca una mutacion: los consumidores viejos siguen andando hasta
     *   que se los retire.
     * - **La retencion es del bus, no la verdad.** Siete dias, politica de
     *   limites. El sistema de registro es el hub; nada depende de la retencion
     *   del bus para ser cierto, y el replay y la auditoria salen de ahi.
     * - **`eventId` es la clave de idempotencia** de punta a punta, y viaja como
     *   `Nats-Msg-Id`: republicar el mismo hecho no lo duplica.
     */
    public object Reglas {
        public const val RETENCION_DIAS: Int = 7
        public const val VENTANA_DEDUPE_MINUTOS: Int = 10
    }
}
