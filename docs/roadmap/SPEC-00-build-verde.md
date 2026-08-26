# SPEC-00 — Build verde

**Precondición de todas las demás specs.** Ninguna otra se ejecuta hasta que ésta cierre.

**Depende de:** nada · **Bloquea:** todo · **Tamaño:** chico, mecánico

---

## Objetivo

`LANG=C.UTF-8 ./gradlew check` termina en `BUILD SUCCESSFUL`, sin `--continue` y sin excluir módulos.

## Estado verificado

`./gradlew check` falla en 11 tareas. No es deuda de refactor: hay código que **nunca compiló** y un problema de entorno.

```
:platform:serialization:compileKotlin              FAILED
:platform:contracts:compileTestKotlin              FAILED
:platform:batch-io:compileKotlin                   FAILED
:hub:hub-domain:compileKotlin                      FAILED
:engines:scene-engine:scene-batch:compileKotlin    FAILED
:engines:scene-engine:scene-domain:test            FAILED
:engines:sentinel:sentinel-batch:compileKotlin     FAILED
:engines:sentinel:sentinel-service:compileKotlin   FAILED
:engines:harbor:harbor-service:compileKotlin       FAILED
:engines:politica-engine:politica-bdd:compileKotlin FAILED
:blueprints:jose-301-harbor-delivery:compileKotlin FAILED
```

---

## Tarea 1 — El acento que rompe el build

**Síntoma:** `:engines:scene-engine:scene-domain:test` falla con

```
Cannot access output property 'reports.enabledReports.html.outputLocation'
  > Failed to create MD5 hash for file:
    .../PoliticaToSceneIntegrationSpec/Given--a-PolicyCalibration-...-for-Mar??a/index.html
```

**Causa:** la JVM corre con `sun.jnu.encoding = ANSI_X3.4-1968` (ASCII) porque el entorno tiene `LANG=` vacío y `LC_CTYPE=POSIX`. Kotest genera un directorio de reporte por nombre de test; los nombres de test están en español y contienen `María`. El nombre se escribe en disco con un encoding y se relee con otro, y Gradle no encuentra el archivo que él mismo produjo.

**No es un test que falla.** Ningún assert se rompe. Verificado:

```bash
LANG=C.UTF-8 LC_ALL=C.UTF-8 ./gradlew :engines:scene-engine:scene-domain:test   # pasa
```

**Por qué importa más de lo que parece:** el lenguaje ubicuo de este proyecto es español y está en los nombres de los tests a propósito — es lo que permite que el director lea un reporte de test. La cadena de build tiene que sostener eso, no forzar a renombrar los tests a inglés.

**Cambio:** en `gradle.properties`,

```properties
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.language=es -Duser.country=AR
```

`sun.jnu.encoding` no siempre es respetado como propiedad de sistema según la JVM. Si tras el cambio el síntoma persiste, la alternativa robusta es fijar el entorno:

- en `gradlew`/CI: exportar `LANG=C.UTF-8` y `LC_ALL=C.UTF-8` antes de invocar Gradle;
- documentarlo en el README como requisito de desarrollo.

**Prohibido como "arreglo":** renombrar los tests para sacarles los acentos. Eso resuelve el síntoma destruyendo la razón por la que los tests están en español.

**Criterio de aceptación:** `./gradlew :engines:scene-engine:scene-domain:test` pasa en una shell con `LANG=` vacío.

---

## Tarea 2 — Llave de más en `SentinelSignalSerializer`

**Archivo:** `platform/serialization/src/main/kotlin/com/manahive/serialization/SentinelSignalSerializer.kt`

**Causa:** el commit `ad6b2df` agregó la rama `is SentinelSignal.DwellPreWarning ->` al `when` y dejó una llave de cierre sobrante. Líneas 68-70 quedaron:

```kotlin
            }      // cierra la rama DwellPreWarning
            }      // ← sobra
        }
```

Produce 25 errores `Syntax error: Expecting member declaration` desde la línea 72, y arrastra `Example.kt:87 Unresolved reference 'fromJson'` (la función queda fuera de la clase).

**Cambio:** eliminar la llave sobrante. Verificar que `fromJson` vuelve a ser miembro de la clase.

**Criterio de aceptación:** `:platform:serialization:compileKotlin` y `:platform:batch-io:compileKotlin` pasan.

---

## Tarea 3 — `when` no exhaustivos por los dos tipos nuevos

`ad6b2df` agregó `SentinelSignal.DwellPreWarning` y existe `SceneEvent.StaffLeftDetected`; seis `when` sobre esas jerarquías no se actualizaron.

| Archivo | Falta la rama |
|---|---|
| `platform/batch-io/.../SentinelSignalWriter.kt:26` | `DwellPreWarning` |
| `engines/harbor/harbor-service/.../HarborNatsEgress.kt:106` | `DwellPreWarning` |
| `engines/sentinel/sentinel-batch/.../output/SignalJsonlWriter.kt:35` | `DwellPreWarning` |
| `engines/sentinel/sentinel-batch/.../output/SignalOutWriter.kt:42` | `DwellPreWarning` |
| `engines/scene-engine/scene-batch/.../commands/VerifyCommand.kt:252` | `StaffLeftDetected` |
| `engines/scene-engine/scene-batch/.../output/FactsOutWriter.kt:44` | `StaffLeftDetected` |
| `engines/scene-engine/scene-batch/.../output/FactsWriter.kt:43` | `StaffLeftDetected` |

**Cambio:** agregar la rama que falta en cada uno, con serialización coherente con las ramas vecinas del mismo archivo.

**No usar `else`.** El `when` exhaustivo es lo que hizo visible este problema; un `else` lo apaga y la próxima señal del dominio va a pasar en silencio. Es una decisión de diseño, no una preferencia de estilo.

---

## Tarea 4 — `PolicyCatalog` no existe

**Archivo:** `hub/hub-domain/src/main/kotlin/com/manahive/hub/policy/InMemoryPolicyCatalog.kt:5`

```kotlin
import com.manahive.contracts.policy.PolicyCatalog   // no existe
```

Búsqueda en todo el repo: el único `PolicyCatalog` es `hub-service/.../api/PolicyCatalogController.kt`, que es un controlador REST, no la interfaz. La interfaz nunca se escribió; `InMemoryPolicyCatalog` es una implementación sin contrato.

**Cambio:** crear `platform/contracts/src/main/kotlin/com/manahive/contracts/policy/PolicyCatalog.kt` con la interfaz que `InMemoryPolicyCatalog` ya implementa de hecho. Derivar la firma leyendo la implementación existente y `PolicyCatalogController`; no inventar métodos que nadie llama.

**Nota para quien ejecute:** este catálogo es materia de `SPEC-02`, que puede decidir retirarlo. Acá el objetivo es sólo **que compile con la forma que ya tiene**. No rediseñar, no ampliar.

---

## Tarea 5 — Firmas desactualizadas

| Archivo | Error | Acción |
|---|---|---|
| `engines/sentinel/sentinel-batch/.../config/BatchConfig.kt:53` | Faltan `transitionRules`, `dwellRules`, `sceneStateRules` al construir `SentinelCalibration` | Pasar los tres. **Ojo:** ver nota abajo. |
| `engines/sentinel/sentinel-service/.../nats/SentinelNatsIngest.kt:92` | `EpisodeLedger.empty()` recibe demasiados argumentos; `SentinelCalibration.fatigue` no existe | Ajustar a la firma actual de `EpisodeLedger.empty(residentId)`; quitar la referencia a `fatigue` (la fatiga es de Harbor, no de Sentinel) |
| `blueprints/jose-301-harbor-delivery/.../Main.kt:43` | `Channel` sin resolver | Importar `com.manahive.contracts.common.Channel` |
| `engines/politica-engine/politica-bdd/.../PoliticaBdd.kt:71` | `!!` innecesario sobre un no-nulo (warning como error) | Quitar el `!!` |

**Nota sobre `BatchConfig.kt:53`:** la tentación es copiar lo que hace `SentinelCalibration.from()`, que rellena `transitionRules` y `dwellRules` con el **mismo mapa**. No hacerlo sin leer `SPEC-01`: esa duplicación es la causa del defecto clínico principal. Para esta spec, replicar el comportamiento actual es aceptable **si se deja un comentario apuntando a `SPEC-01`**; el arreglo real es allá.

---

## Tarea 6 — `PolicyPayloadDslSpec` prueba una API inexistente

**Archivo:** `platform/contracts/src/test/kotlin/com/manahive/contracts/policy/PolicyPayloadDslSpec.kt` — 64 errores.

El spec ejercita cuatro builders DSL:

```
buildCalibrationPayload {}   buildResponsePayload {}
buildEscalationPayload {}    buildRecordingPayload {}
```

En `PolicyPayload.kt` sólo existen los cuatro `data class` correspondientes (`CalibrationPayload`, `ResponsePayload`, `EscalationPayload`, `RecordingPayload`). **Los builders nunca se escribieron.** El test se commiteó en `6bd1037` contra una API planeada.

**Decisión requerida antes de tocar** — dos caminos legítimos:

**(a) Escribir los builders.** El test documenta la forma esperada del DSL con precisión (`dwell`, `confidence`, `hysteresis`, `heartbeatTimeout`, `rule`, `requiresNvr`, `reversible`, `escalationDelay`, `staffAssist`, `maxLevel`, `enabled`, `preEventWindow`, `postEventWindow`, `quality`). Implementarlos contra el test.

**(b) Retirar el test.** Si estos payloads son un modelo que `SPEC-02` va a descartar, mantener el test es trabajo perdido.

**Recomendación de los arquitectos: (a), pero recién después de `SPEC-02`.** Para desbloquear el build ahora, mover el archivo a `PolicyPayloadDslSpec.kt.pending` **con una entrada en este roadmap**, no borrarlo. Es la única deuda que esta spec puede dejar abierta, y se cierra en `SPEC-02`.

Si se elige mover: dejar en la cabecera del archivo un comentario con la fecha, el motivo y el enlace a `SPEC-02`.

---

## Verificación final

```bash
LANG=C.UTF-8 ./gradlew check
```

Debe dar `BUILD SUCCESSFUL`. Además, los dos blueprints deben seguir corriendo:

```bash
LANG=C.UTF-8 ./gradlew :blueprints:jose-301-e2e-pipeline:run
LANG=C.UTF-8 ./gradlew :blueprints:susan-e2e-standard:run
```

Sus salidas deben ser **idénticas a las de antes del cambio**. Esta spec no altera comportamiento: si alguna cifra cambia, algo se rompió.

## Fuera de alcance

- Cualquier cambio de comportamiento del dominio.
- Rediseñar `PolicyCatalog` o los payloads.
- Tocar `SentinelEvaluatorImpl`.
- Los escenarios comentados de `susan-e2e-standard` (van en `SPEC-01`).
