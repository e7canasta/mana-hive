# Handoff — de un residente a muchos

> Cómo levantar el sistema completo sobre NATS JetStream, qué parte del código
> importa, y qué hay que resolver antes de que dos residentes puedan convivir.
> Todo lo de acá está corrido y verificado, no es de memoria.

---

## 1 · Levantar el sistema

### 1.1 El bus, con JetStream

```bash
nats-server -js -sd /tmp/natsdata -p 4222
```

Sin `-js` no hay streams y todo publish devuelve *503 No Responders*. El
directorio de datos se puede borrar entre corridas para arrancar limpio —
pero **bajá el server antes de borrarlo**, o queda con los bloques colgados.

### 1.2 Compilar

```bash
LANG=C.UTF-8 ./gradlew bootJar
```

Los jars quedan en `<modulo>/build/libs/*.jar` (ignorá los `-plain.jar`).

### 1.3 Levantar los seis servicios

Cada uno tiene su puerto, así que conviven:

| Puerto | Módulo |
|---|---|
| 8080 | `hub/hub-service` |
| 8081 | `engines/scene-engine/scene-service` |
| 8082 | `engines/sentinel/sentinel-service` |
| 8083 | `engines/harbor/harbor-service` |
| 8084 | `engines/recorder/recorder-service` |
| 8085 | `engines/politica-engine/politica-service` |

```bash
for m in hub/hub-service \
         engines/scene-engine/scene-service \
         engines/sentinel/sentinel-service \
         engines/harbor/harbor-service \
         engines/recorder/recorder-service \
         engines/politica-engine/politica-service; do
  java -jar $(ls $m/build/libs/*.jar | grep -v plain | head -1) &
done
```

**Si el hub no arranca**, casi siempre es el puerto 8080 tomado por una
instancia anterior:

```bash
ss -lptn 'sport = :8080'   # y matá ese PID
```

Los servicios deben arrancar **después** de NATS: hoy declaran los streams al
arrancar y no reintentan (ver §6, deuda abierta).

### 1.4 Inyectar observaciones

No hay CLI de NATS instalado. Se usó un publicador mínimo en Java contra
`jnats`, que publica un `EventEnvelope` con un `Observation` adentro a
`perception.observation.v1.<cama>`:

```bash
java -cp "$DIR:$JNATS:$JACKSON" Pub bed-4 \
  "IN_BED@2026-08-26T19:20:00Z" \
  "SITTING_IN_BED@2026-08-26T19:25:00Z" \
  "STANDING@2026-08-26T19:30:00Z"
```

**Primera tarea sugerida:** mover ese publicador al repo como herramienta
(`tools/`) y darle un modo "escenario" que lea un blueprint. Sin eso, cada
prueba manual es artesanal.

---

## 2 · La cadena, y cómo mirarla

```
perception.observation.v1.<cama>
        ↓ scene-service
scene.fact.v1.<cama>
        ↓ sentinel-service
sentinel.signal.v1.<cama>
        ↓ harbor-service            ↓ recorder-service
alarm.event.v1.<alerta>       recorder.command.v1.<cama>
```

**Los logs mienten por omisión:** casi todo lo interesante se loguea en DEBUG y
el nivel no siempre se aplica. La evidencia real está en los streams:

```bash
for s in PERCEPTION SCENE SENTINEL ALARM RECORDER EVIDENCE; do
  find /tmp/natsdata -path "*/$s/msgs/*.blk" -exec du -b {} \; \
    | awk -v s=$s '{t+=$1} END{print s": "t+0}'
done
```

Una noche de José, verificada de punta a punta:

```
PERCEPTION 1012 b → SCENE 1261 b → SENTINEL 1767 b → ALARM 1938 b
SENTINEL: EPISODE_OPENED · UMBRELLA_EVENT · EPISODE_CLOSED   (WARNING)
ALARM:    4 alarm.event.v1.<alert-id>
```

Para leer contenido sin herramientas: `strings` sobre el `.blk` y grep del tipo.

`RECORDER` y `EVIDENCE` en cero es **correcto** con la calibración por defecto:
el grabador sólo dispara con `episodeOpened(CRITICAL)` y estos episodios son
WARNING.

---

## 3 · Qué parte del código importa

Ordenado por cuánto lo vas a tocar trabajando escenarios.

| Dónde | Qué es | Cuándo lo tocás |
|---|---|---|
| `blueprints/` | Nueve escenarios BDD ejecutables. `jose-301-sitting-bed` es el más completo. | **Siempre.** Es donde un escenario falla barato y con diagnóstico. |
| `blueprints/jose-301-sitting-bed/Episodes.kt` | El molde de un escenario: observaciones con offsets legibles (`"1h15m"`). | Al escribir un caso nuevo. |
| `platform/messaging/Subjects.kt` | La taxonomía de subjects. La versión es parte del subject. | Al agregar un tipo de mensaje. |
| `platform/messaging/NatsTopology.kt` | Declara los siete streams. Idempotente. | Al agregar un stream. |
| `platform/messaging/NatsClientConfiguration.kt` | Conexión + streams como beans. La importan los seis servicios. | Es de donde cuelga todo lo de NATS. |
| `platform/serialization/SceneEventSerializer.kt`<br>`platform/serialization/SentinelSignalSerializer.kt` | El lenguaje publicado **con discriminador de tipo**. | Al agregar un evento o señal. **Nunca Jackson crudo sobre las interfaces selladas** — no se pueden reconstruir del otro lado. |
| `platform/domain-kernel/Decider.kt` | La forma del núcleo funcional: `decide` / `evolve`. | Para entender por qué no hace falta un motor por residente (§5). |
| `engines/scene-engine/scene-service/.../SceneNatsIngest.kt` | El gemelo digital por cama (línea 47). | Al trabajar multi-cama. |
| `engines/sentinel/sentinel-service/.../SentinelNatsIngest.kt` | Ledgers por residente (línea 43) **y la línea 89**, que es el bloqueador (§4). | Primero de todo. |
| `engines/harbor/harbor-service/.../HarborNatsIngest.kt` | `HarborState` **global** (línea 40). Ver §4. | Al trabajar multi-residente. |
| `hub/hub-service/policy/PolicyService.kt` | Capas event-sourced → `AlarmProfile` → `PolicyCalibration`, con procedencia. | Al configurar un residente por API. |
| `engines/politica-engine/politica-domain/PolicyResolver.kt` | Catálogo + perfil → calibraciones de los cuatro motores. Puro. | Al cambiar cómo se resuelven las reglas. |
| `engines/politica-engine/politica-adapters/PolicyAdapters.kt` | `PolicyCalibration` → calibración de cada motor. | **El puente clave para el multi-residente.** |
| `platform/contracts/policy/LevelCatalogs.kt` | `CATALOG_BY_LEVEL`: los cuatro niveles del director. | Al definir un residente nuevo. |
| `platform/contracts/census/CensusSnapshot.kt` | Mapa cama ↔ residente. **Existe y no lo consume nadie.** | Es lo primero que falta (§4). |

---

## 4 · El estado por residente: cómo está hoy

Tenés razón en que los cuatro motores tienen política por residente. Pero el
estado **no** está uniformemente por residente, y esa asimetría es el problema
real:

| Motor | Estado | Clave | Calibración |
|---|---|---|---|
| **scene** | `ConcurrentHashMap<BedId, DigitalTwin>` | ✅ por cama | ❌ bean único |
| **sentinel** | `ConcurrentHashMap<ResidentId, EpisodeLedger>` | ⚠️ ver abajo | ❌ bean único |
| **harbor** | `private var state = HarborState()` | ❌ **global** | ❌ bean único |
| **recorder** | sin estado en el ingest | — | ❌ bean único |

Tres cosas que salen de esa tabla:

**a · La identidad del residente sale de la calibración singleton.**
`SentinelNatsIngest.kt:89` hace `val residentId = calibration.residentId`, y la
calibración es **un solo bean** (`resident("default")`). El mapa de ledgers está
bien pensado, pero con una única clave posible **colapsa a un ledger para
todos**: los episodios de dos residentes se mezclan.

**b · Harbor tiene un estado global.** `HarborState` guarda avisos y
**presupuesto de notificación**, y hoy es uno solo para toda la residencia. Con
dos residentes, uno ruidoso se come el presupuesto del otro. Clínicamente es lo
peor de la lista: significa alarmas silenciadas para alguien por culpa de otro.

**c · Ninguno sabe de quién es la cama.** `CensusSnapshot` existe en contracts
y no lo consume ningún servicio.

### Tenés razón con la calibración

Que sea **una por residente** está bien — es el diseño correcto y la
documentación de `SentinelCalibration` ya lo dice. Lo que está mal es que hoy
hay **una sola**, inyectada como singleton de Spring. La forma correcta es un
registro por motor:

```kotlin
// en vez de:  private val calibration: SentinelCalibration
// esto:
class CalibrationRegistry<T>(private val default: T) {
    private val byResident = ConcurrentHashMap<ResidentId, T>()
    fun of(id: ResidentId): T = byResident[id] ?: default
    fun put(id: ResidentId, calibration: T) { byResident[id] = calibration }
}
```

Poblado desde política — `PolicyAdapters` ya sabe producir cada calibración — y
actualizado en caliente cuando llega `PolicyChangeDetected`, que el hub **ya
publica al bus**. Falta que los motores lo consuman.

---

## 5 · Concurrencia: los números primero

Tu escenario: **12–20 residentes**, de los cuales 2–5 emiten un evento por
segundo y el resto uno cada cinco.

```
  5 residentes × 1 ev/s   =  5 ev/s
 15 residentes × 0.2 ev/s =  3 ev/s
                            ───────
                             ~8 ev/s sostenidos, picos de ~20
```

**Ocho eventos por segundo.** Un `evaluate` es una función pura sobre un estado
chico: microsegundos. Aun contando un milisegundo por evento a través de los
cuatro motores, eso es **menos del 1% de un core**. Hay cuatro órdenes de
magnitud de margen.

### Mi recomendación, como alguien que ha visto esto salir mal

**No construyas un sistema de actores para ocho eventos por segundo.** A esta
escala la concurrencia no es el problema; el problema es la corrección. Si
metés corrutinas, canales y carriles ahora, agregás una capa de bugs de
concurrencia —los que aparecen una vez cada mil noches— para resolver un cuello
de botella que no existe.

Concretamente:

1. **No agregues `kotlinx-coroutines` todavía.** Hoy no es dependencia del
   proyecto, y a 8 ev/s no la necesitás.
2. **Mantené el `ConcurrentHashMap.compute` de escena.** Ya te da atomicidad por
   cama, que es exactamente el orden que necesitás: las observaciones de una
   misma cama se aplican en serie, y camas distintas no comparten nada.
3. **Arreglá harbor**, que es el que está realmente mal: `HarborState` tiene que
   estar en un mapa por residente, igual que los ledgers de sentinel.
4. **Resolvé la identidad del residente** (§4a). Sin eso, más concurrencia sólo
   mezcla datos más rápido.

### Sobre las goroutines: sí, existen — dos veces

Para que quede la referencia, porque en algún momento va a hacer falta:

- **Virtual threads** (Project Loom), estables desde Java 21. El toolchain acá
  es `jvmToolchain(25)`, así que ya están disponibles **sin agregar ninguna
  dependencia**: `Executors.newVirtualThreadPerTaskExecutor()`. Son el
  equivalente directo de las goroutines: millones de hilos baratos, y bloquear
  no cuesta.
- **Corrutinas de Kotlin**, todavía más livianas porque no son hilos sino
  continuaciones. Más expresivas (canales, `select`, cancelación estructurada),
  pero es una dependencia y un modelo mental más.

### Cuándo revisar esta decisión

El problema real nunca es el paralelismo — es el **orden**. El gemelo digital
*evoluciona*: cada observación se aplica sobre el estado que dejó la anterior.
Dos observaciones de la **misma cama** en paralelo corrompen el estado. Entre
camas distintas no hay nada compartido.

O sea que si algún día hace falta escalar, lo que se necesita no es "más hilos"
sino **un carril por residente**: orden estricto adentro, independencia afuera.
El modelo de actor. Yo lo haría con corrutinas y un `Channel` por residente,
porque el orden sale gratis del canal y la cancelación estructurada resuelve el
apagado.

Volvería a mirarlo cuando pase alguna de estas:

- **Cientos de residentes** (no veinte), o
- **el trabajo por evento crece** — inferencia en el borde, escritura a
  Postgres, llamadas a NVR — porque ahí el bloqueo empieza a importar, o
- **aparece I/O bloqueante en el camino caliente**, que es exactamente el caso
  donde los virtual threads ganan sin cambiar el estilo del código.

---

## 6 · Antes de optimizar nada: la ingesta no es durable

Los seis servicios consumen con **core NATS**
(`connection.createDispatcher` + `subscribe`), no con consumidores de
JetStream — aunque el log diga *"Subscribed to SCENE stream"*.

Consecuencias: sin durabilidad, sin ack, sin replay. **Todo lo publicado
mientras un servicio está caído se pierde**, y el hub es el System of Record.
Los streams retienen siete días y nadie los lee.

Pasar la ingesta a consumidores durables resuelve la pérdida **y** te da el
particionado por residente en el mismo movimiento (el subject ya lleva la cama:
`scene.fact.v1.<cama>`). Es la spec que yo pondría antes que cualquier trabajo
de concurrencia.

Deuda relacionada: los servicios **no arrancan si NATS no está**. Los
`@PostConstruct` de ingesta y egress bloquean dentro de las llamadas JetStream.
`Nats.connectReconnectOnConnect` quita la excepción inicial pero deja el
arranque colgado, que es peor; el arreglo real es darles timeout o diferir la
suscripción a un callback de conexión.

---

## 7 · Orden de trabajo sugerido

1. **Publicador al repo** (`tools/`), con modo escenario que lea un blueprint.
2. **Un residente, un escenario** — ya funciona. Escribilo primero como
   blueprint, después mandalo por el bus.
3. **Otro residente, en otra corrida** — funciona hoy siempre que sea de a uno.
   Sirve para validar `FALL_RISK`, `NIGHT_WANDERING`, `CRITICAL` sin tocar
   concurrencia.
4. **Cerrar el bloqueador multi-residente:** censo cama→residente, registro de
   calibración por residente, y `HarborState` por residente.
5. **Recalibración en caliente:** los motores consumen `PolicyChangeDetected`.
6. **Consumidores durables de JetStream** — y recién ahí, si los números lo
   piden, carriles por residente.

> **Advertencia que vale para todo lo anterior:** `./gradlew check` no ejecuta
> ningún servicio ni ningún blueprint. Todo lo de este documento se verifica
> levantando el sistema, no compilándolo.

---

## 8 · La arquitectura que propongo

> Nada de lo anterior está escrito en piedra. Esta sección es mi recomendación
> como diseño, no como parche.

### El diagnóstico

Hoy la **unidad de despliegue** (el servicio) y la **unidad de consistencia**
(el residente) son ortogonales, y de ese desajuste sale *todo* lo que está mal
en §4: cada servicio guarda un mapa con clave distinta —cama, residente,
global—, cada uno tiene una sola calibración, y la historia de un residente
queda repartida en seis procesos.

Y hay una evidencia que pesa más que cualquier argumento de diseño:

- La composición **en proceso** de los cuatro motores ya existe
  (`engines/pipeline/pipeline-bdd`, `PipelineContext`), toma un
  `PolicyCalibration` resuelto **por residente**, y el blueprint
  `jose-301-e2e-pipeline` la corre entera y pasa.
- La composición **por el bus** entre motores tenía los **cuatro saltos rotos**
  y nunca había funcionado.

Una de las dos está probada y la otra recién empieza a existir. No es
casualidad: cada salto por el bus agrega una serialización, un contrato que
puede desalinearse y un modo de falla silencioso.

### La propuesta

**El residente es la unidad de consistencia. El bus vive en los bordes, no
entre los motores.**

```
   borde / ia-cell
        │  perception.observation.v1.<cama>        ← bus
        ▼
┌─────────────────────────────────────────────────┐
│  night-watch runtime  (un solo proceso)         │
│                                                 │
│   ResidentRuntime(jose)   ResidentRuntime(ana)  │
│   ├ calibraciones ×4      ├ calibraciones ×4    │
│   ├ twin · ledger         ├ twin · ledger       │
│   ├ harborState           ├ harborState         │
│   └ mailbox (orden)       └ mailbox (orden)     │
│                                                 │
│   scene → sentinel → harbor → recorder          │
│   en proceso, por valores, sin serializar       │
└─────────────────────────────────────────────────┘
        │  alarm.event.v1 · recorder.command.v1    → bus
        │  scene.fact.v1 · sentinel.signal.v1      → bus (al hub, para el ledger)
        ▼
   hub  (System of Record · API · auditoría)
```

Concretamente:

1. **Un `ResidentRuntime` por residente.** Un objeto —no un proceso, no un
   servicio— que tiene las cuatro calibraciones de ese residente y su estado:
   twin, ledger, harborState, sesiones de grabación. Es lo que `PipelineContext`
   ya es, con nombre propio y multiplicado por N.

2. **Los motores siguen siendo funciones puras y compartidas.** Un solo
   `SceneInterpreter`, un solo `SentinelEvaluator`. No se instancian por
   residente: reciben `(hecho, estado, calibración)` y devuelven un veredicto.
   Eso ya es así; sólo hay que dejar de inyectarles la calibración en el
   constructor.

3. **Un mailbox por residente** da el orden. Adentro de un residente, serie;
   entre residentes, independientes. A 8 ev/s con 20 residentes esto es
   literalmente un `ConcurrentHashMap` de colas y un pool chico —o un virtual
   thread por residente, que a 20 no se nota.

4. **El bus queda en los bordes**, donde aporta de verdad:
   - **entrada**: observaciones del borde, que sí cruzan máquina;
   - **salida**: alarmas y comandos de grabación, que van a dispositivos;
   - **al hub**: los hechos y señales, para el ledger event-sourced y la
     auditoría. Acá el bus es el registro, no el pegamento.

5. **El hub sigue separado.** Es el único que tiene otra naturaleza: System of
   Record, API, historia clínica, disponibilidad distinta. Ahí la separación se
   gana el costo.

### Qué se gana

- **Los cuatro saltos rotos desaparecen por construcción**, porque dejan de
  existir. No hay serialización entre scene y sentinel.
- **La calibración por residente es natural**: es un campo del runtime, no un
  singleton de Spring peleando con la realidad.
- **El bug del `HarborState` global se evapora**: cada residente tiene el suyo
  porque el estado vive donde vive el residente.
- **El orden es trivial** y no hace falta razonar sobre él en cuatro lugares.
- **Dos deployables** en vez de seis, para una carga que usa el 1% de un core.

### Qué se pierde, y por qué lo pagaría

- **Escalado independiente por motor.** A 8 ev/s no significa nada. Si algún día
  un motor necesita hardware distinto —inferencia en GPU, por ejemplo— el corte
  vuelve a estar disponible: los motores siguen siendo funciones puras que
  hablan por valores del lenguaje publicado. El seam no se destruye, se deja de
  pagar.
- **Aislamiento de fallas entre motores.** Hoy ese aislamiento es teórico:
  ninguno de los seis arrancaba solo hace unas horas.
- **Un residente muy pesado bloquea su propio carril.** Es correcto que así sea:
  su estado es secuencial por definición.

### El contrato que hay que sostener sí o sí

Que los motores sigan siendo **puros** y hablando por **valores del lenguaje
publicado** (`SceneEvent`, `SentinelSignal`, `NoticeCommand`). Eso es lo que
mantiene la opción de volver a separarlos, y es lo que hace que los blueprints
puedan ejercitar la cadena entera sin infraestructura. Si eso se respeta,
in-process vs. bus es una decisión de despliegue reversible — que es
exactamente lo que uno quiere que sea.

### Camino de migración

No es un rewrite. Es mover el punto de composición:

1. `ResidentRuntime` = `PipelineContext` + mailbox, con las calibraciones
   inyectadas por residente. **El código ya está**, en `pipeline-bdd`; hay que
   graduarlo de arnés de test a módulo de producción (es justo lo que
   `SPEC-04` hizo con `PolicyAdapters`).
2. Un servicio nuevo que tenga el registro de runtimes, consuma perception del
   bus y publique alarmas/comandos/hechos.
3. Los cuatro `*-service` actuales se retiran cuando ese servicio los cubre.
   Sus adaptadores NATS de entrada/salida se reutilizan casi tal cual.
4. El hub queda como está.

Mi orden sería: primero §7.4 (censo + calibración por residente), porque hace
falta en cualquiera de las dos arquitecturas; y en paralelo levantar el
`ResidentRuntime` con dos residentes en un blueprint, que es donde se ve si la
idea se sostiene antes de mover un solo deploy.

---

## 9 · La pizarra

### Cómo es hoy

Cuatro especialistas, cada uno en una oficina distinta, que se pasan notas por
una sala de correo. Cada nota hay que traducirla al salir y al entrar.

```
   habitación                sala de correo (NATS)
   ┌───────┐                 ╔═══════════════════╗
   │ José  │───observación──▶║                   ║
   └───────┘                 ║   ┌──────────┐    ║
                             ║   │ escena   │    ║  ← traduce al entrar
                             ║   └────┬─────┘    ║     y al salir
                             ║        │ nota     ║
                             ║   ┌────▼─────┐    ║
                             ║   │vigilancia│    ║  ← traduce otra vez
                             ║   └────┬─────┘    ║
                             ║        │ nota     ║
                             ║   ┌────▼─────┐    ║
                             ║   │  faro    │    ║  ← y otra
                             ║   └────┬─────┘    ║
                             ╚════════│══════════╝
                                      ▼
                                   alarma
```

Cuatro traducciones para **una sola decisión clínica**. Las cuatro estaban
rotas. Y si una oficina está cerrada, la nota se pierde sin que nadie se entere.

### Lo que propongo

Una estación de enfermería. Por cada residente hay **una carpeta abierta**, y
una sola persona sigue su historia de principio a fin: lo ve levantarse, juzga
si importa, decide si llamar y si grabar. Sin traducciones intermedias.

```
   habitaciones              ESTACIÓN DE ENFERMERÍA (un solo proceso)
   ┌───────┐    ╔═════╗     ┌──────────────────────────────────────┐
   │ José  │───▶║     ║────▶│  carpeta José                        │
   └───────┘    ║  N  ║     │  ┌────────────────────────────────┐  │
   ┌───────┐    ║  A  ║     │  │ sus reglas · su estado         │  │
   │ Ana   │───▶║  T  ║────▶│  │ escena→vigilancia→faro→cámara  │  │
   └───────┘    ║  S  ║     │  └────────────────────────────────┘  │
   ┌───────┐    ║     ║     │  carpeta Ana        ┌─────────────┐  │
   │ Elena │───▶║     ║────▶│  carpeta Elena      │ una fila    │  │
   └───────┘    ╚═════╝     │  carpeta …          │ por carpeta │  │
                   ▲        └──────────┬───────────────────────────┘
                   │                   │
                   │                   ▼
                   │              ╔═════════╗
                   └──────────────║  NATS   ║──▶ alarmas · grabación
                                  ╚════╤════╝
                                       ▼
                                ┌─────────────┐
                                │  hub (SoR)  │  historia clínica
                                └─────────────┘
```

El correo **sigue existiendo**, en los dos bordes: lo que llega de las
habitaciones, y lo que sale hacia el personal, las cámaras y el archivo. Lo que
se va es el correo *entre los cuatro especialistas*, que es donde no aportaba
nada y rompía todo.

### El "pool" de carpetas

Una carpeta por residente, abierta mientras vive en la residencia. En código es
literalmente un mapa:

```kotlin
class ResidentRegistry(
    private val policy: PolicyPort,            // trae las 4 calibraciones
) {
    private val runtimes = ConcurrentHashMap<ResidentId, ResidentRuntime>()

    fun of(id: ResidentId): ResidentRuntime =
        runtimes.computeIfAbsent(id) { ResidentRuntime(it, policy.calibrationsFor(it)) }

    /** El residente se fue de alta: se cierra la carpeta. */
    fun close(id: ResidentId) { runtimes.remove(id) }

    /** Cambió su política: se reemplazan las reglas, no el estado. */
    fun recalibrate(id: ResidentId) {
        runtimes.computeIfPresent(id) { _, rt -> rt.withCalibrations(policy.calibrationsFor(id)) }
    }
}
```

**No son beans de Spring.** Los beans son para la infraestructura, que es una
sola: la conexión, los adaptadores, el registro. Las carpetas son objetos de
dominio con ciclo de vida propio —alta, estadía, alta médica— y pelearse con
scopes de Spring para modelar eso es peor que un mapa.

Con 20 residentes el mapa entero son unos pocos kilobytes.

### El orden, sin maquinaria nueva

Lo único que hay que garantizar es que **dos eventos del mismo residente no se
procesen a la vez** — su estado es secuencial por definición. Entre residentes
distintos no hay nada compartido.

`ConcurrentHashMap.compute` ya da exactamente eso: toma el candado de esa clave
y lo suelta al terminar. Escena ya lo usa así hoy.

```kotlin
fun onObservation(id: ResidentId, obs: Observation) {
    runtimes.compute(id) { _, rt -> requireNotNull(rt).apply(obs) }   // en serie por residente
}
```

A 8 eventos por segundo esto sobra. **Cuándo dejaría de alcanzar:** cuando el
trabajo por evento se vuelva lento (inferencia, escritura a Postgres, llamada a
la NVR), porque `compute` bloquea el bin del mapa mientras dura. Ahí se pasa a
un mailbox por residente —un `Channel` de corrutinas, o un virtual thread por
carpeta— y el resto del diseño no cambia. Es un cambio local, no una
rearquitectura.

### Sobre "todos escuchan NATS y se sabe si mueren"

Es una intuición correcta y vale discutirla en serio, no descartarla:

- **"Se escala simple".** Cierto en principio. Pero la carga es de 8 eventos por
  segundo: no hay nada que escalar. Y hasta hoy no escalaba — no funcionaba.
- **"Se sabe si mueren".** Es el argumento más fuerte del diseño actual… salvo
  que hoy **cuatro de los seis servicios no podían ni arrancar y nadie se había
  enterado**. La observabilidad era teórica: apareció cuando alguien los levantó
  a mano.
- Con un solo runtime la pregunta de salud **mejora**: deja de ser *"¿está vivo
  el motor de vigilancia?"* —que al director no le dice nada— y pasa a ser
  *"¿hay alguna carpeta atascada, y de quién?"*, que es la pregunta clínica.

Lo que el bus da de verdad es **desacople** y **replay**. Ambos valen en los
bordes y en el archivo. Entre los cuatro motores, dentro de la decisión sobre
**un** residente, no querés desacople: querés una sola decisión consistente.
Partir un juicio clínico en cuatro procesos y una red es lo que produjo los
cuatro saltos rotos.

Y la puerta queda abierta: mientras los motores sigan siendo funciones puras que
hablan por valores del lenguaje publicado, volver a separarlos es una decisión
de despliegue, no un rewrite.

---

## 10 · Buffer por residente, y el modo del borde

### El buffer no es una optimización: es una consecuencia del modo de ia-cell

`ia-cell` puede configurarse de dos formas, y **cada una exige una política de
buffer distinta**:

| Modo del borde | Qué llega | Buffer seguro |
|---|---|---|
| **cadencia** (level-triggered) | una muestra cada N segundos, repita o no el estado | colapsar repetidos consecutivos |
| **sólo cambios** (edge-triggered) | únicamente transiciones | **FIFO estricto, no se descarta nada** |

En modo cadencia, `IN_BED, IN_BED, IN_BED` son tres muestras del mismo hecho:
quedarse con la más fresca no pierde nada. En modo cambios, **cada mensaje es
información única** y descartar uno es perder una transición.

### La regla que hace segura la configuración

**Coalescing = colapsar corridas consecutivas del mismo estado. Nada más.**

No ofrecer nunca una política de tipo *"quedarse con el último"* a secas. Esa sí
es una perilla de corrección, y alguien la va a prender: colapsar
`LYING → SITTING_IN_BED → STANDING` a `LYING → STANDING` borra el sentarse en el
borde de la cama, que **es** el patrón de riesgo de caída.

Con la regla acotada así, la configuración se vuelve una perilla de
*rendimiento*, no de corrección:

- borde en cadencia + buffer estricto → funciona, sólo trabaja de más;
- borde en cambios + coalescing → funciona, el coalescing nunca se activa
  (no hay repetidos consecutivos).

Los dos desajustes degradan de forma segura. Es la propiedad que hay que
preservar: **que la opción peligrosa no sea representable.**

### El modo "sólo cambios" rompe SignalLost — ojo con esto

`ClockSweeperImpl.checkSignalLost` decide por **ausencia**:

```kotlin
val timeSinceHeartbeat = Duration.between(twin.signal.lastHeartbeat, ctx.now)
if (timeSinceHeartbeat < heartbeatTimeout) { /* sano */ }
```

Con `heartbeatTimeout` de 90 segundos por defecto. En modo **cadencia** eso
funciona solo: la cadencia *es* el latido.

En modo **sólo cambios**, un residente que duerme quieto no manda nada, y a los
90 segundos el sistema declara que perdió la señal. Sería una falsa alarma por
noche, por residente, por dormir normalmente.

**Recomendación:** que `ia-cell` mande **siempre** un latido, independiente del
modo. Así el modo decide únicamente si las muestras de estado se repiten, y la
detección de pérdida de señal queda igual en los dos casos. Es más barato que
tener dos mecanismos de liveness.

### Forma de la configuración

```yaml
scene:
  ingest:
    edge-mode: cadence        # cadence | changes
    buffer:
      capacity: 64
      on-full: block          # block | gap
```

```kotlin
enum class EdgeMode { CADENCE, CHANGES }
enum class OnFull { BLOCK, GAP }

data class IngestBufferConfig(
    val edgeMode: EdgeMode = EdgeMode.CADENCE,
    val capacity: Int = 64,
    val onFull: OnFull = OnFull.BLOCK,
)
```

Arranca como configuración del servicio. Si más adelante resulta que las camas
diferen entre sí, el mismo valor puede viajar por política, que es el canal que
ya lleva las calibraciones por residente.

### Qué hacer cuando se llena

Con un worker por residente y virtual threads, **bloquear es la opción correcta
y es gratis**: la contrapresión sube hasta el consumidor de JetStream, que
simplemente no ackea y el mensaje se vuelve a entregar. Nada se pierde.

`GAP` es la salida de emergencia: si hay que descartar, **decirlo**. El dominio
ya tiene el vocabulario — `SceneEvent.SignalLost` y `UnknownCause.SIGNAL_LOST`
significan *"dejamos de ver al residente"*, que es exactamente lo que un hueco
por buffer lleno es. Quedarse con la última muestra y seguir como si nada es
inventar.

A 8 eventos por segundo el buffer va a tener 0 o 1 elementos. Esto no se diseña
para el caudal: se diseña para que el día que algo se ponga lento —inferencia en
el borde, Postgres, la NVR— degrade de forma que se note.
