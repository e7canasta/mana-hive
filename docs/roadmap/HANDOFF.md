# Handoff — sesión del 2026-08-29

Escrito de memoria al cierre de la sesión, para que la próxima arranque sin releer todo.
Lo que digo acá con seguridad, lo verifiqué. Lo que no, está marcado como **[verificar]**.

---

## 0. Estado del árbol

**El servicio real corre con el perfil del director gobernándolo.** Verificado contra NATS, no en memoria.

| Verificación | Resultado |
|---|---|
| `./gradlew check` (todo, con hub-service) | **600 tests, 0 fallas** |
| `scripts/blueprints.sh` | **11/11 en verde** |
| `night-watch-runtime` real contra NATS | **arranca en 1,2s, abre episodio CRITICAL** |

> Los tests bajaron de 649 a 600 porque se **borraron 49 junto con el código muerto** del vocabulario duplicado. Menos tests y más cobertura real.

### Estado de las fases

| Fase | Estado |
|---|---|
| 1 — Reloj por slot + `UNKNOWN` | ✅ |
| 2 — Identidad de estado abierta | ✅ **cerrada por borrado** |
| 3 — El perfil como contrato | ✅ **el runtime lo usa** |
| 4 — Transporte de campos de escena | ✅ |
| 5 — Frontera externa | ⚠️ las dos vías andan; falta el sistema de registro real (otro equipo) |
| 6 — Notificación | ✅ |

### La prueba, tal cual salió del servicio

```
CensusSeed      : Censo: elena en cama bed-5 (CAMERA_ROOM_401)
ProfileCalibrator: Alta de elena con perfil v8 (ventana 'night', huella 9489321e…)
ProfileSeed     : Arranque en frio: 1 de 1 perfiles vigentes
NightWatchService: consumiendo: 1 residentes activos
NightWatchService: Signal for elena: EpisodeOpened
NightWatchService: Harbor command for elena: Dispatch
```

Y la señal que salió al bus, con las cinco reglas del perfil en la huella:

```json
{ "type": "EPISODE_OPENED", "resident": "elena", "rule": "alert-bed_edge",
  "trigger": "BED_EDGE", "severity": "CRITICAL", "requiresNvr": true,
  "rulesFingerprint": "alert-bed-left-down,alert-bed_edge,alert-in_bathroom,
                       alert-wheelchair-presence-out_of_reach,comeback-lying" }
```

### Cómo se instala en una habitación

```bash
nats-server -p 4222 -m 8222 -js -sd /var/lib/nats &

mkdir -p profiles
# profiles/census.json  → [{"resident":"elena","bed":"bed-5",
#                           "night":"night-elena-401","monitor":"CAMERA_ROOM_401"}]
# profiles/elena.json   → el ResidentProfileDto del director

./gradlew :engines:night-watch-runtime:bootRun \
  --args='--manahive.profiles.dir=/ruta/a/profiles'

curl localhost:8081/actuator/health   # {"status":"UP"}
```

**Trampas del build:**

- `:hub:hub-service:test` **se cuelga sin NATS**, no se saltea. Levantar el broker antes de `check`.
- `pkill -f <patrón>` matchea el propio comando y mata la shell. Vale para `nats-server` y `GradleWrapperMain`. Matar por PID.

---

## 1. La tesis, en una línea

> El motor de políticas está bien por dentro. Lo que está mal es cómo se lo administra desde afuera: nos llega *plantilla + bolsa de parches*, y tiene que llegarnos **el perfil completo del residente, inmutable y versionado**.

Todo el roadmap se deriva de eso. El documento largo es `SPEC-02-perfil-del-residente.md`, en esta misma carpeta.

---

## 2. Decisiones tomadas (no reabrir sin motivo nuevo)

| # | Decisión | Por qué |
|---|---|---|
| 1 | **Versionado inmutable** del perfil, con historial | Hace trivial el fingerprint y contesta "¿con qué reglas se decidió esa noche?" sin reconstruir |
| 2 | **Reemplazo total, nunca delta** | Elimina precedencia, defaults inventados e invariantes salteadas |
| 3 | **Dos canales**: evento de novedad + endpoint de consulta | Sin el segundo no hay arranque en frío |
| 4 | **Ventanas horarias por re-emisión** en el borde | Cero impacto en motores; el cambio de régimen queda auditado |
| 5 | **`UNKNOWN` inicial** en todo aspecto | Un default que afirma sin haber mirado es un falso positivo garantizado |
| 6 | El catálogo es **plantilla de autoría**, no capa de runtime | El `templateId` queda sólo como procedencia |
| 7 | **Una regla mira un solo estado** | La composición va en el episodio; la severidad decide qué notifica |
| 8 | **La unidad es el residente** | Dos ocupantes = dos habitaciones virtuales; el perfil es autosuficiente |
| 9 | **Cuatro niveles de severidad** | El intermedio (`HIGH`, "llamado") es donde vive el trabajo nocturno real |
| 10 | **STANDARD observa y no alerta**, tampoco las caídas | Es observación pura por diseño; una excepción escondida sería otra constante disfrazada |

---

## 3. Lo que se hizo en esta sesión

### 3.1 La caída era irreportable de punta a punta

Tres agujeros encadenados, cada uno suficiente por sí solo para que nunca funcionara:

1. **`StateKind` no tenía `ON_FLOOR`.** El mapeo lo degradaba a `STANDING` con el comentario *"closest mapping"*. Una persona en el piso viajaba como persona parada.
2. **`ObservationKind` no tenía `ON_FLOOR`.** El edge server no tenía cómo reportar una caída.
3. **El DAG no tenía transiciones hacia el piso.** Aunque llegara la observación, el intérprete la descartaba por transición ilegal.

Los tres cerrados y probados sobre NATS real: entra la observación → episodio `CRITICAL` → alarma → dos órdenes de grabación.

De paso se agregó `BED_EDGE` al enum del DAG: no estaba, y el viaje de ida y vuelta lo convertía en `SITTING_IN_BED` — o sea que la regla crítica de `FALL_RISK` perdía su sujeto.

### 3.2 Reloj por campo y `UNKNOWN` (fase 1 del roadmap, completa)

- `SceneState.since` es un mapa por campo; `evolveScene` estampa sólo lo que cambió vía `stamped()`.
- Antes había **un solo `sceneSince`** para todo el compuesto: si la baranda bajaba a las 3:00 y la silla se movía a las 3:10, se perdía que la baranda llevaba diez minutos abajo. La permanencia por campo no se podía calcular.
- `PresenceState` y `RailState` ganaron `Unknown` (nivel `0b11`, entra en el bitmask que ya existía) y **es el valor inicial**.
- Se corrigieron dos predicados que el valor nuevo volvía mentirosos: `isPresent` daba `true` para desconocido, e `isRailsUp` daba `true` con una baranda sin observar.

### 3.3 Severidad: cuatro niveles

Los niveles contestan **dos preguntas**: quién se entera, y si alguien tiene que ir.

| Nivel | Se entera | Hay que ir | Ventana de confirmación |
|---|---|---|---|
| `INFO` | nadie | no | — |
| `WARNING` | el turno | no | 5 min |
| `HIGH` | el turno | **sí**, sin urgencia | 2 min |
| `CRITICAL` | todos, ya | **sí**, ahora | 0 |

- `Severity.rank` y `Severity.requiresAttendance` en `EffectiveRules.kt`.
- Harbor: bloque `call { }` en el DSL (entre `alert` e `incident`), ruteo propio, y `budget { call(n) }`.
- El `require` de `HarborCalibrationDsl` exige que **toda** severidad tenga canales — buena invariante, encontró todos los huecos sola.

### 3.4 El contrato de entrada, publicado como jar

**`platform/profile-api`** — módulo `pure-domain`, cero dependencias externas (lo garantiza el purity guard). El equipo del sistema de registro compila contra esto en vez de deducir la estructura de un ejemplo.

- `ResidentProfileDto` y su árbol, en primitivos (String, Int, Map, duraciones ISO-8601).
- `ProfileEndpoints` — las cinco firmas que tienen que exponer.
- `ResidentProfileChanged` — el evento de novedad.
- `ProfileValidation.validate()` — **la misma validación que corremos nosotros**, devuelve todos los problemas con ruta exacta.
- `ProfileExamples.ELENA` — el perfil de Elena completo, ejemplo ejecutable.
  **Movido al source principal** en la sesión siguiente: vivía en `ResidentProfileSpec`,
  y los tests no se publican en el jar, así que el equipo externo tenía que copiar
  un JSON de un documento — que es exactamente cómo un contrato se desactualiza.
  De paso, la baranda baja pasó de `CRITICAL` a `HIGH`: es el ejemplo textual que
  justifica el nivel intermedio en la decisión #9, y tenerlo en `CRITICAL` dejaba
  al ejemplo canónico contradiciendo al modelo.

### 3.5 El contrato del bus, escrito

`platform/messaging/BusContract.kt`. Separa **lo que recibimos** de **lo que nos comprometemos a emitir**, con tres promesas que valen más que la tabla:

1. Los hechos de escena van **siempre**, haya episodio o no — es lo único que produce un residente en STANDARD y es su historia clínica.
2. La señal se publica **antes** que la alarma, para que la alarma cite el `seq` real de su origen.
3. Los identificadores son deterministas — un replay produce la misma alarma, no una nueva.

### 3.6 `blueprints/nats-e2e`

Blueprint nuevo que prueba el contrato del bus contra NATS real con JetStream. Tres roles en un proceso hablando por el bus: sistema externo, motor, verificador. 14 checks.

### 3.7 Tipo de dominio del perfil

`ResidentProfile` en `platform/contracts/.../policy/ResidentProfile.kt`. Escrito y compilando, **todavía no usado por nadie**. Es el punto de partida de la fase 3.

---

## 4. El roadmap que queda

### Fase 3 — El perfil reemplaza a los parches

Mueren `AlarmProfile`, `PolicyOverride`, `PolicySource`, `applyOverrides()` y las tres capas de precedencia. `resolve()` deja de ser un merge y pasa a ser una proyección.

**Radio medido de nuevo: 26 archivos** tocan `AlarmProfile` (no 24 — la cuenta vieja era corta).

#### Paso 1 — camino nuevo al lado del viejo ✅ **hecho**

Nada del camino viejo se tocó: `PolicyResolver` sigue intacto y sigue siendo el único que corre en producción.

- `politica-domain` ahora depende de `platform:profile-api`. Los dos son `pure-domain` y el purity guard pasa.
- **`ProfileMapper`** (`politica/profile/ProfileMapper.kt`) — `ResidentProfileDto` → `ResidentProfile`. Devuelve `ProfileMapping.Accepted | Rejected`, nunca tira: un perfil malo es un dato que llegó mal, no un error del programa, y quien lo mandó merece la lista entera de problemas con la ruta de cada uno. **Un perfil se toma entero o no se toma** — aceptar la mitad sería reintroducir el modelo de parches por la puerta de atrás.
- **`ProfileProjection`** (`politica/profile/ProfileProjection.kt`) — `ResidentProfile` + ventana activa → `Explained<PolicyCalibration>`.
- 41 tests nuevos. El spec de la proyección se verificó por mutación (invertir el criterio del plazo de escalada hace fallar exactamente un test).

**Tres decisiones que se tomaron acá y conviene no reabrir:**

1. **Las ventanas horarias eligen, no mezclan.** A las 22:00 se toma la regla de `night` **entera** —plazo, severidad, cierre y notificación— y se descarta la de `always`. No se mergean campos. El spec lo fija: de día el baño es `WARNING`/`SAFE_ONLY` a los 15 min, de noche es `CRITICAL`/`STAFF_OR_SAFE` a los 8. Es otra regla, no el mismo umbral con otro número.
2. **La huella es `(residente, versión, ventana)`.** Con perfiles inmutables la versión *identifica* al documento en vez de resumirlo, así que la deuda 5.1 se disuelve sola en el camino nuevo: el spec prueba que subir de versión mueve la huella aunque no cambie ningún número, que es justo lo que la huella vieja no hacía.
3. **`requiresConfirmation` y `requiresNvr` salen del documento.** Antes se deducían de la severidad (`requiresNvr = severity == CRITICAL`). Ahora los contestan `notify` y `record` del propio perfil. Deducirlos de la severidad *era* el síntoma de que la política de video y de notificación no estaba en la política.

#### Paso 2 — migrar llamadores ← **empezar por acá**

Ninguno migrado todavía. `ProfileProjection` compila, está probado y **no lo llama nadie**.

El orden sugerido, de menos a más acoplado: `PolicyEventParser` → `PoliticaBdd` → `NightWatchService` → hub. `PolicyProjection.kt` del hub se borra entero en la fase 5, así que no vale la pena migrarlo: conviene saltearlo y esperar.

#### Paso 3 — borrar

Cuando no quede ningún llamador. **Ojo con `TemplateId`**: vive dentro de `AlarmProfile.kt` y lo usa `Provenance`, o sea que sobrevive al borrado y hay que mudarlo antes. Lo mismo `CatalogVersion`, que usa `DagCatalog`.

### Fase 4 — Sentinel lee las reglas de cama, silla y andador ✅ **completa**

La corrección de la sesión anterior era correcta: `sceneStateRules` existía y era un stub. Ahora es una vía funcionando de punta a punta, sin bus.

**El transporte que faltaba, en tres lugares que se cortaban a la vez** —cada corte bastaba solo para que la baranda no llegara:

1. `PolicyCalibration` no tenía dónde llevarla → `ScenePolicy` ganó `sceneHysteresis` y `sceneThresholds`; `SentinelPolicy` ganó `sceneStateRules`.
2. El DSL de Sentinel no tenía cómo declararla → `sentinelCalibration { sceneRule(...) }`.
3. Los adapters no la pasaban → `toSceneCalibration` y `toSentinelCalibration` la pasan.

**El problema de tipo se resolvió sin la cirugía de 28 usos.** `AlertRule.trigger` sigue siendo un `StateKind` no-nulo. Las reglas de campo tienen su propio tipo, **`SceneFieldRule`**, que no tiene `trigger` porque la identidad *es* el campo, ni `triggerOn` porque un flag no tiene familias de disparo. El slot de `SentinelCalibration` estaba tipado `Map<String, AlertRule>` y **nadie lo llenaba ni lo leía**, así que retiparlo salió gratis.

> Meter `StateKind.UNKNOWN` en el `trigger` de una regla de baranda hubiera sido afirmar que la persona está en estado desconocido. Era la salida barata y era otra mentira.

**Una sola convención de clave: `sujeto.aspecto`.** Las constantes de campo del gemelo se alinearon a esa forma —`staff.presence`, `wheelchair.presence`, `walker.presence`, `bed.left`, `bed.right`— y el aspecto del perfil de Elena pasó de `railLeft` a `left`. El perfil dice `bed.left` y el motor escucha `bed.left`: **no hay tabla de traducción**, que es donde se pierden las cosas. Nombrar el aspecto además deja lugar a `wheelchair.reach` sin renombrar nada, y una clave plana `wheelchair` no puede sostener dos ejes.

**Probado:** `SceneFieldProjectionSpec` (15) y `ProfileToEnginesSpec` (9), que es el recorrido completo documento → cuatro calibraciones.

**El evaluador la consume.** `SceneDwellExceeded` era un no-op con el comentario *"not yet judged by sentinel"*; ahora abre episodio, y la composición la decide la severidad igual que con las posturas: la baranda `HIGH` eleva un episodio `WARNING` abierto por `BED_EDGE`, y una de nivel menor entra sin elevarlo. Spec: `SceneFieldEpisodeSpec`, 7 tests.

**El `trigger` se resolvió con nullable, no con tipo sellado.** `Episode.trigger` y `SentinelSignal.EpisodeOpened.trigger` son `StateKind?`, y ambos ganaron un `field: String?` al lado. Un episodio abierto por la baranda tiene `trigger = null` y `triggerField = "bed.left"`.

- Meter `StateKind.UNKNOWN` era afirmar que la persona está en estado desconocido: falso.
- El tipo sellado `EpisodeTrigger` es el modelo correcto a largo plazo, pero arrastra los codecs y **cambia el formato de cable**, y no sabemos si hay consumidores afuera (ver §8).
- Nullable no cambia el formato para ningún caso que exista hoy: `put("trigger", trigger?.name ?: "none")` sigue emitiendo un string, así que un consumidor viejo que hace `get("trigger").asText()` no explota.
- Sólo hubo **dos** errores de compilación en todo el árbol. `notifiableStatesFor(null)` devuelve conjunto vacío: un episodio abierto por un campo no tiene paraguas de estados de persona.

**Los tres huecos que quedaban, cerrados.** Eran de la misma familia: *el perfil lo decía y el motor no lo escuchaba*.

1. **El cierre lo gobierna el perfil.** `SentinelPolicy.closingStates` transporta los estados que cierran, como `staff.presence.PRESENT`, y `SceneStateChanged` —que era no-op con el comentario *"harbor's concern, not sentinel's"*— los consulta. Es de Harbor **avisar**; cerrar un episodio es del que lleva los episodios. Antes cerraba porque el código decía que cerraba: si mañana el director decide que la baranda subida también cierra, ahora es una edición del perfil y no un release. Es un conjunto y no una bandera sobre el personal justamente por eso.
2. **La ventana de video sale de la regla.** `RecorderPolicy.ruleWindows`, indexada por `RuleId`, cubre las dos familias —las del residente y las de campo— con la misma clave. El adapter grababa 30s/2m para todo y deducía la calidad de la severidad; ahora usa lo que pidió el perfil y sólo cae al default si el perfil calla. `LOW/STANDARD/HIGH` → `SD/HD/FULL`: el director dice "alta", el recorder sabe que son 1920×1080.
3. **`SceneDwellWarning` emite preaviso.** Con `DwellPreWarning.state` nullable y `field` al lado, igual que `EpisodeOpened`. Sólo avisa si hay regla: un campo que nadie vigila no genera preaviso de algo que después no va a pasar.

**Probado:** `ProfileClosesTheLoopSpec` (12) y `SceneFieldEpisodeSpec` (14, ampliado).

> Un test mío estaba mal y el código tenía razón: con `SAFE_ONLY` la entrada del personal **no** cierra —cierra que el residente vuelva a estado seguro—. Quedó como caso explícito.

### Fase 5 — Frontera externa

- Los dos canales: evento de novedad y endpoint de arranque en frío.
- Eliminar los endpoints de parches: `PUT /api/policies/{id}/watch-level`, `POST /api/policies/{id}/adjustments`, `DELETE /api/policies/{id}/adjustments/{adjId}`.
- **Renombrar nuestro módulo `hub/`** → `policy-gateway`. Hoy se llama igual que el sistema de registro real y externo, y con un contrato de frontera explícito esa confusión se vuelve cara.
- Ventanas horarias por re-emisión en el borde horario.

### Fase 6 — Notificación de vuelta a la política ✅ **en el camino nuevo**

`ProfileProjection` llena `HarborPolicy` desde el `notify` de cada regla: los canales se unen por severidad y el plazo de escalada toma el más corto. `toHarborCalibration` ya prefería los valores de la política sobre sus defaults, así que con el perfil dejó de inventar. Probado en `ProfileToEnginesSpec`.

Sigue hardcodeado en el camino viejo, que es el que corre: `PolicyResolver` emite `HarborPolicy(emptyMap(), emptyMap())` y el adapter cae a sus defaults. Se cierra cuando se migren los llamadores.

---

## 5. Deuda concreta encontrada, no arreglada

### 5.1 El fingerprint del evento no identifica las reglas

`DefaultPolicyChangeProcessor.kt:53` **descarta** el fingerprint que calculó el resolver y computa otro sobre valores resueltos:

```kotlin
private fun PolicyCalibration.fingerprint(): Fingerprint = buildFingerprint(
    "hysteresis" to scene.hysteresis,
    "dwell" to scene.dwellThresholds,
    "confidence" to scene.confidence,
)
```

No incluye versión de catálogo, ni template, ni Sentinel, ni Harbor, ni Recorder, ni `comeBackThresholds`. Cambiar una severidad, una condición de cierre o una ventana de grabación produce **el mismo fingerprint**. Con perfil inmutable esto se disuelve solo (la huella pasa a ser el hash del documento), por eso no lo parcheé.

> **Estado:** resuelto en el camino nuevo — `ProfileProjection` firma con `(residente, versión, ventana)` y hay un test que lo fija. **Sigue vivo en el camino viejo**, que es el único que corre hoy: `DefaultPolicyChangeProcessor` se borra recién cuando se migren los llamadores (fase 3, paso 2).

### 5.2 El wiki miente

Los DTO se escribieron desde `docs/wiki/3.x` y hay dos desajustes con el código. **La fuente es el código:**

| El wiki dice | El código dice |
|---|---|
| `Severity: CRITICAL, HIGH, WARNING, INFO` | eran tres (`INFO, WARNING, CRITICAL`) — ahora cuatro, pero por decisión de esta sesión, no porque el wiki tuviera razón |
| `ClosureCondition: SAFE_ONLY, STAFF_ONLY, STAFF_OR_SAFE` | `{ SAFE_ONLY, STAFF_AND_SAFE, STAFF_OR_SAFE }` — **no existe `STAFF_ONLY`** |

`STAFF_AND_SAFE` no es un detalle: exige **las dos cosas**, y es el cierre correcto de una caída. Ni alcanza con que alguien pase por la habitación, ni con que la persona se levante sola.

### 5.3 `StateKind` mezcla dos ejes ortogonales

`LYING/BED_EDGE/STANDING` son **postura**; `IN_ROOM/IN_BATHROOM/ABSENT` son **ubicación**. Excluyentes en el enum, no en la realidad: **hoy no se puede representar "parada en el baño"**. El modelo objetivo (sujeto × aspecto) lo resuelve; el enum cerrado no.

### 5.4 Mapeos duplicados

Había **cinco copias** de `StateKind→PersonState`. Se dedujeron dos (los DSL de test delegan en `personStateFromKind`). Quedan tres, y **dos de ellas difieren en el `UnknownCause`** que producen (`SCENE` vs `SIGNAL_LOST`), así que no son duplicados puros: unificarlas cambia comportamiento. Mirar antes de tocar.

### 5.5 El mapeo escena↔políticas mentía — corregido, y el diagnóstico era otro

**Lo que decía este punto era cierto pero incompleto, y llevaba a la conclusión equivocada.**

Decía: `ATTEMPTING_EXIT → STANDING`, `IN_CHAIR/IN_WHEELCHAIR → STANDING`, `OUTDOOR → IN_HALLWAY`, y remataba con "ninguno es tan grave". Es más grave de lo que parece —el sistema *afirmaba* que alguien estaba parado cuando estaba sentado, y afirmaba "parado" del instante que precede a una caída— pero la conclusión natural, *"agreguemos los nodos que faltan al DAG"*, es incorrecta. Verificado en código:

**El grafo que corre ya tiene los trece estados.** `TransitionTable.RELEASE_2` está escrito en `StateKind` y tiene `ATTEMPTING_EXIT`, `IN_CHAIR`, `IN_WHEELCHAIR` y `OUTDOOR` como nodos de primera clase con sus transiciones. No falta ningún nodo donde importa.

**Lo que sobra es un segundo vocabulario.** `contracts.dag.SceneState` es un enum de 9 valores, más pobre, y `SceneStateMapping` es el puente que aplastaba. Ese puente lo cruzan sólo `SceneDagToTransitionTable` (que usa únicamente su propio test) y los `SceneDagSource`, que leen un TOML **que no existe en el repo**.

**Y `SceneDag` no puede modelar la escena, se le agreguen los nodos que se le agreguen:** su constructor exige `require(!hasCycles())`, y la escena tiene ciclos por naturaleza. `STANDING → IN_CHAIR → STANDING` es cotidiano. Una estructura que prohíbe lo que la realidad hace todos los días no es el gemelo digital de nada. El nombre "DAG" es el error.

**Hecho:** el puente dejó de mentir. `toSceneState()` devuelve `null` donde no hay nodo fiel, con el motivo escrito. Null significa "no tengo cómo representarlo"; `STANDING` significaba "está parado" y era falso. Spec: `SceneStateMappingHonestySpec`.

**Pendiente:** borrar `SceneDag`, `SceneNode`, `SceneEdge`, `contracts.dag.SceneState`, `SceneStateMapping`, `SceneDagToTransitionTable` y los dos `SceneDagSource`. Es barato —nadie vivo los cruza— y deja una sola identidad de estado.

### 5.5.b La caída no estaba en la tabla base — arreglado

Encontrado en la misma pasada y **peor que el 5.5**, porque estaba vivo:

> `TransitionTable.RELEASE_2` **no tenía ninguna arista hacia `ON_FLOOR`.** Sonda en vivo: `LYING→ON_FLOOR = false`.

Las aristas vivían sólo en `ProductionDagCatalog`, que se aplica como *override* sobre la tabla base. O sea que la caída funcionaba por el camino de producción y **desaparecía en silencio** en cualquier uso de la tabla sola: el default de `SceneCalibration`, el de `BatchConfig`, `Main.kt`. Un motor arrancado así descartaba la observación por transición ilegal.

Es el mismo defecto que ya se arregló dos veces —`ON_FLOOR` ausente de `StateKind`, `BED_EDGE` ausente del DAG— reapareciendo un nivel más abajo.

**Hecho:** `RELEASE_2` tiene la caída desde las once posiciones físicas (incluida la silla de ruedas, que el catálogo tampoco contemplaba), la salida del piso, y la recuperación desde `UNKNOWN`. Spec: `FallReachabilitySpec`, 18 tests.

### 5.6 `mostProtective` no tiene casa todavía

El hub aplica hoy *"gana el umbral más protector"* al colapsar capas (`PolicyProjection.kt`). Es una regla clínica real. En el modelo nuevo no tiene lugar en la resolución —no hay capas— y **pasa a ser validación de autoría**: cuando el director publica un perfil menos protector que su plantilla, el sistema se lo advierte y pide confirmación. Todavía no está implementado en ningún lado, y `PolicyProjection` se borra en la fase 5. **No perderlo.**

### 5.7 Lo que la proyección todavía no puede transportar

Empezó en cuatro y quedó en **una**:

| Qué se pierde | Por qué |
|---|---|
| `wheelchair.presence.unknownAfter` | avisarle a mantenimiento que un sensor lleva media hora mudo |

**Y no es un hueco de transporte: es una capacidad que no existe.** No hay canal de mantenimiento, y es un problema técnico que por diseño nunca abre un episodio de cuidado. Darle un slot en la calibración que nadie lee sería construir otro stub como el `sceneStateRules` que acabamos de sacar — un campo que existe, se llena y no cambia nada. Cuando exista el canal, se transporta.

`ProfileProjection.unrepresentable(perfil)` sigue siendo la fuente: devuelve la lista con la ruta exacta y la explicación de la proyección la nombra. Mientras no esté vacía, el perfil dice más de lo que el motor escucha — y conviene que eso se pueda leer, no deducir.

---

## 6. Cómo correr las cosas

```bash
# Tests rápidos (hub-service es lento: Spring + NATS, ~3 min)
./gradlew test -x :hub:hub-service:test

# Todo
./gradlew test

# Un blueprint
./gradlew :blueprints:nats-e2e:run

# NATS: NO hay docker daemon en esta máquina, pero SÍ hay binario local
nats-server -p 4222 -m 8222 -js -sd /tmp/natsdata &
curl -s http://127.0.0.1:8222/jsz     # health de JetStream
```

**Trampas del build que me costaron tiempo:**

- **`-Werror` está activo**: un warning rompe la compilación. Un `?.let` innecesario alcanza.
- **Los specs de Kotest no se filtran con `--tests`** — Gradle dice "no tests found". Correr el módulo entero.
- **`pure-domain` tiene purity guard**: un módulo puro no puede declarar dependencias externas (sólo stdlib de Kotlin y otros módulos puros). `java.time` sí está permitido, es JDK.
- **No usar `pkill -f nats-server`**: el patrón matchea el propio comando y mata la shell. Usar `pkill -x nats-server`.
- Al agregar un valor a un enum sellado, **revisar los predicados derivados**, no sólo los `when`. `isPresent = this !is NotPresent` se volvió mentira al agregar `Unknown`, y el compilador no dice nada.

---

## 7. Preguntas abiertas

**Para el director médico** — sólo queda una:

- **Quién construye el panel** donde edita los perfiles. Sin eso el rediseño funciona pero nadie lo puede usar. La propuesta de UX ya está hecha (cinco pantallas), falta quién la implementa.

*(Cerradas en esta sesión: si una caída alerta en STANDARD → no, observa y registra. Si hacen falta más niveles de gravedad → sí, se agregó el intermedio. Si hay estados de habitación compartidos → no, la unidad es el residente. Si hacen falta condiciones cruzadas → no.)*

**Dependencia externa, fuera de nuestro control:**

- El equipo del **sistema de registro real** tiene que implementar `com.manahive:profile-api`. Es lo único que puede frenar la puesta en producción.
- **[verificar]** El equipo del **edge server** tiene que empezar a emitir `ObservationKind.ON_FLOOR` y los estados de cama/silla/andador. Nosotros ya podemos recibirlos; que ellos puedan producirlos no lo verifiqué.

---

## 8. Lo que **no** sé

Honestidad sobre los bordes de esta sesión:

- ~~**[verificar]** Si `PolicyBusIntegrationSpec` (hub-service) realmente ejercita NATS o se saltea con un `assumeTrue`.~~ **Cerrada:** no se saltea — **se cuelga**. Sin broker el módulo queda esperando indefinidamente, no falla ni salta. Levantar NATS antes de `check` (ver §0).
- **[verificar]** Si `SceneCalibration.sceneThresholds` se llena desde algún lado hoy. Cableé el sweeper para que lo lea **por campo**, pero el origen de esos umbrales es justamente el hueco de la fase 4.
- **[verificar]** Si hay consumidores del bus fuera de este repo que se rompan al agregar `HIGH` a `Severity` o `ON_FLOOR` a los enums. Adentro está todo verde; afuera no miré.
- No corrí el `night-watch-runtime` como servicio real contra NATS — el blueprint replica su lógica de publicación, pero **no es el servicio**.
