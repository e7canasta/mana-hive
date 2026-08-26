# SPEC-05 — Cerrar la cadena del dwell inverso (ComeBack)

**Depende de:** `SPEC-02`, y coordina con `SPEC-01` · **Tamaño:** mediano
**Decisión de arquitectura:** `AD-3` (se mantiene `ComeBackExceeded` como tipo propio)

---

## Corrección de un supuesto

Documentación previa — incluida la sesión con la dirección — declaraba el dwell inverso como *"diseñado y documentado; no implementado"*. **Es falso.** Está implementado, bajo el nombre `ComeBack`. Lo que falta son los dos extremos de la cadena.

`blueprints/jose-301-sitting-bed/README.md` sigue afirmando que es un *"Feature Necesario"* pendiente. Corregirlo es parte de esta spec.

## Estado verificado

| Eslabón | Archivo | Estado |
|---|---|---|
| Hecho del dominio | `contracts/scene/SceneEvent.kt:114,122` — `ComeBackWarning`, `ComeBackExceeded` | ✅ |
| Estado del gemelo | `scene/core/DigitalTwin.kt:62,67` — `leftStateAt`, `baselineState` | ✅ |
| Detección | `scene/sweeper/ClockSweeperImpl.checkComeBack()` — con marcas idempotentes por evento de salida | ✅ |
| Calibración de Scene | `scene/calibration/SceneCalibration.kt:117` — bloque `comeBack {}` | ✅ |
| Config batch | `scene-batch/config/BatchConfigLoader.kt:51` — clave YAML `comeBack:` | ✅ |
| Aserciones BDD | `scene-bdd/Scenario.kt:356` — `comeBackExceeded(count)` | ✅ |
| **DSL de política** | `contracts/policy/DagDsl.kt` | ❌ no lo puede expresar |
| **Transporte** | `contracts/policy/PolicyCalibration.kt` — `ScenePolicy` | ❌ no lo lleva |
| **Juicio clínico** | `sentinel/SentinelEvaluatorImpl.kt:41` — el `when` sobre `SceneEvent` | ❌ no tiene rama |

Verificable:

```bash
grep -rn "comeBack\|ComeBack" --include="*.kt" \
  platform/contracts/src/main/kotlin/com/manahive/contracts/policy/ \
  engines/politica-engine/ engines/sentinel/ | grep -v "/build/"
# sin resultados relevantes
```

**Scene sabe emitir el hecho; el director no lo puede pedir y Vigilancia lo ignora.** La pregunta de la enfermera — *"avisame si no vuelve a la cama"* — no tiene camino de punta a punta.

## La decisión que quedó a medias

`jose-301-sitting-bed/README.md` preveía que el dwell inverso fuese **otro `DwellExceeded`** en el stream, con esta tabla explícita:

> | Componente | Cambia | No Cambia |
> | `Sentinel` | — | ✅ (ve otro DwellExceeded) |
>
> *"Sentinel no distingue dwell normal de inverso. Es otro `DwellExceeded` en el stream."*

La implementación eligió un tipo de evento propio — y **no se siguió la consecuencia**, que era enseñarle a Sentinel a juzgarlo.

`AD-3` ratifica el tipo propio: *"lleva mucho en el baño"* y *"no volvió a la cama"* son preguntas clínicas distintas y merecen severidad y condición de cierre distintas. Fundirlas ahorra una rama de `when` y cuesta poder expresarlas por separado.

---

## Cambios

### 1 · El director lo puede pedir

`platform/contracts/src/main/kotlin/com/manahive/contracts/policy/DagDsl.kt`

```kotlin
resident {
    comeBackTo(StateKind.LYING) {
        warningAfter(Duration.ofMinutes(15))
        alertAfter(Duration.ofMinutes(20))
        severity(Severity.WARNING)
        closure(ClosureCondition.STAFF_OR_SAFE)
    }
}
```

Se lee: *"si sale de la cama y no vuelve en veinte minutos, avísenme"*. Ésa es la frase de la enfermera; el DSL tiene que sonar así.

El estado de referencia es parámetro, no fijo en `LYING`. `baselineState` ya lo es en el gemelo; el DSL no debe ser más pobre que el modelo.

### 2 · Viaja en la política

`ScenePolicy` gana:

```kotlin
val comeBackThresholds: Map<StateKind, DwellThreshold> = emptyMap(),
```

Nombre idéntico al de `SceneCalibration.comeBackThresholds`, para que el adapter sea una asignación y no una traducción.

`PolicyResolver.resolve()` lo puebla desde el catálogo y los overrides, con la misma precedencia que los dwell normales.

`toSceneCalibration()` (ver `SPEC-04`) lo copia al bloque `comeBack {}`.

### 3 · Sentinel lo juzga

`engines/sentinel/sentinel-domain/.../SentinelEvaluatorImpl.kt`, en el `when (fact)` de la línea 41:

```kotlin
is SceneEvent.ComeBackExceeded -> { ... }   // abre episodio
is SceneEvent.ComeBackWarning  -> { ... }   // señal informativa, sin episodio
```

Simetría a respetar con lo que ya existe:

- `ComeBackExceeded` se comporta como `DwellExceeded`: abre episodio si no hay uno abierto; si lo hay, emite `UmbrellaEvent`.
- `ComeBackWarning` se comporta como `DwellWarning`: emite `DwellPreWarning`, no abre episodio.

`SentinelCalibration` gana `comeBackRules: Map<StateKind, AlertRule>` y su lookup `comeBackRuleFor(baseline)`.

**Coordinación con `SPEC-01`:** ésta es una tercera familia de reglas, junto a `transitionRules` y `dwellRules`. Si `SPEC-01` ya introdujo `TriggerOn`, agregar el valor `COME_BACK` en vez de un mapa suelto. Si se ejecutan en paralelo, acordar primero cuál de las dos define el enum.

### 4 · Un `when` que no se puede olvidar

El `when (fact)` de `SentinelEvaluatorImpl` es exhaustivo hoy sólo porque el compilador lo exige. Verificar que **ninguna** de las ramas nuevas se resuelva con `else`. Vale la misma regla que `SPEC-00` tarea 3: un `else` acá es un hecho del dominio que Vigilancia va a ignorar en silencio.

### 5 · El blueprint que lo prueba

`blueprints/jose-301-sitting-bed/` tiene la noche completa de José — 23 observaciones, 7 episodios, con `expected3.out` esperando 28 hechos incluyendo dwell inverso. Es el escenario natural.

Casos que deben quedar cubiertos, tomados del propio README:

| Caso | Coreografía | Espera |
|---|---|---|
| La mina explota | sale de `LYING` 23:15, no vuelve hasta 23:32 (17 min > 15) | `ComeBackExceeded(LYING)` a las 23:30, y **un solo** hecho |
| La mina se desarma | sale de `LYING` 03:50, vuelve 03:54 (4 min < 15) | ningún `ComeBackExceeded` |
| La mina no se re-arma | tras explotar, vuelve a `LYING` y vuelve a salir | mina nueva, un hecho nuevo |
| Sentinel juzga | cualquiera de los anteriores con regla configurada | `EpisodeOpened` con la severidad del catálogo |

El tercero es el que ejercita la idempotencia por marca (`DwellMarkKey` con `leftStateAt` como identidad) y es el que más fácil se rompe.

---

## Criterios de aceptación

1. Un perfil escrito con `comeBackTo(LYING) { alertAfter(20.min) }` produce, sin intervención manual, un `ComeBackExceeded` en Scene **y** un `EpisodeOpened` en Sentinel.
2. La mina no dispara si el residente vuelve antes del umbral.
3. La mina dispara **una sola vez** por salida, aunque el barrido corra muchas veces.
4. Una salida posterior arma una mina nueva.
5. `ComeBackWarning` no abre episodio.
6. El `when` de `SentinelEvaluatorImpl` no tiene `else`.
7. `blueprints/jose-301-sitting-bed/README.md` deja de declarar el dwell inverso como pendiente y describe lo que existe. Ver `SPEC-07`.
8. `LANG=C.UTF-8 ./gradlew check` verde.

## Fuera de alcance

- `expected3.out` con las 28 líneas exactas: verificar que corresponden al comportamiento nuevo, pero si difieren, corregir el archivo esperado **sólo tras entender la diferencia**, nunca regenerándolo a ciegas.
- El dwell de estado de escena (`checkSceneDwell`, campos `staff`, `wheelchair`, `walker`, `bed.left`, `bed.right`). Es un mecanismo hermano que tampoco tiene juicio en Sentinel, pero es otra spec.
