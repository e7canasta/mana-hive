# Politica Engine — Design Memory

**Fecha:** 2026-08-21
**Estado:** Primer contrato — listo para Sprint 2
**Build:** `./gradlew :engines:politica-engine:politica-domain:test`

---

## Qué es el Politica Engine

El "traductor de políticas": convierte las decisiones del System of Record (hub)
en calibraciones que el Scene Engine puede usar.

```
System of Record (hub) → PolicyChangeDetected → Politica Engine → CalibrationChanged → Scene Engine
```

## Qué hace y qué NO hace

| Hace | NO hace |
|------|---------|
| Observa cambios de política del hub | Persiste datos de política (eso es del hub) |
| Resuelve reglas efectivas por residente | Decide qué es importante (eso es de Sentinel) |
| Genera SceneCalibration para Scene Engine | Interpreta observaciones (eso es de Scene Engine) |
| Emite CalibrationChanged | Notifica (eso es de Sentinel) |

## Vocabulario

| Término | Qué significa | Ejemplo |
|---------|---------------|---------|
| **AlarmProfile** | Política de un residente | "María es high risk, walker, night_wandering" |
| **AlarmCatalog** | Catálogo de reglas | "29 transiciones, 3 niveles" |
| **SceneCalibration** | Lo que Scene Engine necesita | "Histeresis + dwell + confianza" |
| **PolicyChangeDetected** | Evento del System of Record | "La política de María cambió" |
| **CalibrationChanged** | Evento para Scene Engine | "La calibración de María cambió" |

## DSL

```kotlin
val calibration = buildSceneCalibration {
    resident(ResidentId("maria"))

    hysteresis {
        from(LYING) { to(BED_EDGE) after 1500.ms }
        from(BED_EDGE) { to(STANDING) after 1500.ms }
    }

    dwell {
        STANDING warning 4.minutes exceeded 5.minutes
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

## Archivos

| Archivo | Qué es |
|---------|--------|
| `AlarmProfile.kt` | Entidad: política de un residente |
| `AlarmCatalog.kt` | Value Object: catálogo de reglas |
| `PolicyOverride.kt` | Sealed Interface: overrides tipados |
| `SceneCalibration.kt` | Value Object: lo que Scene Engine necesita |
| `PolicyResolver.kt` | Service: resuelve reglas efectivas |
| `PolicyChangeProcessor.kt` | Interface: observa cambios, genera eventos |
| `DefaultPolicyChangeProcessor.kt` | Implementación: default del processor |
| `CalibrationProvider.kt` | Port: lo que Scene Engine necesita |
| `SceneCalibrationDsl.kt` | DSL: construye SceneCalibration |
| `DurationExtensions.kt` | Extensiones: 1500.ms, 4.minutes |
| `PoliticaDsl.kt` | Marker: @PoliticaDsl |

## Contrato con Scene Engine

```kotlin
// Lo que Scene Engine necesita de Politica:
interface CalibrationProvider {
    fun getCalibration(residentId: ResidentId): Explained<SceneCalibration?>
    fun getAllCalibrations(): Map<ResidentId, SceneCalibration>
}

// Lo que Politica emite:
data class CalibrationChanged(
    val residentId: ResidentId,
    val at: Instant,
    val calibration: SceneCalibration,
)
```

## Refactoring aplicado (Fowler)

| Smell | Antes | Después | Patrón |
|-------|-------|---------|--------|
| **Dispensable** | `EffectivePolicy` (intermediario) | Eliminado | Remove Dispensable |
| **Primitive Obsession** | `PolicyOverride.value: String` | `PolicyOverride` sealed interface | Replace Primitive with Value Object |
| **Switch Statement** | `if/else` chain para source | `when` expression | Decompose Conditional |

## Decisiones de diseño

1. **`SceneCalibration`** (no `SceneCalibrationForResident`) — Fowler: "nombra por qué es, no por dónde se usa"
2. **`PolicyChangeDetected`** (no `PolicyChangedEvent`) — sigue el patrón del proyecto: `TransitionDetected`, `DwellWarning`
3. **`@PoliticaDsl`** — sigue el patrón de `@SceneEngineDsl`
4. **`Explained<T>`** en `CalibrationProvider` — sigue el patrón del kernel
5. **`buildSceneCalibration { }`** — sigue el patrón de `buildCalibration { }`
6. **`PolicyOverride` sealed interface** — type safety, no Strings

## Orden de implementación (Sprint 2)

```
1. PoliticaEngine: AlarmProfile, AlarmCatalog, PolicyResolver
   → politica-domain

2. PolicyChangeProcessor: observa PolicyChangeDetected, emite CalibrationChanged
   → politica-domain

3. CalibrationProvider: port para Scene Engine
   → politica-domain

4. Scene Engine: SceneCalibration port → CalibrationProvider
   → scene-domain

5. Wire: PolicyChangeProcessor → CalibrationProvider → SceneInterpreter
   → politica-service
```

---

*Primer contrato — listo para Sprint 2.*
