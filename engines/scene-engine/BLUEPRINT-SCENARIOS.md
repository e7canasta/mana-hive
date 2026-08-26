# Blueprint Scenarios — Scene Engine

Guia de referencia para los escenarios de prueba del **Scene Engine** usando `scene-batch`.

## Vision General

Los blueprint scenarios son "rafagas de observaciones" que ejercitan cada camino del motor,
desde filtros simples (confianza, hysteresis) hasta escenarios clinicos completos.

Cada escenario tiene 3 archivos:

```
NN-nombre-del-escenario/
  run.yaml        <- configuracion del batch (calibracion, residente, monitor)
  events.dat      <- entrada: ráfaga de observaciones del sensor
  expected.out    <- salida esperada: hechos que el motor debe producir
```

## Formato de events.dat

```
t=<offset> OBS <KIND> confidence=<valor>
```

- `t=<offset>`: tiempo relativo al inicio. Formatos: `0s`, `2s`, `4m30s`, `1h5m`
- `OBS`: tipo de observacion (siempre OBS para este batch)
- `<KIND>`: `ObservationKind` del contrato (IN_BED, BED_EDGE, STANDING, etc.)
- `confidence=<valor>`: nivel de confianza del sensor (0.0 a 1.0)
- Lineas con `#` son comentarios. Lineas vacias se ignoran.

### ObservationKind validos

| Tipo | Persona | Escena |
|------|---------|--------|
| `IN_BED` | PersonState.Lying | - |
| `SITTING_IN_BED` | PersonState.SittingInBed | - |
| `ATTEMPTING_EXIT` | PersonState.AttemptingExit | - |
| `BED_EDGE` | PersonState.BedEdge | - |
| `STANDING` | PersonState.Standing | - |
| `IN_BATHROOM` | PersonState.InBathroom | - |
| `IN_ROOM` | PersonState.InRoom | - |
| `IN_HALLWAY` | PersonState.InHallway | - |
| `OUTDOOR` | PersonState.Outdoor | - |
| `IN_CHAIR` | PersonState.InChair | - |
| `IN_WHEELCHAIR` | PersonState.InWheelchair | - |
| `OUT_OF_ROOM` | PersonState.Absent | - |
| `STAFF_ENTERED` | - | staff = Present |
| `STAFF_LEFT` | - | staff = NotPresent |
| `STAFF_IN_REACH` | - | staff = InReach |
| `WHEELCHAIR_PRESENT` | - | wheelchair = Present |
| `WHEELCHAIR_ABSENT` | - | wheelchair = NotPresent |
| `WALKER_PRESENT` | - | walker = Present |
| `WALKER_ABSENT` | - | walker = NotPresent |
| `BED_RAILS_UP` | - | bed.rails = Up |
| `BED_RAILS_DOWN` | - | bed.rails = Down |

## Formato de expected.out

```
t=<offset>  <TIPO> <detalles>           # <- evento <n>
```

### Tipos de hechos esperados

| Tipo | Ejemplo | Description |
|------|---------|-------------|
| `TRANSITION` | `TRANSITION LYING -> BED_EDGE` | Transicion de estado de persona aceptada |
| `DWELL_WARNING` | `DWELL_WARNING STANDING` | Warning: se acerca al umbral de dwell |
| `DWELL_EXCEEDED` | `DWELL_EXCEEDED STANDING` | Exceeded: umbral de dwell cruzado |
| `SIGNAL_LOST` | `SIGNAL_LOST` | Monitor desconectado (heartbeat timeout) |
| `SIGNAL_RECOVERED` | `SIGNAL_RECOVERED` | Monitor reconectado |
| `SCENE_CHANGED` | `SCENE_CHANGED staff` | Cambio de estado de escena |
| `SCENE_DWELL_WARNING` | `SCENE_DWELL_WARNING staff` | Warning de dwell de escena |
| `SCENE_DWELL_EXCEEDED` | `SCENE_DWELL_EXCEEDED staff` | Exceeded de dwell de escena |
| `STAFF_PRESENCE` | `STAFF_PRESENCE` | Presencia de personal detectada |
| `DISCARD` | `DISCARD` | Observacion descartada (descarto esperado) |

## Pipeline del Motor (BatchProcessor)

Para cada evento en `events.dat`, el batch processor ejecuta:

```
1. SWEEP (ClockSweeper)
   - Revisa dwells de persona (DwellWarning, DwellExceeded)
   - Revisa dwells de escena (SceneDwellWarning, SceneDwellExceeded)
   - Revisa heartbeat timeout (SignalLost)
   - Usa DwellMarks para idempotencia (no duplica hechos)

2. INTERPRET (SceneInterpreter)
   - Chequeo 1: Confidence (confianza >= umbral minimo)
   - Chequeo 2: Sensor Recovery (si signal.lost -> emite SignalRecovered)
   - Chequeo 3: Duplicate (mismo estado -> no-op)
   - Chequeo 4: Legal Transition (transicion permitida en DAG)
   - Chequeo 5: Hysteresis (tiempo en estado >= minimo requerido)
   - Si todo pasa: emite TransitionDetected
```

### Causas de descarte (DiscardCause)

| Causa | Quando ocurre |
|-------|---------------|
| `CONFIDENCE_TOO_LOW` | confidence < umbral minimo para el estado |
| `DUPLICATE` | Observation mapea al mismo estado actual |
| `ILLEGAL_TRANSITION` | Transicion no permitida en el DAG/TransitionTable |
| `HYSTERESIS_NOT_MET` | Tiempo en estado < hysteresis requerida |
| `NO_OCCUPANT` | No hay residente asignado a la cama |
| `STAFF_PRESENT` | Personal detectado (suprime ciertas alarmas) |

## Los 11 Escenarios

### 01 — Confidence Filter
**Objetivo**: Verificar que observaciones por debajo del umbral de confianza son descartadas.

**Camino del motor**: `SceneInterpreter.checkConfidence()` -> `CONFIDENCE_TOO_LOW`

**Config clave**:
```yaml
confidence:
  BED_EDGE: 0.8    # requiere 0.8 minimo
  STANDING: 0.7    # requiere 0.7 minimo
```

**events.dat**:
```
t=0s    OBS IN_BED confidence=0.95       # DISCARD (duplicate)
t=2s    OBS BED_EDGE confidence=0.70     # DISCARD (0.70 < 0.8)
t=4s    OBS BED_EDGE confidence=0.85     # TRANSITION LYING -> BED_EDGE
t=6s    OBS STANDING confidence=0.60     # DISCARD (0.60 < 0.7)
t=8s    OBS STANDING confidence=0.75     # TRANSITION BED_EDGE -> STANDING
```

**expected.out**: 2 transiciones aceptadas.

---

### 02 — Hysteresis Block
**Objetivo**: Verificar que transiciones demasiado rapidas son bloqueadas.

**Camino del motor**: `SceneInterpreter.checkHysteresis()` -> `HYSTERESIS_NOT_MET`

**Valores de hysteresis (RELEASE_2)**:
- LYING -> BED_EDGE: 1.5s
- BED_EDGE -> STANDING: 1.2s

**events.dat**:
```
t=0s    OBS IN_BED confidence=0.95       # DISCARD (duplicate)
t=1s    OBS BED_EDGE confidence=0.90     # DISCARD (1s < 1.5s)
t=3s    OBS BED_EDGE confidence=0.90     # TRANSITION (3s >= 1.5s)
t=4s    OBS STANDING confidence=0.90     # DISCARD (1s < 1.2s)
t=6s    OBS STANDING confidence=0.90     # TRANSITION (3s >= 1.2s)
```

**expected.out**: 2 transiciones aceptadas.

---

### 03 — Duplicate Discard
**Objetivo**: Verificar idempotencia — mismo estado = no-op.

**Camino del motor**: `SceneInterpreter.checkDuplicate()` -> `DUPLICATE`

**events.dat**: 3x IN_BED, 1x BED_EDGE, 2x BED_EDGE

**expected.out**: 1 transicion aceptada.

---

### 04 — Illegal Transition
**Objetivo**: Verificar que transiciones no permitidas en el DAG son rechazadas.

**Camino del motor**: `SceneInterpreter.checkIllegalTransition()` -> `ILLEGAL_TRANSITION`

**Regla**: LYING solo puede ir a SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE.

**events.dat**: 3 transiciones ilegales (STANDING, IN_HALLWAY, OUTDOOR) + 2 legales.

**expected.out**: 2 transiciones aceptadas.

---

### 05 — Dwell Warning
**Objetivo**: Verificar que el sweeper emite DwellWarning al umbral de warning.

**Camino del motor**: `ClockSweeper.checkDwell()` -> `DwellWarning`

**Config**: STANDING warning=4m, exceeded=5m

**events.dat**: Secuencia para llegar a STANDING, luego salto temporal a +4m.

**expected.out**: 1 transicion + 1 DwellWarning.

---

### 06 — Dwell Exceeded
**Objetivo**: Verificar que el sweeper emite DwellExceeded al cruzar el umbral.

**Camino del motor**: `ClockSweeper.checkDwell()` -> `DwellExceeded`

**events.dat**: Salto temporal a +4m (warning) y +5m (exceeded).

**expected.out**: 1 transicion + 1 DwellWarning + 1 DwellExceeded.

---

### 07 — Signal Lost
**Objetivo**: Verificar deteccion de perdida de senal del monitor.

**Camino del motor**: `ClockSweeper.checkSignalLost()` -> `SignalLost`

**Config**: heartbeat timeout = 90s

**Logica**: lastHeartbeat se actualiza cuando se procesa una observacion.
Si no hay observaciones por >90s, se emite SignalLost.

**expected.out**: 1 transicion + 1 SignalLost.

---

### 08 — Signal Recovery
**Objetivo**: Verificar que cuando el monitor vuelve, se emite SignalRecovered.

**Camino del motor**: `SceneInterpreter.recoverSensor()` -> `SignalRecovered`

**Logica**: Si signal.lost=true y llega una observacion (no HEARTBEAT),
se recupera el sensor y se emite SignalRecovered ANTES de evaluar la transicion.

**expected.out**: 1 transicion + 1 SignalLost + 1 SignalRecovered + 1 transicion.

---

### 09 — Staff Presence
**Objetivo**: Verificar cambios de estado de escena (SceneState).

**Camino del motor**: `SceneInterpreter.interpretSceneEvent()` -> `SceneStateChanged`

**Logica**: STAFF_ENTERED no cambia PersonState, cambia SceneState.staff.
STAFF_LEFT revierte el cambio.

**expected.out**: 2 SCENE_CHANGED + 1 TRANSITION.

---

### 10 — Night Wandering (Escenario Clinico Completo)
**Objetivo**: Escenario realista de residente que se levanta, va al baño, y regresa.

**Camino del motor**: Combina interpreter + sweeper con dwells de IN_BATHROOM.

**Config**: IN_BATHROOM warning=3m, exceeded=4m

**events.dat**: 9 eventos cubriendo: transicion + dwell warning + exceeded + retorno.

**expected.out**: 8 hechos (transiciones + dwells).

---

### 11 — Return to Bed (Auto-Recovery)
**Objetivo**: Residente que se levanta y vuelve rapido sin incidente.

**Camino del motor**: Secuencia completa de ida y vuelta.

**events.dat**: 5 eventos: LYING -> BED_EDGE -> STANDING -> BED_EDGE -> LYING.

**expected.out**: 4 transiciones.

## Como Ejecutar

### Run (solo produce salida)

```bash
cd engines/scene-engine/scene-batch

# Ejecutar un escenario
./gradlew :scene-batch:run --args="src/main/resources/scenarios/01-confidence-filter/run.yaml"

# Ver la salida
cat src/main/resources/scenarios/01-confidence-filter/output/facts.out
```

### Diff (comparar expected vs actual)

```bash
# Generar salida
./gradlew :scene-batch:run --args="src/main/resources/scenarios/01-confidence-filter/run.yaml"

# Comparar con expected
./gradlew :scene-batch:diff --args="src/main/resources/scenarios/01-confidence-filter/expected.out src/main/resources/scenarios/01-confidence-filter/output/facts.out"
```

### Verify (ejecutar + verificar en un paso)

```bash
./gradlew :scene-batch:verify --args="src/main/resources/scenarios/01-confidence-filter/run.yaml src/main/resources/scenarios/01-confidence-filter/expected.out"
```

### Todos los escenarios

```bash
for scenario in 01-confidence-filter 02-hysteresis-block 03-duplicate-discard \
                04-illegal-transition 05-dwell-warning 06-dwell-exceeded \
                07-signal-lost 08-signal-recovery 09-staff-presence \
                10-night-wandering 11-return-to-bed; do
  echo "=== $scenario ==="
  ./gradlew :scene-batch:run --args="src/main/resources/scenarios/$scenario/run.yaml" 2>&1 | tail -5
  echo
done
```

## Archivos Generados por Escenario

Cada ejecucion produce en `output/`:

| Archivo | Formato | Contenido |
|---------|---------|-----------|
| `facts.out` | Texto | Hechos en formato diff-friendly |
| `facts.jsonl` | JSON Lines | Hechos en formato maquina |
| `engine.log` | JSON Lines | Log estructurado del motor |

### Ejemplo facts.out

```
t=2s      TRANSITION LYING -> BED_EDGE           # <- evento 3
t=4s      TRANSITION BED_EDGE -> STANDING        # <- evento 4
t=4m      SIGNAL_LOST monitor=m1                 # <- evento 5
```

### Ejemplo facts.jsonl

```json
{"t":"2s","event":3,"type":"TransitionDetected","bed":"bed-3","night":"night-1","from":"LYING","to":"BED_EDGE"}
```

## Patron de Diseno (Fowler/Vernon)

El batch tool implementa patrones clave:

| Patron | Donde | Referencia |
|--------|-------|------------|
| **Specification** | SceneInterpreter pipeline de chequeos | Vernon IDDD Ch. 10 |
| **Decider** | DigitalTwin.evolve(), NoticeLifecycle | Fowler "Decisor Pattern" |
| **Event Sourcing** | DigitalTwin plega SceneFacts | Vernon IDDD Ch. 5 |
| **Anti-Corruption Layer** | PolicyCalibrationAdapter | Evans DDD Ch. 14 |
| **Pure Domain + Thin Shell** | scene-domain vs scene-service | Hexagonal Architecture |
| **Idempotency via Marks** | DwellMarks previene duplicados | Fowler |
| **Extract Method** | BatchProcessor.processEvent() | Fowler Refactoring |
| **Introduce Parameter Object** | BatchContext, BatchWriters | Fowler Refactoring |
| **Fail-Fast** | VerifyCommand detiene en primer error | Cockburn |

## Extendiendo los Escenarios

### Agregar un nuevo escenario

1. Crear carpeta: `scenarios/NN-nombre/`
2. Crear `run.yaml` con la calibracion deseada
3. Crear `events.dat` con las observaciones
4. Ejecutar `scene-batch run` para ver la salida real
5. Crear `expected.out` con los hechos esperados
6. Ejecutar `scene-batch verify` para confirmar

### Agregar un nuevo tipo de hecho

1. Agregar subtipo a `SceneFact` en `platform/contracts`
2. Actualizar `FactsOutWriter.formatBody()` con el nuevo formato
3. Actualizar `VerifyCommand.matchFact()` con la nueva comparacion
4. Agregar caso en `VerifyCommand.parseExpectedLine()`
5. Crear escenario que ejercite el nuevo camino

### Agregar un nuevo ObservationKind

1. Agregar enum a `ObservationKind` en `platform/contracts`
2. Actualizar `toPersonState()` o `toSceneStateChange()` en `ObservationKindMapping.kt`
3. Crear escenario que lo ejercite
