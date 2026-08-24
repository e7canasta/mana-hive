# SPEC-08 — La POC operativa, de uno a cuatro residentes

**Depende de:** `SPEC-06` · **Tamaño:** mediano
**Objetivo:** cerrar una versión **operativa** —no una demo con piezas de mentira—
para 1 a 4 residentes, con la menor superficie de error posible.

---

## El criterio de esta spec

Todo lo que entra tiene que cumplir las dos condiciones a la vez:

1. **Sin ello la POC no es el producto.** Un sistema que sólo emite
   `TransitionDetected` no es esto: la frase del director —*"avisame si lleva
   quince minutos sentado"*, *"avisame si no vuelve a la cama"*— es el producto.
2. **No se puede simular.** Nada de valores fijos, residentes falsos ni
   calibraciones de juguete. Si algo queda afuera, queda **documentado como
   límite conocido**, no disfrazado.

Todo lo demás se difiere explícitamente en *Fuera de alcance*, con el motivo.

---

## Lo que hoy impide cerrarla

Verificado corriendo el sistema, no leyendo:

| # | Qué | Consecuencia | Dónde |
|---|---|---|---|
| 1 | La interpretación usa `Instant.now()` | El reloj clínico es el de proceso, no el del hecho. Los umbrales miden cualquier cosa. | `SceneNatsIngest` |
| 2 | **Nadie agenda el barrido** | **No hay dwell ni comeback en producción.** Sólo salen transiciones. | `SceneNatsIngest.sweep()` no tiene llamador |
| 3 | La identidad del residente sale de la calibración singleton | Con 2+ residentes los episodios se mezclan en un ledger. | `SentinelNatsIngest:89` |
| 4 | `HarborState` es global | Un residente ruidoso consume el presupuesto de notificación de los otros. | `HarborNatsIngest:40` |
| 5 | Una sola calibración por servicio | Todos los residentes comparten reglas. | los cuatro `*-service` |

Los puntos 3, 4 y 5 son **el mismo problema visto en tres lugares**: el estado y
la política son por residente, y la unidad de despliegue no lo es.

---

## La decisión: un runtime, no cuatro servicios

**Recomendación: para la POC, colapsar los cuatro motores en un
`night-watch-runtime` y dejar el hub como está.** Dos deployables más NATS.

No es una reescritura. La composición en proceso **ya existe y está probada**:
`engines/pipeline/pipeline-bdd` con `PipelineContext` corre los cuatro motores
con un `PolicyCalibration` por residente, y el blueprint `jose-301-e2e-pipeline`
pasa. El movimiento es el mismo que `SPEC-04` hizo con `PolicyAdapters`:
graduar un módulo de arnés de test a producción.

### Por qué es *más rápido* y no menos

| | Arreglar los cuatro servicios | Un runtime |
|---|---|---|
| Tiempo de evento (#1) | 1 lugar | 1 lugar |
| Agendar barrido (#2) | 1 lugar + resolver la carrera | 1 lugar + resolver la carrera |
| Identidad del residente (#3) | censo en 4 servicios | 1 lugar |
| `HarborState` por residente (#4) | rediseñar el estado de harbor | **gratis**: es un campo del runtime |
| Calibración por residente (#5) | registro en 4 servicios | **gratis**: es un campo del runtime |
| Serialización entre motores | 4 saltos que mantener | **desaparecen** |

Tres de los cinco bloqueadores se resuelven por construcción. Y los cuatro
saltos por el bus —que hasta ayer estaban **todos rotos**— dejan de existir como
superficie de error justo en la versión que hay que demostrar.

### Qué NO cambia

Los motores siguen siendo funciones puras hablando por valores del lenguaje
publicado (`SceneEvent`, `SentinelSignal`, `NoticeCommand`). Eso es lo que
mantiene abierta la opción de volver a separarlos: sería una decisión de
despliegue, no un rewrite. **Ese contrato es innegociable.**

---

## Cambios

### 1 · Tiempo de evento

La interpretación pasa a usar `observation.observedAt`, no `Instant.now()`.

El barrido **sí** usa reloj de pared —pregunta *"¿cuánto lleva ahí ahora?"*— y
está bien que lo haga. Son dos relojes distintos que hoy están mezclados en uno.

Prueba de que funciona: alimentar una noche con marcas de tiempo de hace seis
horas debe producir los mismos hechos que el blueprint, no todos apilados en el
instante del procesamiento.

### 2 · `ResidentRuntime` y su registro

```kotlin
class ResidentRuntime(
    val resident: ResidentId,
    private var calibrations: EngineCalibrations,   // scene · sentinel · harbor · recorder
) {
    private var twin: DigitalTwin
    private var ledger: EpisodeLedger
    private var harbor: HarborState
    private var marks: DwellMarks

    fun onObservation(obs: Observation): List<Outbound>
    fun onTick(now: Instant): List<Outbound>
    fun recalibrate(c: EngineCalibrations)
}
```

`EngineCalibrations` sale entero de `PolicyCalibration` con los adapters que ya
existen (`toSceneCalibration()`, `toSentinelCalibration()`, …).

El registro es un mapa, **no beans de Spring**: las carpetas tienen ciclo de
vida de dominio (ingreso, estadía, alta) y modelarlo con scopes de Spring sale
peor.

### 3 · Un solo escritor por residente

Tanto la observación como el tick del barrido entran por el mismo candado por
residente. Para 1–4 residentes alcanza `ConcurrentHashMap.compute(residentId)`:
toma el candado de esa clave y lo suelta al terminar.

```kotlin
fun onObservation(id: ResidentId, obs: Observation) {
    val out = runtimes.compute(id) { _, rt -> requireNotNull(rt).also { it.onObservation(obs) } }
    publish(out)
}
```

**Sin mailboxes ni hilos por residente en esta versión.** A 8 eventos por
segundo no hay contención, y cada pieza de concurrencia que no se agrega es una
clase de bug que no aparece en la demo.

### 4 · Agendar el barrido

Un `@Scheduled` cada 30 segundos que manda un tick **a través del mismo
candado**. Sin eso no hay dwell ni comeback; con eso pero sin el candado hay dos
escritores sobre el mismo twin.

### 5 · Política real para 1–4 residentes

Cada residente se da de alta por la API del hub (`PUT /watch-level`,
`POST /adjustments`), se resuelve con `PolicyResolver` contra
`CATALOG_BY_LEVEL`, y llega al runtime.

Para la POC, el runtime consume `hub.policy.change.v1` y resuelve **en proceso**
—`PolicyResolver` es puro— y llama a `recalibrate`. Eso saca a
`politica-service` del despliegue sin perder nada.

Los cuatro residentes deben tener **niveles distintos** entre sí. Si los cuatro
son STANDARD, la demo no prueba que la política llegue.

### 6 · El contrato con `ia-cell`: cambios inmediatos + latido periódico

Si el borde se puede configurar, la forma que más sirve aguas abajo **no es
ninguno de los dos modos puros**, sino los dos a la vez:

```
  al detectar un cambio de estado  → emitir YA, con su propio timestamp
  cada 30 s, haya pasado algo o no → emitir el estado ACTUAL (latido)
```

#### Por qué no cadencia pura

- **Pierde transiciones.** Si el residente se sienta y se vuelve a acostar entre
  dos muestras, esa transición no existió nunca para el sistema. Sentarse en el
  borde de la cama es el patrón de riesgo de caída: es justo lo que no se puede
  perder.
- **Agrega latencia donde más duele.** Para un residente CRITICAL, donde *"un
  minuto ya es tarde"*, esperar hasta un período de cadencia para enterarse de un
  `BED_EDGE` es exactamente el retraso que el producto existe para evitar.

#### Por qué no "sólo cambios" puro

- **Rompe `SignalLost`.** Se detecta por ausencia
  (`now - lastHeartbeat >= heartbeatTimeout`, 90 s). Un residente que duerme
  quieto no manda nada, y el sistema declara que perdió la cámara. Una falsa
  alarma por noche, por residente, por dormir normalmente.

#### Por qué el híbrido es estrictamente mejor

1. **Transiciones inmediatas**: latencia mínima donde importa.
2. **Liveness gratis**: el latido *es* el heartbeat, sin protocolo aparte.
3. **Se auto-repara.** Si se pierde un mensaje de cambio —y hoy se puede, porque
   la ingesta todavía no es durable— el próximo latido **vuelve a declarar el
   estado real** y el gemelo converge. Un mensaje perdido pasa de *"estado
   equivocado para siempre"* a *"equivocado como mucho 30 segundos"*.
   Esto mitiga justo el riesgo que esta spec difiere.
4. **Menos tráfico, no más.** Los cambios son raros (unos pocos por hora por
   residente) y el latido cada 30 s da 0,03 ev/s por residente. Veinte
   residentes quedan por debajo de **1 evento por segundo** — dos órdenes de
   magnitud menos que la cadencia de un segundo.

#### Lo que se le pide al borde

| Requisito | Por qué |
|---|---|
| `observedAt` = reloj del borde **en el instante de la observación** | Es el reloj clínico. Todo umbral se mide contra él. |
| Latido cada **30 s** con el estado actual | `heartbeatTimeout` es 90 s: tres latidos de margen antes de declarar pérdida. |
| Distinguir **cambio** de **latido** (un flag basta) | Para que el consumidor no tenga que adivinar si `stateSince` se reinicia. |
| Número de secuencia monótono **por cama** | Hace *visible* un hueco en vez de silencioso. Barato en el borde, imposible de reconstruir después. |
| Reloj sincronizado (NTP) | Si el reloj del borde deriva, derivan todos los umbrales clínicos. |
| **No suavizar ni filtrar por criterio clínico** | La histéresis y la confianza son decisiones del dominio, configurables por residente. El borde reporta percepción; el dominio decide qué significa. |

El último punto es el más importante para la arquitectura: si `ia-cell` empieza a
decidir qué transiciones "valen", la política del director deja de gobernar y no
hay forma de calibrarla por residente.

#### Qué hay que tocar aguas abajo

**Nada.** El intérprete ya trata un estado repetido como no-op que sólo actualiza
el latido, y `stateSince` sólo se mueve en transición. El híbrido funciona con el
código que ya existe — por eso entra en esta spec y no en la lista de diferidos.

### 7 · El blueprint de dos residentes

Un blueprint nuevo con **dos residentes de niveles distintos en la misma noche**,
que demuestre que:

- cada uno abre episodios con **sus** umbrales;
- los episodios **no** se mezclan entre ledgers;
- el presupuesto de notificación de uno **no** afecta al otro.

Es la única prueba de que los bloqueadores 3, 4 y 5 están realmente cerrados. Va
antes de tocar el despliegue: si falla, falla barato.

---

## Criterios de aceptación

1. Una noche alimentada con marcas de hace seis horas produce los mismos hechos
   que el blueprint equivalente, con los mismos tiempos relativos.
2. Con el sistema levantado, una permanencia larga produce `DwellExceeded`, y una
   salida sin retorno produce `ComeBackExceeded`. **Hoy ninguno de los dos ocurre.**
3. Dos residentes con niveles distintos, en la misma noche, abren episodios con
   umbrales distintos y ledgers separados.
4. Silenciar por presupuesto a un residente no silencia al otro.
5. Dar de alta un residente por la API del hub cambia su comportamiento en el
   runtime sin reiniciar nada.
6. `LANG=C.UTF-8 ./gradlew check` verde, y los nueve blueprints en exit 0.
7. Ningún valor fijo, residente inventado ni calibración de juguete en el camino
   de producción. Los defaults provisionales que queden están comentados como
   tales y listados en *Fuera de alcance*.

---

## Fuera de alcance — y por qué

Cada punto queda como **límite conocido y escrito**, no como algo disimulado.

| Diferido | Motivo | Riesgo que se acepta |
|---|---|---|
| **Consumidores durables de JetStream** | Es la spec más grande que queda y no bloquea la demo. | Si un servicio se reinicia, se pierde lo que estaba en vuelo. Con 1–4 residentes y reinicios controlados, es tolerable **si se sabe**. |
| **Buffer configurable por residente** | A 8 ev/s la cola tiene 0 o 1 elementos. La contrapresión de NATS alcanza. | Ninguno a esta escala. |
| **Mailboxes / virtual threads por residente** | El candado por clave da el mismo orden con menos piezas. | Se revisa cuando el trabajo por evento se vuelva lento (inferencia, Postgres, NVR). |
| **Buffer con coalescing** | Con el híbrido los repetidos son un latido cada 30 s: no hay nada que colapsar. | Ninguno. Se reevalúa si sube la cadencia. |
| **Arranque sin NATS** | Los `@PostConstruct` bloquean dentro de JetStream. | Hay que levantar NATS antes que los servicios. Está documentado. |
| **Retirar los cuatro `*-service`** | Se pueden dejar parados y sin desplegar. | Código muerto temporal, marcado como tal. |
| **El nivel viaja como string en `templateId`** | Cambiar `AlarmProfile` es tocar lenguaje publicado. | Hay tests que fijan la convención. |

---

## Orden de trabajo

1. **Tiempo de evento** — chico, aislado, y precondición de todo lo demás.
2. **Blueprint de dos residentes** — falla barato y define "listo".
3. **`ResidentRuntime` + registro**, con `PipelineContext` como semilla.
4. **Candado por residente + barrido agendado**, juntos.
5. **El servicio runtime**: ingest de perception, egress de alarmas y comandos,
   consumo de `hub.policy.change.v1`.
6. **Verificación operativa**: noche completa, dos residentes, contra NATS real.

Los pasos 1 y 2 se pueden hacer en paralelo y no tocan el despliegue. Recién el
5 cambia lo que corre.

---

### Lo verificado al cerrar `SPEC-08`

`check` verde, **500 tests**, **10 blueprints** en exit 0, y el circuito completo
corriendo contra un `nats-server -js` real:

```
PUT /api/policies/jose/watch-level {FALL_RISK}   → HTTP 200
  → hub.policy.change.v1
  → runtime: "Alta de jose en nivel FALL_RISK"
observaciones → PERCEPTION
  → SCENE 2563 b  →  SENTINEL 711 b (EPISODE_OPENED)  →  Harbor Dispatch
```

El `ResidentRuntime` está bien resuelto: `observedAt` para interpretar,
`Instant.now()` sólo en el barrido, `@Scheduled(30s)`, y `synchronized(rt)` en
observación y tick — escritor único por residente sin maquinaria de concurrencia.

Seis defectos encontrados **al revisarlo corriendo**, ya corregidos:

1. **El blueprint no probaba el runtime.** Usaba dos `PipelineContext`
   separados, así que "ledgers separados" era cierto por construcción *del
   test*. Reescrito contra un único `NightWatchRuntime` con los dos residentes
   adentro: el mismo estímulo —los dos sentados 25 minutos— produce episodio en
   José (umbral 15 min) y nada en Elena (umbral 45 min).
2. **El escenario de ComeBack no afirmaba ComeBack.** Se llamaba
   *"ComeBackExceeded"*, emitía cero señales, y el único check era
   `TransitionDetected present`. Ahora lo afirma, y pasa.
3. **El presupuesto de notificación estaba inerte.** `NotificationBudget.track`
   hacía `budgets[severity] ?: return this` y el estado arranca con el mapa
   vacío: el contador no se movía nunca. Y `canDeliver` se le preguntaba a
   `calibration.budget`, que tiene `dispatched = 0` fijo porque es
   configuración. **La fatiga —la razón de ser de Harbor— no suprimía nada.**
4. **`NightWatchService` no era un bean.** Sin `@Component`, sin
   `@Import(NatsClientConfiguration)` y sin `@EnableScheduling`: arrancaba,
   parecía sano, y no consumía nada ni barría nunca.
5. **El nivel del director no llegaba.** Se parseaba de `catalogVersion`
   ("2.1.0") en vez de `templateId`, y `valueOf` sobre el label no matchea
   nunca: todos caían a STANDARD en silencio. El director ponía FALL_RISK y el
   motor vigilaba con otras reglas.
6. **El runtime no publicaba nada.** Sólo se suscribía, así que los hechos y
   señales no llegaban al hub y el System of Record quedaba vacío.

#### Lo que queda abierto

- **Egress de alarmas y grabación.** El runtime publica hechos y señales; el
  mapeo `NoticeCommand → AlarmEvent` que hacía harbor **inventa** `RuleId` y
  `EpisodeId` con UUIDs random. Hay que arreglar el mapeo antes de cablearlo:
  replicarlo sería empeorarlo.
- Los cuatro `*-service` de motor siguen en el repo, sin desplegarse.
- Ingesta no durable y arranque dependiente de NATS: ver `SPEC-08`.
