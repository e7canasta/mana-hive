# Handoff: José 301 Blueprint — Session 1

## Qué hicimos

### 1. SceneEngine Facade (`scene-domain/.../SceneEngine.kt`)
Single entry point que oculta `SceneInterpreter`, `ClockSweeper`, `DigitalTwin`, `DwellMarks`:
```kotlin
val engine = SceneEngine.create(calibration)
val result = engine.processWithSweep(observations, sweepIntervalSeconds = 60)
// result.facts → List<SceneFact>
// result.finalTwin → DigitalTwin
```

### 2. BDD Scenario DSL (`blueprints/.../Scenario.kt`)
DSL declarativo Given/When/Then:
```kotlin
scenario("José se sienta y se acuesta") {
    given { calibration(cal) }
    whenObserving(IN_BED at "0s" withConfidence 0.95)
    whenObserving(SITTING_IN_BED at "1h15m" withConfidence 0.92)
    whenObserving(IN_BED at "1h32m" withConfidence 0.94)
    thenExpectTransitions(3)
    thenExpectTransition(Unknown to Lying)
    thenExpectTransition(Lying to SittingInBed)
    thenExpectTransition(SittingInBed to Lying)
}
```

### 3. Fixes al Engine
- **Hysteresis desde Unknown**: `checkHysteresis` ahora salta cuando `twin.state is Unknown` — no hay "parpadeo" posible sin estado previo.
- **Validación DSL**: `requireValidDwell()` extraído como función genérica — error claro con nombre del estado y valores.

### 4. Refactoring DSL
- `requireValidDwell<K>()` — función genérica que reemplaza 3 copias de validación en `DwellThresholdsBuilder`, `ReturnDwellThresholdsBuilder`, `SceneDwellThresholdsBuilder`.

## Archivos clave

| Archivo | Qué hace |
|---------|----------|
| `scene-domain/.../SceneEngine.kt` | Facade — nuevo |
| `scene-domain/.../SceneCalibration.kt` | DSL builders + `requireValidDwell` |
| `scene-domain/.../SceneInterpreterImpl.kt` | Fix: hysteresis skip desde Unknown |
| `blueprints/.../Scenario.kt` | BDD DSL — nuevo |
| `blueprints/.../Shared.kt` | Helpers (`obsAt`, `initialTwin`, `t()`, `Memory`) |
| `blueprints/.../Main.kt` | BDD test scenarios |
| `blueprints/.../events.dat` | E1 solo (3 observaciones) |

## Estado del blueprint

- **E1 funciona**: Unknown → Lying → SittingInBed → Lying (3 transiciones)
- **ReturnDwell funciona**: 15m fuera de LYING → ReturnDwellExceeded
- **SignalLost funciona**: heartbeat timeout
- **Falta**: E2-E7 (baño, más episodios), Config 1/2/3 completas

## Pendiente para próxima sesión

1. **Expandir E1 → noche completa**: agregar E2 (baño 15m), E3 (baño 31m), E4 (sentado 4m), E5 (baño 26m), E6 (sentado 3m), E7 (levantarse)
2. **Config 1/2/3**: tester las 3 configuraciones con noche completa
3. **Dwell normal**: IN_BATHROOM > 5m → DwellExceeded (falta probar)
4. **events.dat**: archivo con todas las observaciones de la noche
5. **expected.out**: golden files con resultados esperados

## Decisiones de diseño

| Decisión | Razón |
|----------|-------|
| Estado inicial `Unknown(SCENE)` | Más honesto que `Lying` — no asumimos nada |
| Hysteresis skip desde Unknown | No hay "parpadeo" sin estado previo |
| SceneEngine como Facade | Oculta 5 conceptos internos |
| BDD DSL sobre engine | declarativo, leíble para stakeholders |
| `requireValidDwell` genérico | Una sola función para 3 builders |
