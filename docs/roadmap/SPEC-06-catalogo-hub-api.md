# SPEC-06 — El catálogo real en el hub, y la API para editarlo

**Es el punto original de la lista.** Va último porque depende de que el modelo canónico esté decidido: escribir una API sobre dos modelos en conflicto es garantizar el retrabajo.

**Depende de:** `SPEC-02`, `SPEC-03` · **Tamaño:** grande

---

## El problema, tal como estaba anotado

`docs/POLITICA-GUIDE.md`, sección *Gap identificado*:

> - `PolicyService` en el Hub está hardcodeado (usa defaults)
> - Falta conectar el catálogo real al PoliticaApplication
> - Falta API REST para crear/modificar templates desde el Hub

Verificado, sigue siendo cierto. `hub/hub-service/.../policy/PolicyService.kt:34-47`:

```kotlin
// TODO: In real implementation, fetch PolicyLayers from event-sourced history
log.warn("Using hardcoded default PolicyLayers for resident {} — not production ready", ...)
val layers = PolicyLayers(level = WatchLevel.STANDARD, template = LevelTemplate(id = "default", rules = emptyList()), adjustments = emptyList(), windows = emptyList())
```

Todo residente que pase por el hub resuelve a **plantilla vacía en nivel STANDARD**: sin reglas, sin alertas, siempre.

## Lo que cambió respecto de la anotación original

Tras `SPEC-02` y `SPEC-03`, el trabajo ya no es "conectar el catálogo": es **cablear la proyección** que esas specs dejaron definida y ponerle una superficie de edición.

```
Almacén event-sourced de capas   ← acá va lo que el director edita
        ↓ PolicyLayers.toAlarmProfile()     (SPEC-02)
   AlarmProfile
        ↓ politica.PolicyResolver.resolve(CATALOG_BY_LEVEL[nivel], perfil)   (SPEC-03)
   PolicyCalibration
        ↓ adapters                          (SPEC-04)
   Scene · Sentinel · Harbor · Recorder
```

---

## Cambios

### 1 · Las capas se persisten como historia, no como estado

`CONTEXT-MAP.md` y el comentario de `hub.policy.PolicyResolver` ya fijan el principio, y hay que sostenerlo:

> *"El resident policy en sí está event-sourced en el hub (cada cambio de juicio clínico sobre una persona es historia clínica, no un UPDATE)."*

Cada cambio de nivel, cada ajuste manual y cada ventana horaria se escribe como **evento**, con actor y timestamp. El estado vigente es un fold. **Nunca un `UPDATE` sobre la política de un residente.**

Es lo que hace contestable *"¿de dónde salió ese diez, y quién lo puso?"*.

Los eventos mínimos:

| Evento | Campos |
|---|---|
| `WatchLevelAssigned` | residente, nivel, actor, instante, motivo |
| `ManualAdjustmentAdded` | residente, regla, actor, instante, motivo |
| `ManualAdjustmentRevoked` | residente, id del ajuste, actor, instante |
| `TimeWindowDefined` | residente, ventana, reglas, actor, instante |

`motivo` es texto libre y **es obligatorio**. Es lo que el director va a leer dentro de seis meses; un cambio de nivel sin motivo es un cambio que nadie puede explicar.

### 2 · `PolicyService` cablea la cadena real

Reemplazar el cuerpo actual por: leer capas → `toAlarmProfile` → resolver con `CATALOG_BY_LEVEL[nivel]` → devolver `PolicyCalibration` con su `Explained`.

**Sin residente, error explícito.** `SPEC-02` ya lo fija: un residente sin capas es un error operativo que alguien tiene que ver, no un residente en STANDARD. Repetido acá porque es donde se va a sentir la tentación.

### 3 · La API

**Antes de escribir nada: la superficie REST ya existe, y está fragmentada.** `hub-service/api/` tiene cuatro controladores de política, todos **de sólo lectura** — no hay un solo `@PostMapping`, `@PutMapping` ni `@DeleteMapping`:

| Controlador | Ruta base | Vocabulario que expone |
|---|---|---|
| `PolicyController` | `/api/policies` | políticas por residente |
| `RawPolicyController` | `/api/policies/raw` | política cruda |
| `SemanticBucketController` | `/api/semantic-buckets` | buckets semánticos |
| `PolicyCatalogController` | `/api/catalog` | eventos, dimensiones, categorías |

Son **tres vocabularios distintos** para el mismo dominio (política, política cruda, bucket semántico), heredados del modelo que `SPEC-02` retira. La tarea no es "crear una API": es **consolidar y agregar escritura**.

Primer paso, entonces: decidir para cada uno de los cuatro si sobrevive, se fusiona o se retira, a la luz de `AD-1`. Documentarlo en el commit. Un endpoint que expone un modelo retirado es deuda que alguien va a consumir.

Superficie objetivo:

| Método | Ruta | Para qué |
|---|---|---|
| `GET` | `/residents/{id}/policy` | La política vigente, **con procedencia** |
| `GET` | `/residents/{id}/policy/history` | La historia de capas: qué cambió, quién y por qué |
| `PUT` | `/residents/{id}/watch-level` | Cambiar el nivel. Body: nivel + motivo |
| `POST` | `/residents/{id}/adjustments` | Agregar un ajuste manual |
| `DELETE` | `/residents/{id}/adjustments/{adjId}` | Revocar un ajuste |
| `GET` | `/catalog/levels` | Los cuatro niveles con sus tiempos, para que la UI los muestre |
| `GET` | `/catalog/templates` | Las plantillas disponibles |

Notas de diseño, no negociables:

- **`GET /policy` devuelve procedencia.** Cada regla dice si viene del catálogo, de la plantilla o de un ajuste, y en el último caso quién y cuándo. Sin eso la API no sirve para el caso de uso que justifica el producto.
- **Toda escritura exige `motivo` y actor.** 400 si falta.
- **No hay `PUT /catalog/levels`.** Los tiempos de los cuatro niveles son decisión clínica versionada, no configuración de runtime. Cambiarlos es un cambio de catálogo con versión nueva, no una llamada REST. Si la dirección los quiere editables, es otra decisión de arquitectura y necesita su ADR.

### 4 · Publicar el cambio al bus

Cuando la política de un residente cambia, los motores tienen que enterarse. `contracts.policy` ya tiene `CalibrationChanged`, `PolicyChangeDetected` y `PolicyEvent`; `politica-service` ya tiene ingest y egress NATS.

Cablear: escritura en el hub → evento al bus → los motores recalibran. Verificar contra `platform/messaging` cuál es el asunto correcto; no inventar uno nuevo.

### 5 · La versión del catálogo entra en la huella

Toda decisión cita la versión de las reglas que la produjeron — es lo que hace reproducible el replay. Al conectar el catálogo real, `CatalogVersion` (ya existe en `AlarmProfile.kt:31`) tiene que llegar hasta el `fingerprint` de `EffectiveRules` y `SentinelCalibration`.

Prueba de que funciona: dos resoluciones con catálogos de versión distinta deben producir huellas distintas, aunque las reglas resultantes coincidan.

---

## Criterios de aceptación

1. `PolicyService` no contiene `TODO`, ni `hardcoded`, ni `not production ready`.
2. Un residente sin capas produce error, no un STANDARD silencioso, con test que lo demuestra.
3. `GET /residents/{id}/policy` devuelve, para un residente con nivel + ajuste manual, la regla efectiva **y** las tres capas que la formaron con su actor.
4. `PUT /watch-level` sin `motivo` devuelve 400.
5. La historia de un residente al que se le cambió el nivel dos veces devuelve los dos eventos, en orden, con sus actores. Nada se sobrescribió.
6. Cambiar la política publica el evento correspondiente en el bus.
7. Dos catálogos de versión distinta producen `fingerprint` distinto.
8. Test de integración de punta a punta: cambiar el nivel de un residente por la API → el motor recibe la calibración nueva.
9. `LANG=C.UTF-8 ./gradlew check` verde.
10. `docs/POLITICA-GUIDE.md` ya no lista estos puntos como *Gap identificado*.
11. Los cuatro controladores existentes quedan resueltos: cada uno sobrevive, se fusiona o se retira, con la decisión escrita. No queda ninguno exponiendo un modelo que `SPEC-02` retiró.

## Fuera de alcance

- La UI. Esta spec entrega la API; la pantalla que el director toca es otro trabajo.
- Autenticación y autorización. El `actor` se recibe como dato; quién puede firmar qué es una spec propia — **y es una spec necesaria**: hoy cualquiera puede cambiar el nivel de cualquier residente. Anotarla en el roadmap al cerrar ésta.
- Migración de datos existentes: no hay datos en producción.
