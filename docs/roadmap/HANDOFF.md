# Handoff — sesión del 2026-08-29

Escrito de memoria al cierre de la sesión, para que la próxima arranque sin releer todo.
Lo que digo acá con seguridad, lo verifiqué. Lo que no, está marcado como **[verificar]**.

---

## 0. Estado del árbol

**Nada commiteado.** 36 archivos tocados sobre `main`.

| Verificación | Resultado |
|---|---|
| `./gradlew test -x :hub:hub-service:test` | 534 tests, 14 módulos, 0 fallas |
| `./gradlew :hub:hub-service:test` | exit 0 (lento: Spring + NATS, ~3 min) |
| 11 blueprints | 11 en verde, 0 ❌ |

Si algo falla al retomar, empezar por acá antes de tocar nada.

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
- `ResidentProfileSpec` — el perfil de Elena completo, ejemplo ejecutable.

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

### Fase 3 — El perfil reemplaza a los parches ← **empezar por acá**

Mueren `AlarmProfile`, `PolicyOverride`, `PolicySource`, `applyOverrides()` y las tres capas de precedencia. `resolve()` deja de ser un merge y pasa a ser una proyección.

- **Radio medido: 24 archivos** tocan `AlarmProfile`.
- El tipo de dominio ya existe (`ResidentProfile`); falta el mapper DTO→dominio y el resolver.
- **Hacerlo en tres pasos** para no romper: camino nuevo al lado del viejo → migrar llamadores → borrar.
- El mapper necesita que `politica-domain` dependa de `platform:profile-api` (los dos son `pure-domain`, está permitido).

### Fase 4 — Sentinel lee las reglas de cama, silla y andador

**Corrección importante a lo que dije antes:** yo reporté que la cañería estaba lista. Es cierto en Scene y **falso en Sentinel**.

- `SentinelCalibration.sceneStateRules: Map<String, AlertRule>` existe y tiene accessor `sceneStateRuleFor(field)`, pero **nadie lo llama** y las tres construcciones lo pasan `emptyMap()`. Es un stub, no una vía funcionando.
- `PolicyCalibration` no tiene dónde transportar los campos de escena: hay que agregarlo a `ScenePolicy` y `SentinelPolicy`.
- **Problema de tipo:** `AlertRule.trigger` es un `StateKind` **no-nulo**, y una regla sobre `bed.left` no tiene `StateKind` que poner. Radio medido: **28 usos**, y `Episode.trigger` y `SentinelSignal.trigger` viajan con él. Lo correcto es que apunte a la identidad abierta; conviene que sea **su propio cambio**, no mezclado con el perfil.

### Fase 5 — Frontera externa

- Los dos canales: evento de novedad y endpoint de arranque en frío.
- Eliminar los endpoints de parches: `PUT /api/policies/{id}/watch-level`, `POST /api/policies/{id}/adjustments`, `DELETE /api/policies/{id}/adjustments/{adjId}`.
- **Renombrar nuestro módulo `hub/`** → `policy-gateway`. Hoy se llama igual que el sistema de registro real y externo, y con un contrato de frontera explícito esa confusión se vuelve cara.
- Ventanas horarias por re-emisión en el borde horario.

### Fase 6 — La notificación vuelve a la política

Hoy `PolicyResolver` emite `HarborPolicy(emptyMap(), emptyMap())` y los canales por severidad están **hardcodeados en `PolicyAdapters`**. O sea: la política de notificación no está en la política, y nadie que no lea Kotlin puede saber a quién se le avisa.

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

### 5.5 Lo que el mapeo escena↔políticas todavía pierde

Documentado en `contracts/dag/SceneStateMapping.kt`. Ninguno es tan grave como los dos ya arreglados:

| StateKind | va a | por qué se pierde |
|---|---|---|
| `ATTEMPTING_EXIT` | `STANDING` | el intento de salir no es una posición del DAG |
| `IN_CHAIR`, `IN_WHEELCHAIR` | `STANDING` | el DAG no distingue en qué está sentado |
| `OUTDOOR` | `IN_HALLWAY` | el DAG no modela el afuera |

### 5.6 `mostProtective` no tiene casa todavía

El hub aplica hoy *"gana el umbral más protector"* al colapsar capas (`PolicyProjection.kt`). Es una regla clínica real. En el modelo nuevo no tiene lugar en la resolución —no hay capas— y **pasa a ser validación de autoría**: cuando el director publica un perfil menos protector que su plantilla, el sistema se lo advierte y pide confirmación. Todavía no está implementado en ningún lado, y `PolicyProjection` se borra en la fase 5. **No perderlo.**

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

- **[verificar]** Si `PolicyBusIntegrationSpec` (hub-service) realmente ejercita NATS o se saltea con un `assumeTrue` cuando no hay broker. Pasa en verde en los dos casos, así que no prueba lo que parece.
- **[verificar]** Si `SceneCalibration.sceneThresholds` se llena desde algún lado hoy. Cableé el sweeper para que lo lea **por campo**, pero el origen de esos umbrales es justamente el hueco de la fase 4.
- **[verificar]** Si hay consumidores del bus fuera de este repo que se rompan al agregar `HIGH` a `Severity` o `ON_FLOOR` a los enums. Adentro está todo verde; afuera no miré.
- No corrí el `night-watch-runtime` como servicio real contra NATS — el blueprint replica su lógica de publicación, pero **no es el servicio**.
