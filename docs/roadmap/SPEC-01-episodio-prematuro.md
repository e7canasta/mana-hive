# SPEC-01 — El episodio se abre antes de tiempo

**El defecto con mayor impacto clínico del sistema.** Contradice, en la práctica, la frase que el director escribe en el perfil.

**Depende de:** `SPEC-00` · **Bloquea:** nada · **Tamaño:** mediano, quirúrgico
**Decisión de arquitectura que aplica:** `AD-2` (el tiempo vive en Scene; Sentinel juzga hechos ya temporizados)

---

## El problema, en el idioma de la residencia

El director escribe para José:

```kotlin
resident {
    sitting { alertAfter(Duration.ofMinutes(15)) }
}
```

y lo lee: *"si José se queda sentado más de quince minutos, avísenme"*.

Lo que el sistema hace: **avisa en el instante en que José se sienta.**

Esto es precisamente la falsa alarma que motiva el producto. En palabras de la enfermera de guardia, citada en `blueprints/jose-301-sitting-bed/README.md`:

> *"El radar de piso genera falsas alarmas. La enfermera va a la habitación y José ya está acostado o solo está en el baño."*

Hoy mana-hive reproduce ese comportamiento. No por calibración: por implementación.

## Evidencia

```
$ ./gradlew :blueprints:jose-301-e2e-pipeline:run

  Reglas de residente:
    SITTING_IN_BED: alertAfter=PT15M

  ── Pipeline: José se sienta en la cama — pipeline completa ──
  Stage 1 — Scene:     3 SceneEvents · TransitionDetected ×3   ← ningún DwellExceeded
  Stage 2 — Sentinel:  EpisodeOpened, EpisodeClosed            ← episodio abierto igual
  Stage 3 — Harbor:    Dispatch, Resolve                       ← notificación enviada
```

El escenario emite `IN_BED → SITTING_IN_BED → IN_BED`. Scene no produce ni un `DwellExceeded`. Sentinel abre episodio de todos modos, y Harbor despacha.

---

## Causa raíz

Son dos defectos encadenados.

### Defecto A — `SentinelEvaluatorImpl` usa el lookup equivocado

`engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluatorImpl.kt:95-106`

```kotlin
private fun evaluateNewEpisode(bed, state, episodes, now): EvalResult {
    val rule = calibration.ruleFor(state)          // ← legacy
        ?: return noRuleResult(state, episodes)
    return openEpisode(bed, rule, now, episodes)   // ← abre por EXISTIR la regla
}
```

Se llega acá desde `is SceneEvent.TransitionDetected`. El único requisito para abrir episodio es que exista una regla para el estado destino. El tiempo de la regla no se consulta en esta rama.

### Defecto B — las dos familias de reglas son el mismo mapa

`engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelCalibration.kt:44-54`

```kotlin
public fun from(rules: EffectiveRules): SentinelCalibration {
    val byTrigger = rules.rules.associateBy { it.trigger }
    return SentinelCalibration(
        rulesByTrigger  = byTrigger,
        transitionRules = byTrigger,   // ← misma referencia
        dwellRules      = byTrigger,   // ← misma referencia
        sceneStateRules = emptyMap(),
        ...
    )
}
```

**La estructura correcta ya existe.** Alguien ya separó `transitionRules` de `dwellRules` y escribió los lookups:

```kotlin
public fun transitionRuleFor(targetState: StateKind): AlertRule? = transitionRules[targetState]
public fun dwellRuleFor(state: StateKind): AlertRule? = dwellRules[state]
```

Pero **ninguno de los dos se llama en ningún lado del repositorio.** Son API muerta. Todo el evaluador usa el `ruleFor` legacy, y ambos mapas contienen lo mismo, así que la distinción no existe en tiempo de ejecución.

Verificable:

```bash
grep -rn "transitionRuleFor(\|dwellRuleFor(" --include="*.kt" engines/ | grep -v SentinelCalibration.kt
# sin resultados
```

---

## El diseño correcto

Una regla clínica tiene que decir **con qué hecho se dispara**, no sólo sobre qué estado. Son dos intenciones distintas que hoy no se pueden distinguir:

| Lo que dice el director | Hecho que debe disparar |
|---|---|
| *"si se queda sentado más de 15 minutos, avísenme"* | `DwellExceeded(SITTING_IN_BED)` |
| *"si pisa el borde de la cama, avísenme ya"* | `TransitionDetected(→ BED_EDGE)` |

Ambas son legítimas. La segunda es la que salva a un residente CRITICAL, donde un minuto ya es tarde. Por eso la solución **no** es mover todo a dwell: es hacer la distinción expresable.

### Regla de derivación

En el DSL de política, la forma de expresarlo ya está disponible sin inventar sintaxis nueva:

| Cómo lo escribe el director | Familia | Va a |
|---|---|---|
| `sitting { alertAfter(15.min) }` | temporizada | `dwellRules[SITTING_IN_BED]` |
| `bedEdge { alertOnEntry() }` | inmediata | `transitionRules[BED_EDGE]` |

`alertOnEntry()` es el único agregado de sintaxis. Cualquier estado configurado con `alertAfter(...)` es, por definición, temporizado, y **no** debe generar regla de transición.

---

## Cambios

### 1 · `AlertRule` gana el disparador

`platform/contracts/src/main/kotlin/com/manahive/contracts/policy/EffectiveRules.kt`

Agregar a `AlertRule` el campo que dice cómo se dispara:

```kotlin
public enum class TriggerOn {
    /** Abre al entrar al estado. Para lo que no admite espera. */
    ENTRY,
    /** Abre cuando Scene declara que se venció la permanencia. */
    DWELL,
}
```

`AlertRule.triggerOn: TriggerOn`. Sin default: obligar a que cada sitio de construcción decida. Un default silencioso reintroduce el defecto.

### 2 · `PolicyResolver` reparte en dos mapas

`engines/politica-engine/politica-domain/src/main/kotlin/com/manahive/politica/PolicyResolver.kt`

`resolveAlertRulesFromDag()` hoy produce un solo `Map<StateKind, AlertRule>`. Debe producir la regla con su `triggerOn` derivado según la tabla de arriba: presencia de `alertAfter` → `DWELL`; `alertOnEntry` → `ENTRY`.

`SentinelPolicy` pasa a llevar las dos familias por separado, o una sola lista de la que el adapter derive ambos mapas. Preferir lo segundo: una sola fuente de verdad, el reparto se hace en un lugar.

### 3 · `SentinelCalibration.from()` deja de duplicar

```kotlin
transitionRules = rules.rules.filter { it.triggerOn == TriggerOn.ENTRY }.associateBy { it.trigger },
dwellRules      = rules.rules.filter { it.triggerOn == TriggerOn.DWELL }.associateBy { it.trigger },
```

`rulesByTrigger` y `ruleFor()` quedan marcados `@Deprecated` con mensaje que apunte al lookup correcto. No borrarlos en esta spec: `evaluateUnderUmbrella` los usa para decidir escalación entre severidades y ese camino merece su propio análisis.

### 4 · `SentinelEvaluatorImpl` usa el lookup correcto

| Línea | Hoy | Debe ser |
|---|---|---|
| `:102` (`evaluateNewEpisode`, viene de `TransitionDetected`) | `calibration.ruleFor(state)` | `calibration.transitionRuleFor(state)` |
| `:191` (`evaluateDwellExceeded`, sin episodio abierto) | `calibration.ruleFor(state)` | `calibration.dwellRuleFor(state)` |
| `:197` (`isNotifiable` bajo paraguas) | `calibration.ruleFor(state)` | `calibration.dwellRuleFor(state) ?: calibration.transitionRuleFor(state)` |
| `:120` (`evaluateUnderUmbrella`, escalación) | `calibration.ruleFor(state)` | sin cambio en esta spec — dejar comentario |

### 5 · Reactivar los escenarios de Susan

`blueprints/susan-e2e-standard/src/main/kotlin/susane2e/Main.kt:171` tiene bloqueados dos escenarios con el comentario `TODO: Pendiente — sweep timing necesita debugging`.

Con este arreglo, el escenario *"Susan en baño 17 min — warning + episodio"* pasa a ser exactamente el caso que prueba el cambio. Descomentar y hacer que pase. Si sigue fallando después del arreglo, el problema del sweep es real y separado: documentarlo acá con la evidencia antes de cerrar.

---

## Criterios de aceptación

1. **José se sienta y se levanta antes del umbral → no hay episodio.**
   Escenario nuevo en `jose-301-e2e-pipeline`: `IN_BED → SITTING_IN_BED (t=0) → IN_BED (t=10m)`, con `sitting { alertAfter(15.min) }`.
   Espera: `thenEpisodeOpenCount(0)`, `thenHarborCommandCount(0)`.
   **Este escenario falla hoy** — es la prueba de regresión del defecto.

2. **José se queda sentado más del umbral → hay episodio, y llega tarde.**
   `IN_BED → SITTING_IN_BED (t=0) → IN_BED (t=20m)`.
   Espera: `thenSceneEventPresent(DwellExceeded)`, `thenEpisodeOpenCount(1)`, y que el `EpisodeOpened` tenga `at >= t0 + 15m`. La aserción temporal es parte del criterio: sin ella el test pasa por la razón equivocada.

3. **Un estado de entrada inmediata sigue disparando al entrar.**
   Perfil con `bedEdge { alertOnEntry() }`. Transición a `BED_EDGE` abre episodio en el mismo instante.

4. **Los escenarios de Susan comentados corren y pasan**, o queda documentado con evidencia por qué no.

5. `LANG=C.UTF-8 ./gradlew check` verde.

6. Las salidas de los dos blueprints se revisan a mano: **ninguna cifra debe bajar a cero por accidente.** Si un escenario que abría episodio deja de abrirlo, hay que poder explicar por qué en una frase que el director entienda.

---

## Riesgo

El cambio **reduce** la cantidad de alertas. Eso es lo buscado, pero significa que cualquier test que hoy afirme "se abrió un episodio" apoyándose en el disparo por transición va a fallar. Esos fallos son la señal de que el arreglo funciona, no de que rompió algo.

Regla para quien ejecute: **ante cada test que se ponga en rojo, decidir explícitamente** si el escenario quería "avisar al entrar" (entonces la regla necesita `alertOnEntry`) o "avisar al vencer" (entonces el test estaba verde por el motivo equivocado y hay que corregir la expectativa). No ajustar umbrales para que el test vuelva a verde.

## Fuera de alcance

- `evaluateUnderUmbrella` y la lógica de escalación entre severidades.
- La cadena `ComeBack` (`SPEC-05`).
- Presupuesto de fatiga en Harbor.
- Unificar los dos modelos de política (`SPEC-02`). Esta spec funciona con el modelo actual y sobrevive a esa decisión, porque `triggerOn` viaja en `AlertRule`, que es común a los dos.
