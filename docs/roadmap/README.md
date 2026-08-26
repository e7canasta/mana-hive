# Roadmap — mana-hive

> Decisiones de arquitectura y orden de trabajo.
> Redactado por los arquitectos líderes; las specs numeradas son ejecutables por un agente de código sin más contexto que el que traen.

**Fecha de auditoría:** 2026-08-26 · **HEAD:** `ad6b2df` · **Rama:** `main`

---

## Estado

| Spec | Estado |
|---|---|
| `SPEC-00` Build verde | ✅ **cerrada** — `./gradlew check` pasa |
| `SPEC-01` Episodio prematuro | ✅ **cerrada** — el episodio se abre al vencer el plazo |
| `SPEC-02` Política canónica | ✅ **cerrada** — ver [ADR-001](../adr/ADR-001-modelo-de-politica-canonico.md) |
| `SPEC-03` Los cuatro niveles | ✅ **cerrada** |
| `SPEC-04` Adapters a producción | ✅ **cerrada** |
| `SPEC-05` Cadena ComeBack | ✅ **cerrada** — ComeBack de punta a punta: DSL → Politica → Scene → Sentinel |
| `SPEC-06` Catálogo en el hub + API | ✅ **cerrada** — hub arranca, eventos + fold, API escritura + historial, bus cableado |
| `SPEC-07` Lenguaje y documentación | ✅ **cerrada** — vigia→harbor, Susan→Elena (ya hecho), ComeBack estandarizado, POLITICA-GUIDE corregido |

Lo verificado al cerrar 00 y 01, corriendo el código:

- `./gradlew check` verde sin `--continue` y sin excluir módulos, en una shell sin `LANG`.
- José: 22 checks en verde. El episodio ahora se abre por `DwellExceeded`, no por `TransitionDetected`.
- Susan: 18 checks, incluidos **los dos escenarios de dwell que estaban comentados**. La causa real de aquel bloqueo no era el timing del barrido sino una **colisión de claves de marca** entre `checkSignalLost` y `checkDwell`, que usaban la misma `DwellMarkKey`. Se resolvió con un discriminador `DwellMarkKind`. Buen hallazgo: el `TODO` culpaba al síntoma.

Tres defectos encontrados **durante la revisión de la implementación**, ya corregidos:

1. **`TriggerOn.ENTRY` era inalcanzable.** `buildAlertRule` traía `triggerOn: TriggerOn = TriggerOn.DWELL` y ningún llamador lo sobreescribía, así que `transitionRules` quedaba siempre vacío y la rama de transición de Sentinel estaba muerta. El sistema pasó de "todo dispara al entrar" a "nada puede disparar al entrar" — una sobrecorrección. Se agregó `alertOnEntry()` al DSL, se quitó el default y se cubrió con tests.
2. **`alertAfter` sin `warningAfter` reventaba el resolver.** En el catálogo, `warning` caía por defecto en `alertAfter`, violando el invariante `warning < exceeded` de `DwellThreshold`. El camino de override sí tenía el fallback a la mitad; el del catálogo no. Un director que escribe sólo "avísenme a los 15 minutos" — la forma natural y documentada — hacía crashear la resolución.
3. **`triggerOn` no sobrevivía la serialización.** Un catálogo con una regla `ENTRY` escrito a TOML volvía como `DWELL`: el residente crítico dejaba de ser avisado al pisar el borde de la cama, en silencio. Se cableó en `CatalogSerializer` y `CatalogCodec`, con aserción en el roundtrip.

**La deuda de `SPEC-01` quedó cerrada, no diferida.** Al mirarla de cerca no era fragilidad futura: era **el mismo defecto sobreviviendo en el camino del paraguas**. `evaluateUnderUmbrella` escalaba con `ruleFor(state)`, la unión — así que con un episodio ya abierto, entrar a un estado con regla temporizada de mayor severidad escalaba **en la transición**, ignorando el plazo. El arreglo de `SPEC-01` había entrado en `evaluateNewEpisode` y no en el camino hermano.

Y había un hueco simétrico: con un episodio abierto, `evaluateDwellExceeded` sólo emitía `UmbrellaEvent`. Nunca escalaba. Combinado con lo anterior, **una regla temporizada no podía escalar nada, jamás**.

Tres cambios:

1. **Escalar obedece el mismo criterio que abrir.** En la transición sólo actúa una regla `ENTRY`; la temporizada espera su plazo.
2. **Vencido el plazo, la regla temporizada puede escalar.** Es el momento que la transición se negó deliberadamente a aprovechar.
3. **`rulesByTrigger` → `rulesByState: Map<StateKind, List<AlertRule>>`.** Era un `Map<StateKind, AlertRule>` construido con `associateBy`: si un estado tenía regla de entrada y de plazo, una se perdía en silencio.

El punto 3 salió de un fixture de test que declaraba `ENTRY` y `DWELL` sobre `STANDING`. La primera reacción fue prohibir la combinación con un `require`. Es la decisión equivocada: **la rampa de severidad es clínicamente legítima** — *"avisen cuando se pare; si sigue parado a los diez minutos, es crítico"* — y el motor no debe prohibir lo que sólo es todavía inexpresable. La restricción vive en el DSL del director, donde está el vocabulario, no en la estructura de datos del motor.

**Limitación registrada:** el director todavía no puede pedir esa rampa. `ResidentStateRule` tiene un solo campo `severity`, y `alertOnEntry()` y `alertAfter()` son excluyentes en `DagDsl`. El motor la sostiene; la vocabulario no la expresa. Cuando aparezca la necesidad clínica, el cambio es en el DSL, no en Sentinel.

`SentinelCalibration.ruleFor` se eliminó: al terminar no le quedaban llamadores.

### Lo verificado al cerrar `SPEC-02`

`./gradlew check` verde sin `--continue`, **438 tests, 0 fallidos**, José 22 checks y Susan 18. Un solo `PolicyResolver` en el repo, `WatchLevel` con los cuatro valores del director y sólo en `contracts`, sin archivos `.pending`.

Tres decisiones que se apartaron del plan escrito, todas registradas en el ADR:

1. **No se mapeó `WatchLevel` a `RiskLevel`.** Es un error de categoría: `RiskLevel` describe al residente — qué tan frágil es, una entrada; `WatchLevel` describe la decisión clínica. Se cruzan (riesgo alto en STANDARD porque la familia rechazó el monitoreo; riesgo bajo en CRITICAL por post-operatorio). El plan además se contradecía: borraba el nivel y luego seleccionaba catálogo por nivel. Se adelantó de `SPEC-03` el tipo de 4 valores más `CATALOG_BY_LEVEL`, que es lo mínimo sin lo cual `SPEC-02` no cierra.

2. **`ManualAdjustment` y `TimeWindow` se rediseñaron**, contra la instrucción de conservarlos. Guardaban `AlertRule`, que **no tiene umbral de tiempo**: el hub no podía almacenar *"avísenme a los quince minutos"*, que es lo que el director más hace. Ahora guardan `state` + `DwellThreshold`, y `ManualAdjustment` exige `reason` no vacío.

3. **`resolve()` devuelve `Explained<PolicyCalibration>`** y `PolicyCalibration` gana `fingerprint` tipado con el `Fingerprint`/`buildFingerprint` que ya existía en `contracts.common`. Sin lo primero, la procedencia que el hub arma se cortaba en el resolvedor y nunca llegaba a la API.

**Deuda anotada:** `PolicyResolverSpec` y `PoliticaCatalogSpec` ejercitan el resolvedor **legacy** (`AlarmCatalog`). El canónico (`DagCatalog`) tiene cobertura más fina de la que merece. Va a `SPEC-03`.

### Lo verificado al cerrar `SPEC-03`

`./gradlew check` verde sin `--continue`, **443 tests, 0 fallidos**. Cinco blueprints: José 22, Ana 18, night-wandering 11, fall-risk 9, critical 12. `level(WatchLevel)` en el DSL, plantillas fuera de fuentes de test, `resolve(AlarmCatalog, …)` retirado, y la residente Susan renombrada a Ana — sin residuos en el código.

#### "Fuera de la habitación" no disparaba nunca

Al cerrar la spec se retiró un escenario ABSENT del blueprint CRITICAL, atribuyéndolo a que *"el sweep no produce `DwellExceeded` para ABSENT vía `OUT_OF_ROOM`"*. Se verificó: **esa explicación no era la causa**. `OUT_OF_ROOM` sí mapea a `PersonState.Absent`, y el barrido sí emite `DwellExceeded` para ABSENT — hay un test que lo fija (`ClockSweeperAbsentSpec`).

La causa real eran **tres defectos encadenados**, y el escenario estaba destapando el más grave que tuvo el sistema hasta ahora: los cuatro niveles configuran la fila *"Fuera de la habitación"* de `NIVELES-MONITOREO.md` —en CRITICAL, aviso a los 2 minutos— y **no disparaba nunca**. El residente que se va de noche y no vuelve es el caso más peligroso que este producto dice cubrir.

1. **`SceneCalibration.DwellThresholdsBuilder` no exponía `ABSENT`.** Sólo seis estados. La calibración del motor de escena era literalmente incapaz de transportar el umbral, por más que Política lo resolviera bien (el blueprint imprimía `Scene: 5 dwell`, ABSENT incluido).
2. **El adapter lo descartaba en silencio.** `toSceneCalibration()` traducía el mapa con un `when` que terminaba en `else -> {}`. Exactamente la construcción que `SPEC-00` tarea 3 prohíbe, y por el mismo motivo: apaga un caso del dominio sin ruido.
3. **El grafo de transiciones era asimétrico.** Se salía de ABSENT hacia seis estados, pero sólo se entraba desde `STANDING`. El recorrido natural para irse —habitación → pasillo → fuera— moría antes de llegar. Se podía volver de ABSENT al pasillo pero no volverse ABSENT desde el pasillo.

Los tres arreglados. El (2) se cerró estructuralmente, no por caso: se agregó `state(kind)` a los builders de dwell y el adapter dejó de enumerar estados a mano, así que la clase entera de bug —"olvidé un estado en el `when`"— deja de ser posible. El escenario retirado volvió al blueprint y pasa.

**Lección para las specs que siguen:** un escenario que no pasa es una hipótesis sobre el motor, no un veredicto. Antes de retirarlo, reproducir la causa en un test del componente acusado. Acá el componente acusado estaba sano y el bug estaba en otros tres lugares.

### Lo verificado al cerrar `SPEC-04`

`./gradlew check` verde, **445 tests, 0 fallidos**, los cinco blueprints con la misma salida que antes (22 / 18 / 11 / 9 / 12). `PolicyAdapters.kt` vive en `politica-adapters`; ningún módulo de producción depende de `-bdd` ni `-test-data`.

**La guarda se probó en las dos direcciones**, que es lo que hace que una guarda sirva:

- `implementation(scene-bdd)` desde un módulo de producción → falla, con un mensaje que nombra la configuración culpable y sugiere el arreglo.
- `testImplementation(scene-bdd)` → **pasa**. Es importante: una guarda que prohibiera esto empujaría a duplicar los helpers de BDD o a moverlos a producción para esquivarla, que es exactamente lo que la guarda existe para impedir.

(Cuidado al probarla: `scene-domain → scene-bdd` produce una dependencia circular y Gradle falla *antes* de llegar a la guarda. Para verificarla hace falta un par sin ciclo, como `politica-adapters → scene-bdd`.)

#### Un clone limpio de este repositorio no compilaba

`SPEC-04` sacó `build-logic/` del `.gitignore` — correcto, las convenciones de build no estaban versionadas. Al revisar ese cambio apareció algo peor en el mismo archivo:

```
.gradle/     ← la caché. Ignorarla está bien.
gradle/      ← el wrapper y libs.versions.toml. Ignorarlo rompe el repo.
```

Se confundieron los dos directorios. Consecuencia: `gradle/libs.versions.toml` —el catálogo con todas las versiones de dependencias— y `gradle/wrapper/` no estaban en git. **Cualquiera que clonara el repositorio no tenía con qué compilar.** Corregido.

Queda una decisión pendiente que no me corresponde tomar: **`crates/` sigue ignorado**, y son 28 entradas — el workspace Rust con los contextos acotados (`ctx-residencia`, `ctx-politica`, `ctx-vigilancia`…). O es deliberado y va a otro repositorio, o es la misma pérdida a mayor escala.

**Deuda nueva anotada:** hay **dos** clases `DwellThresholdsBuilder` — una en `calibration/dsl/DwellThresholdsDsl.kt` y otra en `calibration/SceneCalibration.kt:167`, con APIs distintas y cobertura de estados distinta. Es la jerarquía paralela que el comentario del primer archivo dice haber eliminado. Unificarlas antes de que la divergencia esconda otro estado.


### Lo verificado al cerrar `SPEC-06` y `SPEC-07`

`LANG=C.UTF-8 ./gradlew check` verde, **494 tests**, los nueve blueprints en exit 0. Y esta vez
*arrancando el servicio*, no sólo cargando un contexto de test:

```
Started HubApplicationKt in 1.371 seconds   ·   Tomcat started on port 8080
PUT /api/policies/jose/watch-level  →  HTTP 200
stream POLICY  →  hub.policy.change.v1 · PolicyChangeDetected · jose
```

Dos criterios ya se cumplían antes de empezar, porque `SPEC-02`–`SPEC-05` los habían dejado
hechos: `PolicyService` cableado (1 y 2) y `CatalogVersion` en la huella (7). El documento de la
spec se corrigió para decirlo en vez de sostener la premisa vieja.

#### El hub no arrancaba, y no era por NATS

`SPEC-06` se cerró con dos `@SpringBootTest` en verde. Pero los dos **excluían justo las dos cosas
que rompían el arranque real**: `nats.enabled=false` y `DataSourceAutoConfiguration` excluido. Con
`bootRun`, en el perfil por defecto:

```
Failed to configure a DataSource: 'url' attribute is not specified
```

El hub declara `spring-boot-starter-jdbc` y el driver de Postgres, y **no tiene una sola línea de
JDBC**: todo el almacenamiento es in-memory. Spring se negaba a arrancar por un DataSource que
nadie usa. La exclusión se movió al `application.yml` principal —donde vale para el servicio, no
sólo para los tests— y se quitó la anotación de los tests, así que ahora prueban **la misma
configuración que se envía**. Se quita cuando aparezca el primer repositorio real.

De paso: la exclusión que ya estaba en el yml de tests apuntaba a
`org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`, paquete que en esta
versión de Boot ya no existe — o sea que **era una línea muerta** y el trabajo lo hacía la
anotación. Corregida al paquete real.

#### El bus no recibía nada, y la escritura contestaba 200

Con el servicio por fin arriba y un `nats-server` local, el primer `PUT` reveló dos defectos
encadenados que ningún test podía ver, porque toda la suite corría con un publicador doble:

1. **`NatsObjectMapper` no registraba `JavaTimeModule`**, y su comentario decía que era a
   propósito: *"services that need it should create their own"*. Pero todo hecho que viaja por
   este bus lleva un `Instant` — `EventEnvelope.occurredAt`, el `at` de cada evento. Esa política
   garantizaba el fallo: *«Java 8 date/time type not supported»*. Ahora va registrado en el mapper
   compartido, con fechas ISO-8601 como en el resto del código.

2. **Nadie creaba los streams de JetStream.** `NatsTopology` documenta que *"every service calls
   `ensure(...)` on startup"* y no tenía **un solo llamador**, así que publicar devolvía
   *503 No Responders*. Se cableó `ensureAll()` en el arranque del hub.

En los dos casos el egress atrapaba la excepción y la escritura contestaba 200. El director
cambiaba el nivel de un residente, y los motores nunca se enteraban.

#### Los otros cuatro, corregidos

3. **La configuración probada no era la que se envía.** Con `nats.enabled=false` el publicador real
   no existe como bean, así que todas las escrituras se ejercitaban con el publicador en null.
   `PolicyPublishSpec` ocupa ese lugar con un doble; verificado en rojo.

4. **Leer la política requería el bus.** `PolicyService` recibía el publicador como dependencia
   directa, así que un egress que no se podía crear tiraba abajo `GET /api/policies/{id}`.
   Consultar qué reglas rigen para alguien es una consulta pura sobre las capas. Ahora llega como
   `ObjectProvider`.

5. **El `catch` de `publishIfPossible` estaba vacío** y el comentario decía que logueaba. Es el que
   destapó los dos defectos del bus una vez que empezó a hablar.

6. **Criterio 7 sin test.** `FingerprintSpec` cubre las dos mitades: versiones distintas dan
   huellas distintas, y la misma versión da la misma huella dos veces.

**Criterio 8, cerrado.** `PolicyBusIntegrationSpec` publica por el egress real, recibe del subject,
y resuelve una calibración con lo que llegó. Es el único test que ejercita el salto por el bus.
Se **saltea** —no falla— si no hay NATS escuchando: verificado en las dos direcciones
(`skipped=0` con el servidor arriba, `skipped=1` sin él). Para correrlo:
`nats-server -js -sd /tmp/natsdata`.

**Criterio 11, resuelto entero.** `RawPolicyController` y `SemanticBucketController` quedaron
`@Deprecated` con motivo y condición de retiro; `PolicyController` es el consolidado. El cuarto,
`PolicyCatalogController`, **sobrevive**: no es un tercer vocabulario para la política de un
residente, responde *"qué tipos de evento y qué dimensiones existen"*. Es metadata del lenguaje,
que `AD-1` no tocó. La decisión quedó escrita en el controlador y en el puerto.

#### Lo que queda abierto

- **El hub sigue sin arrancar si NATS no está.** `Nats.connectReconnectOnConnect` elimina la
  excepción inicial, pero los `@PostConstruct` de ingesta y egress **bloquean** dentro de las
  llamadas JetStream, así que el servicio queda colgado en vez de fallar con un error claro. Eso
  es peor, y el intento se revirtió. El arreglo es darles timeout o diferir la suscripción a un
  callback de conexión, en los seis servicios. Es una spec propia.
- **El nivel viaja al bus como string.** `PolicyChangeDetected` lleva un `AlarmProfile`, y
  `AlarmProfile` **no tiene campo de nivel** — pese a que el nivel es justo lo que elige el
  catálogo. Hoy se recupera del `templateId`, porque el fold hace `LevelTemplate(id = level.label)`.
  Funciona por convención de strings; `PolicyPublishSpec` y `PolicyBusIntegrationSpec` la fijan
  para que romperla rompa un test. Agregar el nivel al contrato publicado es una decisión de
  lenguaje publicado.
- **Los otros cinco servicios no se arrancaron.** El hub era el único sin `@Configuration`, pero
  los dos defectos del bus —mapper y streams— son de `platform/messaging` y los comparten todos.

---
## 0. Cómo leer esto

Este documento no repite lo que dice el código. Dice **qué está roto, por qué importa clínicamente, y en qué orden hay que tocarlo**. Cada punto tiene una spec hermana (`SPEC-NN-*.md`) que un agente de código puede tomar y ejecutar de forma aislada.

Regla de la casa: **ninguna spec se ejecuta contra un build rojo.** `SPEC-00` es precondición de todas las demás.

---

## 1. Estado real, verificado

Lo que sigue no es lectura de documentación: se corrió el build y se corrieron los blueprints.

### 1.1 El build no compila

```
./gradlew check   →  BUILD FAILED · 11 tareas
```

| Tarea que falla | Causa raíz | Commit que la introdujo |
|---|---|---|
| `:platform:serialization:compileKotlin` | Llave `}` de más en `SentinelSignalSerializer.kt:69` → 25 errores de sintaxis | `ad6b2df` (HEAD) |
| `:platform:contracts:compileTestKotlin` | `PolicyPayloadDslSpec` prueba 4 builders DSL que nunca se escribieron | `6bd1037` |
| `:hub:hub-domain:compileKotlin` | `InMemoryPolicyCatalog` importa `contracts.policy.PolicyCatalog`, que no existe | `6bd1037` |
| `:platform:batch-io`, `:engines:scene-engine:scene-batch`, `:engines:sentinel:sentinel-batch`, `:engines:sentinel:sentinel-service`, `:engines:harbor:harbor-service`, `:engines:politica-engine:politica-bdd`, `:blueprints:jose-301-harbor-delivery` | Cascada de las tres anteriores | — |
| `:engines:scene-engine:scene-domain:test` | **No es un test que falla.** Gradle no puede hashear el reporte HTML de un test cuyo nombre contiene `María`. `sun.jnu.encoding = ANSI_X3.4-1968` | entorno, no código |

El último punto es el más revelador: **el lenguaje ubicuo del proyecto es español y la cadena de build no está configurada para UTF-8.** Con `LANG=C.UTF-8` la tarea pasa sin tocar una línea de Kotlin.

Patrón de fondo en los otros dos: hay **consumidores y tests escritos contra APIs que nunca se implementaron**. No es deuda de refactor, es código que nunca compiló.

### 1.2 El episodio se abre al entrar al estado, no al vencer el tiempo

Este es el hallazgo con mayor impacto clínico y no estaba en la lista original.

El director escribe, en el perfil de José:

```kotlin
resident {
    sitting { alertAfter(Duration.ofMinutes(15)) }
}
```

Y lo lee en voz alta como *"si se queda sentado más de quince minutos, avisen"*. Lo que ocurre al correr el blueprint:

```
── Pipeline: José se sienta en la cama ──
  Stage 1 — Scene:     3 SceneEvents  (sólo TransitionDetected, ningún DwellExceeded)
  Stage 2 — Sentinel:  EpisodeOpened  ← se abre acá
  Stage 3 — Harbor:    Dispatch
```

La causa está en `SentinelEvaluatorImpl.evaluateNewEpisode()`:

```kotlin
val rule = calibration.ruleFor(state) ?: return noRuleResult(state, episodes)
return openEpisode(bed, rule, now, episodes)   // se abre por EXISTIR la regla
```

El campo temporal de la regla nunca se consulta en esta rama. `alertAfter(15m)` sólo consigue que la regla **exista**; el disparo es en la transición. El resultado es que José recibe notificación en el instante en que se sienta.

Esto es exactamente la queja de la enfermera de guardia que motiva el producto: *"vamos a la habitación y José ya está acostado"*. El sistema, hoy, produce esa falsa alarma por diseño de la implementación, no por calibración.

→ **`SPEC-01`**

### 1.3 Hay dos modelos de política y no se hablan

| | Stack A — hub | Stack B — politica |
|---|---|---|
| Entrada | `PolicyLayers(level, template, adjustments, windows)` | `DagCatalog` + `AlarmProfile` |
| Niveles | `WatchLevel { STANDARD, ENHANCED, CRITICAL }` | `LevelCatalogs`: STANDARD, NIGHT_WANDERING, FALL_RISK, CRITICAL |
| Salida | `EffectiveRules` (sólo reglas para Sentinel) | `PolicyCalibration` (Scene + Sentinel + Harbor + Recorder) |
| DSL | ninguno | `buildDagCatalog {}`, `buildResidentProfile {}` |
| Lo ejercitan | nada | los blueprints |
| Estado | `PolicyService` devuelve layers vacías y loguea `not production ready` | resuelve de verdad |

Son dos respuestas incompatibles a la misma pregunta. Peor: **`WatchLevel` tiene tres valores y ninguno es el vocabulario del director**, que son cuatro y están documentados en `NIVELES-MONITOREO.md` y `DECISION-TREE.md`. El término `ENHANCED` no lo pronuncia nadie en la residencia.

Y la fragmentación ya llegó a la superficie pública: `hub-service/api/` expone **cuatro controladores de política** — `/api/policies`, `/api/policies/raw`, `/api/semantic-buckets`, `/api/catalog` — con **tres vocabularios distintos** para el mismo dominio, y **todos de sólo lectura**: no hay un solo `@PostMapping`, `@PutMapping` ni `@DeleteMapping` en ninguno. El director no puede cambiar nada por API.

→ **`SPEC-02`** (decisión) y **`SPEC-03`** (los cuatro niveles)

### 1.4 El cableado de producción vive en un módulo de test

`PolicyAdapters.kt` — las funciones que convierten `PolicyCalibration` en las calibraciones de cada motor — está en:

```
engines/pipeline/pipeline-bdd/src/main/kotlin/com/manahive/politica/adapters/PolicyAdapters.kt
```

`pipeline-bdd` es soporte de pruebas, y sus únicos consumidores son dos blueprints. Es decir: **la traducción de política a motores no existe fuera del arnés de test.** Ningún servicio Spring puede usarla hoy.

→ **`SPEC-04`**

### 1.5 La cadena del ComeBack está cortada en los dos extremos

Corrección de un supuesto previo: **el ComeBack sí está implementado**, bajo el nombre `ComeBack`.

| Eslabón | Estado |
|---|---|
| `SceneEvent.ComeBackWarning` / `ComeBackExceeded` | ✅ existe |
| `DigitalTwin.leftStateAt` / `baselineState` | ✅ existe |
| `ClockSweeperImpl.checkComeBack()` con marcas idempotentes | ✅ implementado |
| `sceneCalibration { comeBack { } }` | ✅ existe |
| scene-batch: config YAML `comeBack:` | ✅ existe |
| **DSL de política (`DagDsl`)** | ❌ no lo puede expresar |
| **`PolicyCalibration.ScenePolicy`** | ❌ no lo transporta |
| **`SentinelEvaluatorImpl`** | ❌ no lo juzga — no abre episodio |

Scene sabe emitir el hecho; el director no lo puede pedir y Vigilancia lo ignora. La pregunta de la enfermera — *"avisame si no vuelve a la cama"* — no tiene camino.

Nota de diseño: `blueprints/jose-301-sitting-bed/README.md` preveía que el ComeBack fuese **otro `DwellExceeded`** en el stream, precisamente para que Sentinel no tuviera que cambiar. La implementación eligió un tipo de evento propio y no se siguió la consecuencia. Hay que decidir cuál de las dos formas queda.

→ **`SPEC-05`**

### 1.6 El catálogo del director no llega a ningún lado

Verificado corriendo los blueprints:

```
Susan · template("standard") →  Scene: 0 dwell,  Sentinel: 0 alert rules,  Recorder: 0 windows
José  · template("standard") →  Scene: 2 dwell,  Sentinel: 2 alert rules,  Recorder: 0 windows
                                        ↑ las dos vienen de sus overrides, no de la plantilla
```

Consecuencias:

- **La capa de plantilla aporta cero.** De la cadena `catálogo → plantilla → override`, sólo el último peldaño funciona en la práctica.
- **`NIGHT_WANDERING_CATALOG`, `FALL_RISK_CATALOG` y `CRITICAL_CATALOG` no los ejercita ningún blueprint.** Los tres catálogos que llevan los niveles reales del director están escritos y nunca se corren de punta a punta.
- Las plantillas nombradas (`night-wandering`, `fall-risk`, `low-mobility`) sólo existen en **fuentes de test** (`politica-domain/src/test/.../ProductionCatalog.kt`), pese a que `POLITICA-GUIDE.md` las presenta como catálogo maestro de producción.
- `Recorder: 0 transition windows` en ambos: el blueprint de José compensa escribiendo a mano un `recordingCalibration {}`, o sea que **el camino de Grabadora esquiva Política por completo**.

Efecto colateral sobre el documento de la sesión: el "0 episodios / 0 notificaciones" de Susan **no demuestra que vigilar no es alarmar**. Demuestra que su política resolvió vacía. Los checks verdes son vacuamente ciertos.

→ **`SPEC-03`** y **`SPEC-06`**

### 1.7 Deuda de lenguaje

| Síntoma | Detalle |
|---|---|
| `README.md` desfasado | Nombra el módulo `vigia`, que no existe; el código y `CONTEXT-MAP.md` dicen `harbor` (Faro). Apunta a `files/`; los documentos viven en `docs/`. |
| Un nombre, dos papeles | **Susan** es la enfermera de guardia en `jose-301-sitting-bed/README.md` y la residente de la 401 en `susan-e2e-standard`. |
| Un concepto, dos registros | `ComeBack` es el término único en docs y código. *La mina* sobrevive **a propósito** como metáfora explicativa: es la que hace entender el mecanismo a quien no lee Kotlin. Ya no es deuda. |
| Documentación que miente al alza | `POLITICA-GUIDE.md` describe presupuesto, canales y escalación de Harbor como resueltos por Política; `PolicyResolver` devuelve `emptyMap()` en los dos campos. |
| Documentación que miente a la baja | `jose-301-sitting-bed/README.md` declara el ComeBack como pendiente; está implementado. |

→ **`SPEC-07`**

---

## 2. Decisiones de arquitectura

Tres decisiones que hay que tomar **antes** de escribir código, porque cambian el contenido de varias specs. La recomendación de los arquitectos va marcada; la spec correspondiente asume esa recomendación y explicita qué cambia si se decide lo contrario.

### AD-1 · ¿Cuál es el modelo de política canónico?

**Recomendación: Stack B (politica) es canónico. El hub deja de resolver.**

Razones: es el único que resuelve para los cuatro motores; es el único con DSL; es el único que ejercitan los blueprints; y es el que ya contiene los cuatro niveles del director en `LevelCatalogs.kt`.

El hub conserva lo que sólo el hub puede hacer — ser System of Record: **almacena las capas event-sourced** (nivel, plantilla, ajustes manuales, ventanas horarias con su procedencia y su actor) y las **proyecta a un `AlarmProfile`** que entrega a Política. Deja de tener un `PolicyResolver` propio.

`hub.policy.PolicyResolver`, `PolicyLayers`, `LevelTemplate` y `WatchLevel` se retiran o se reencarnan como el modelo de almacenamiento, no de resolución. `EffectiveRules` sobrevive sólo si sigue siendo el contrato Hub→Sentinel; si no, se retira también.

→ `SPEC-02`

### AD-2 · ¿Dónde vive el tiempo de una regla?

Hoy está en dos lugares: el umbral de permanencia en Scene (`dwellThresholds`) y el campo `alertAfter` de la regla de Sentinel, que nadie lee.

**Recomendación: el tiempo vive en Scene; Sentinel juzga hechos ya temporizados.** Scene es quien tiene el reloj y el barrido. Sentinel debe abrir episodio ante `DwellExceeded`, no ante `TransitionDetected`, salvo para estados que el catálogo marque explícitamente como *disparo inmediato* (por ejemplo `BED_EDGE` en nivel CRITICAL, donde un minuto ya es tarde).

Eso obliga a que la regla diga **con qué hecho** se dispara, no sólo sobre qué estado. Es un campo nuevo en `AlertRule`.

→ `SPEC-01`

### AD-3 · ¿El ComeBack es un evento propio o un `DwellExceeded` más?

**Recomendación: se mantiene `ComeBackExceeded` como tipo propio, y Sentinel aprende a juzgarlo.**

El diseño original lo quería fundido en `DwellExceeded` para no tocar Sentinel. Pero son preguntas clínicas distintas — *"lleva mucho en el baño"* vs. *"no volvió a la cama"* — y merecen severidades y condiciones de cierre distintas. Fundirlos ahorra una rama de `when` y cuesta poder expresarlas por separado. El nombre `ComeBack` además se traduce solo al idioma de la enfermera.

Se actualizan los documentos que describen la fusión.

→ `SPEC-05`

---

## 3. Orden de trabajo

El orden no es por tamaño ni por valor aislado: es por dependencia y por riesgo de retrabajo.

```
SPEC-00  Build verde                    ── precondición de todo
   │
   ├── SPEC-01  Episodio prematuro      ── el defecto clínico; independiente, alto valor
   │
   └── SPEC-02  Política canónica (AD-1)── decisión estructural
          │
          ├── SPEC-03  Los cuatro niveles del director
          │       │
          │       └── SPEC-06  Catálogo real en el hub + API
          │
          ├── SPEC-04  Adapters a producción
          │
          └── SPEC-05  Cerrar la cadena ComeBack

SPEC-07  Lenguaje y documentación       ── al cierre de cada spec anterior, no al final
```

| # | Spec | Por qué en este lugar |
|---|---|---|
| 00 | [Build verde](SPEC-00-build-verde.md) | No se puede especificar contra un build rojo. Incluye el arreglo de UTF-8. |
| 01 | [Episodio prematuro](SPEC-01-episodio-prematuro.md) | Es el defecto que el director puede ver y que contradice su propia frase. No depende de AD-1, así que se puede hacer en paralelo a la decisión. |
| 02 | [Política canónica](SPEC-02-politica-canonica.md) | Todo lo que sigue toca política. Hacerlo después obliga a rehacer. |
| 03 | [Los cuatro niveles](SPEC-03-niveles-director.md) | Pone en el código el vocabulario que ya está en los documentos del director. Saca las plantillas de fuentes de test. |
| 04 | [Adapters a producción](SPEC-04-adapters-produccion.md) | Mecánico, pero bloquea cualquier servicio que quiera usar política. |
| 05 | [Cadena ComeBack](SPEC-05-comeback-cadena.md) | Ya está el 70% hecho; falta DSL, transporte y juicio. |
| 06 | [Catálogo en el hub + API](SPEC-06-catalogo-hub-api.md) | El punto original. Es el último porque depende de que el modelo canónico esté decidido. |
| 07 | [Lenguaje y documentación](SPEC-07-lenguaje-y-docs.md) | Se aplica **al cerrar cada spec**, no como tarea final. Un documento que miente cuesta más que un test que falla. |

---

## 4. Criterio de terminado, global

Ninguna spec se considera cerrada si no cumple las cuatro:

1. `LANG=C.UTF-8 ./gradlew check` pasa.
2. Hay un blueprint que ejercita el cambio de punta a punta y cuyos checks **no son vacuamente verdes** — si el escenario espera cero, tiene que existir un escenario hermano que espere distinto de cero con la misma política.
3. Los documentos que describen el área tocada quedan verdaderos en el mismo commit. Sin excepción.
4. Todo término nuevo visible para la dirección o el staff se puede pronunciar en la residencia. Si no, no entra.

---

## 5. Lo que este roadmap deja explícitamente afuera

- Persistencia real del ledger en Postgres y la moviola. No se auditaron.
- El wiring de NATS JetStream (`0db8c31`, `4f3ac36`). Compila salvo la cascada de `SPEC-00`; no se ejercitó.
- `ia-cell` (el borde) y los adaptadores de entrega física.
- `HubSceneConfigSource.kt:36` tiene un `TODO` de conversión sin implementar. Queda anotado, sin spec, hasta que `SPEC-02` defina si ese camino sobrevive.
