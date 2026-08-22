# Scene Engine + Politica Engine — Sprint 2: Diseño

**Fecha:** 2026-08-21
**Estado:** ✅ COMPLETADO — Todas las historias, reviews, y refactoring
**Build:** `./gradlew :engines:politica-engine:politica-domain:test :engines:scene-engine:scene-domain:test` → BUILD SUCCESSFUL
**Tests:** 19 specs passing

---

## 1. El insight que cambia todo

### Lo que el negocio nos dice

> *"¿Qué reglas son efectivas para un residente y qué configuración estaba vigente cuando ocurrió un evento?"*
> — ctx-politica.md

**Vernon dice**: "¿Estás viendo? Cada residente tiene **sus propias reglas**. No es el mismo interprete para todos."

### Nuestro vocabulario

| Término | Qué significa | Ejemplo |
|---------|---------------|---------|
| **Histeresis** | Tiempo para confirmar un cambio en el mundo | "María pasó de acostada a de pie — ¿lo confirmo después de 1.5s?" |
| **Dwell** | Tiempo en un estado antes de avisar | "María lleva 5 minutos de pie — ¿aviso después de 5 min?" |
| **Confianza** | Qué tan seguros estamos de una observación | "El sensor dice que María está de pie — ¿con qué confianza?" |
| **Estado** | Lo que está pasando en el mundo | "María está de pie" |
| **Transición** | Cambio de estado | "María pasó de acostada a de pie" |

### Lo que NO es nuestro vocabulario

| Término | Por qué no es nuestro |
|---------|----------------------|
| Alarma | Eso es de Sentinel |
| Alerta | Eso es de Sentinel |
| Notificación | Eso es de Sentinel |

### El cambio de perspectiva

| Antes (Sprint 1) | Después (Sprint 2) |
|------------------|-------------------|
| Un interprete para todos | Un interprete **por residente** |
| Misma tabla de transiciones | Tabla **según calibración del residente** |
| Mismos umbrales de permanencia | Umbrales **según política del residente** |
| Gemelo = cama + estado + señal | Gemelo = cama + estado + señal + **calibración** |
| 5 estados | 11 estados |

---

## 2. Arquitectura: Dos engines, un contrato

### Diagrama de contextos

```
┌─────────────────────────────────────────────────────────────────────┐
│                     MANA-HIVE — SPRINT 2                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     HUB (System of Record)                   │   │
│  │  ├── AlarmProfile (entidad versionada)                      │   │
│  │  ├── AlarmCatalog (catálogo TOML)                           │   │
│  │  └── Emite: PolicyChanged                                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │                                         │
│                           ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     POLITICA ENGINE (nuevo)                  │   │
│  │  ├── PolicyChangeProcessor (Integration Pattern)            │   │
│  │  ├── PolicyResolver (Service puro)                          │   │
│  │  ├── CalibrationProvider (Port)                             │   │
│  │  └── Emite: CalibrationChanged                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │                                         │
│                           ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     SCENE ENGINE                             │   │
│  │  ├── SceneInterpreter (por residente)                       │   │
│  │  ├── DigitalTwin (con calibración)                          │   │
│  │  ├── ClockSweeper (con DwellCatalog)                        │   │
│  │  └── Emite: SceneFact                                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │                                         │
│                           ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     SENTINEL                                  │   │
│  │  └── Juzga SceneFact según reglas clínicas                  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Flujo de datos

```
1. Hub actualiza AlarmProfile de María
   → Emite PolicyChanged(residentId: "maria", version: 2)

2. PolicyChangeProcessor recibe PolicyChangeDetected
   → Resuelve SceneCalibration via PolicyResolver
   → Convierte a SceneCalibration

3. PolicyChangeProcessor emite CalibrationChanged
   → Scene Engine recibe
   → Regenera SceneInterpreter con nueva calibración

4. Scene Interpreter usa SceneCalibration
   → Histeresis, dwell, confianza del residente
   → Emite SceneFact (TransitionDetected, DwellWarning, etc.)

5. Sentinel recibe SceneFact
   → Juzga si hay que alertar
```

---

## 3. Modelo de clases — Estado real del código

### Contracts (`platform/contracts/.../policy/`)

```kotlin
// PolicyCalibration.kt — Value Object compartido entre contextos
data class PolicyCalibration(
    val residentId: ResidentId,
    val hysteresis: Map<TransitionKey, Duration>,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
    val confidence: ConfidenceConfig,
    val source: PolicySource,
)

data class ConfidenceConfig(
    val minConfidence: Map<StateKind, Double>,
    val heartbeatTimeout: Duration,
)

data class DwellThreshold(
    val warning: Duration,
    val exceeded: Duration,
)

data class TransitionKey(val from: StateKind, val to: StateKind)

// AlarmProfile.kt — Entidad del hub
data class AlarmProfile(
    val residentId: ResidentId,
    val riskLevel: RiskLevel,
    val mobilityAid: MobilityAid,
    val autopilot: Boolean,
    val mode: PolicyMode,
    val templateId: TemplateId?,
    val overrides: Map<RuleId, PolicyOverride>,
    val catalogVersion: CatalogVersion,
    val validFrom: Instant,
)

// AlarmCatalog.kt — Catálogo TOML
data class AlarmCatalog(
    val transitions: Map<TransitionKey, Duration>,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
    val templates: Map<TemplateId, Template>,
    val version: CatalogVersion,
)

// PolicyOverride.kt — Sealed Interface
sealed interface PolicyOverride {
    val ruleId: RuleId
    val param: String
    data class DurationOverride(...) : PolicyOverride
}

// Domain Events
data class PolicyChangeDetected(
    val residentId: ResidentId,
    val at: Instant,
    val snapshot: AlarmProfile,
)

data class CalibrationChanged(
    val residentId: ResidentId,  // delegated property (DRY)
    val at: Instant,
    val calibration: PolicyCalibration,
)
```

### Politica Engine (`politica-domain`)

```kotlin
// PolicyChangeProcessor.kt — Integration Pattern
interface PolicyChangeProcessor {
    fun process(event: PolicyChangeDetected, now: Instant): PolicyChangeResult
}

data class PolicyChangeResult(
    val calibration: PolicyCalibration,
    val emittedEvents: List<CalibrationChanged>,
)

// DefaultPolicyChangeProcessor.kt — Implementation
class DefaultPolicyChangeProcessor(
    private val resolver: PolicyResolver = PolicyResolver,
    private val catalog: AlarmCatalog,
) : PolicyChangeProcessor { ... }

// PolicyResolver.kt — Service (internal)
internal object PolicyResolver {
    fun resolve(catalog: AlarmCatalog, profile: AlarmProfile): PolicyCalibration
}

// CalibrationProvider.kt — Port
interface CalibrationProvider {
    fun getCalibration(residentId: ResidentId): Explained<PolicyCalibration?>
    fun getAllCalibrations(): Map<ResidentId, PolicyCalibration>
}
```

### Scene Engine (`scene-domain`)

```kotlin
// SceneInterpreter.kt
data class SceneCalibration(
    val table: TransitionTable,
    val minConfidence: Map<StateKind, Double>,
    val heartbeatTimeout: Duration,
    val dwellThresholds: Map<StateKind, DwellThreshold>,
)

// DigitalTwin.kt
data class DigitalTwin(
    val bed: BedId,
    val night: NightId,
    val occupant: ResidentId?,
    val state: PersonState,
    val stateSince: Instant,
    val signal: SignalHealth,
    val calibration: SceneCalibration? = null,  // ← Sprint 2
)

// PolicyCalibrationAdapter.kt — Extension function
fun PolicyCalibration.toSceneCalibration(): SceneCalibration = SceneCalibration(
    table = TransitionTable.from(overrides = hysteresis),
    minConfidence = confidence.minConfidence,
    heartbeatTimeout = confidence.heartbeatTimeout,
    dwellThresholds = dwellThresholds,
)

// TransitionTable.kt
data class TransitionTable(
    private val legal: Map<TransitionKey, Duration>,
) {
    companion object {
        fun from(
            base: TransitionTable = RELEASE_1,
            overrides: Map<TransitionKey, Duration> = emptyMap(),
        ): TransitionTable
    }
}
```

### Shared Utilities (`platform/contracts/.../shared/`)

```kotlin
// DurationExtensions.kt — Shared across all DSLs
val Int.ms: Duration get() = Duration.ofMillis(toLong())
val Int.seconds: Duration get() = Duration.ofSeconds(toLong())
val Int.minutes: Duration get() = Duration.ofMinutes(toLong())
val Int.hours: Duration get() = Duration.ofHours(toLong())

// HeartbeatBuilder.kt — Shared across all DSLs
@PolicyDsl
class HeartbeatBuilder {
    var timeout: Duration = Duration.ofSeconds(90)
    fun build(): Duration = timeout.also {
        require(!it.isZero) { "heartbeat timeout must be > 0" }
    }
}
```

---

## 4. Contrato entre contextos

### Politica → Scene Engine

```kotlin
// Port que Scene Engine usa
interface CalibrationProvider {
    fun getCalibration(residentId: ResidentId): Explained<PolicyCalibration?>
    fun getAllCalibrations(): Map<ResidentId, PolicyCalibration>
}

// Evento que Politica emite
data class CalibrationChanged(
    val residentId: ResidentId,
    val at: Instant,
    val calibration: PolicyCalibration,
)
```

### Hub → Politica

```kotlin
// Evento que el hub emite (en contracts/policy/)
data class PolicyChangeDetected(
    val residentId: ResidentId,
    val at: Instant,
    val snapshot: AlarmProfile,
)
```

---

## 5. Integration Patterns (Hohpe & Woolf)

### PolicyChangeProcessor

El PolicyChangeProcessor es un **Message Router** + **Content Enricher**:

1. **Message Router**: recibe PolicyChangeDetected y lo rutea al processor correcto
2. **Content Enricher**: enriquece los datos raw del hub con SceneCalibration
3. **Event Driven**: reacciona a eventos, emite eventos

```
PolicyChangeDetected (raw) → PolicyChangeProcessor → CalibrationChanged (enriched)
```

### CalibrationProvider

El CalibrationProvider es un **Port** (Vernon):

1. **Anti-Corruption Layer**: Scene Engine no conoce Politica internamente
2. **Dependency Inversion**: Scene Engine depende de abstracción
3. **Adapter Pattern**: Politica implementa el port

```
Scene Engine → CalibrationProvider (interface) → PoliticaEngine (implementation)
```

---

## 6. DSL — Estado real

### PolicyCalibrationDsl (en contracts)

```kotlin
val calibration = buildPolicyCalibration {
    resident(ResidentId("maria"))

    hysteresis {
        from(LYING) { to(BED_EDGE) after 1500.ms }
        from(BED_EDGE) { to(STANDING) after 1500.ms }
    }

    dwell {
        STANDING warning 4.minutes exceeded 5.minutes
        BED_EDGE warning 2.minutes exceeded 3.minutes
        BED_EDGE warning 2.minutes exceeded 3.minutes
    }

    confidence {
        BED_EDGE min 0.9
        STANDING min 0.85
    }

    heartbeat {
        timeout = 90.seconds
    }

    source(PolicySource.TEMPLATE)
}
```

### SceneCalibrationDsl (en scene-domain)

```kotlin
val sceneCalibration = buildSceneCalibration {
    hysteresis {
        from(LYING) { to(BED_EDGE) after 1500.ms }
    }
    dwell {
        STANDING warning 4.minutes exceeded 5.minutes
    }
    confidence {
        BED_EDGE min 0.9
    }
    heartbeat {
        timeout = 90.seconds
    }
}
```

### Idioma unificado (Fowler: Inconsistent DwellIdiom)

Todas las DSLs usan `warning X exceeded Y` (antes variaba entre `after`, `exceeded`, `warn`).

---

## 7. Refactoring aplicado (Kotlin Expert + Fowler/Vernon + Fowler Smells)

### Kotlin Expert Review

| Issue | Solución |
|-------|----------|
| HeartbeatBuilder validation bypass | Custom setter con `require(!it.isZero)` |
| Silent template fallback | Throws `IllegalArgumentException` if template not found |
| Redundant residentId in CalibrationChanged | Delegated property (DRY) |

### Fowler/Vernon Design Review

| Issue | Solución |
|-------|----------|
| `SceneCalibration` missing FQN | `SceneCalibration` in scene-domain (distinct from `PolicyCalibration`) |
| DSL dwell inconsistency | Unified `warning X exceeded Y` across all DSLs |
| Zero-warning silent fallback | Throws error if dwell threshold has no warning |
| Nullable residentId in builder | `var residentId: ResidentId? = null` with validation |
| NightSummary invariants | Documented invariants in KDoc |
| TransitionTable hardcoded | Added `TransitionTable.from()` factory |
| PolicyResolver default catalog | Moved to `DefaultPolicyChangeProcessor` |

### Fowler Refactoring Smells

| # | Smell | Antes | Después |
|---|-------|-------|---------|
| 1 | Duplicated Code — Duration extensions | Copy-pasted in 3 DSLs | `contracts/shared/DurationExtensions.kt` |
| 2 | Duplicated Code — HeartbeatBuilder | Copy-pasted in 3 DSLs | `contracts/shared/HeartbeatBuilder.kt` |
| 4 | Inconsistent Naming — Spanish | `evolucionar`, `nuevoTwin` | `evolve`, `updatedTwin` |
| 8+16 | Speculative Generality — grace | `postTransitionGrace` unused | Eliminated |
| 12 | Feature Envy — Adapter | Manual map conversion | `TransitionTable.from(overrides)` |
| 14 | Inconsistent Naming — DslMarker | `@SceneEngineDsl` | `@SceneDsl` (matches `@PolicyDsl`) |
| 15 | Primitive Obsession — Pair | `Pair<StateKind, StateKind>` | `TransitionKey` (value object) |

### Shared Utilities created

```
platform/contracts/src/main/kotlin/com/manahive/contracts/shared/
├── DurationExtensions.kt   ← Int.ms, Int.seconds, Int.minutes, Int.hours
└── HeartbeatBuilder.kt     ← shared builder con validación
```

---

## 8. Domain Events

| Evento | Emisor | Significado | En contracts |
|--------|--------|-------------|--------------|
| `PolicyChangeDetected` | Hub | "La politica de un residente cambio" | ✅ |
| `CalibrationChanged` | Politica Engine | "La calibración de un residente cambio" | ✅ |
| ~~`PolicyChanged`~~ | Hub | Eliminado (dead code, duplicado de PolicyChangeDetected) | — |

---

## 9. Invariantes

1. Un perfil esta ligado a `ResidentId`, nunca a `BedId`.
2. Como maximo una version es valida para un residente en un instante.
3. Un dia sin observacion no es un dia con observacion cero.
4. Una regla `fall` bloqueada no puede desactivarse por preset, plantilla u override.
5. Un override debe referir a una regla de catalogo y un parametro declarado.
6. HeartbeatBuilder timeout must be > 0 (validated at construction time).
7. PolicyResolver throws if template not found (no silent fallback).

---

## 10. Resumen ejecutivo

**El Politica Engine traduce** las decisiones del hub en calibraciones que el Scene Engine puede usar.

**El Scene Engine recibe** `SceneCalibration` (via adapter) y la usa para interpretar observaciones.

**El flujo es**: Hub → PolicyChangeDetected → Politica Engine → CalibrationChanged → Scene Engine → SceneFact → Sentinel.

**El cambio con Sprint 2**: más estados, más transiciones, más dwell, política por residente.

**El código está**: Kotlin expert reviewed, Fowler/Vernon design reviewed, Fowler smells fixed, tests green.

---

*Documento actualizado el 2026-08-22 — Sprint 2 completado con código real.*
