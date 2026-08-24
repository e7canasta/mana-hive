Perfecto. Tengo todo el material. Aquí va la **Pizarra Completa del Scene Engine**.

---

## PIZARRA: Scene Engine — Casos de Uso, User Stories y Tests con Datos Reales

### 1. MAPEO DE CONCEPTOS → CÓDIGO

```
CONCEPTO CLÍNICO          →  CÓDIGO                    →  CONTRATO
─────────────────────────────────────────────────────────────────────
"Residente en cama"       →  DigitalTwin.state=Lying    →  PersonState.Lying
"Se sienta en el borde"   →  TransitionDetected         →  SceneFact
"Lleva 5 min de pie"      →  ClockSweeper               →  DwellExceeded
"Monitor se desconectó"   →  SignalHealth.lost=true     →  SignalLost
"Monitor volvió"          →  signal.lost=false          →  SignalRecovered
"No parpadear entre       →  Hysteresis (1.5s)          →  Discard.HYSTERESIS_NOT_MET
 estados"
"Confianza insuficiente"  →  ConfidenceThreshold        →  Discard.CONFIDENCE_TOO_LOW
"Transición ilegal"       →  TransitionTable.isLegal()  →  Discard.ILLEGAL_TRANSITION
"Estado repetido"         →  Duplicate check             →  Discard.DUPLICATE
```

---

### 2. CASOS DE USO (6 principales)

| ID | Caso de Uso | Actor | Componente | Fact Emitido |
|----|-------------|-------|------------|--------------|
| **CU-01** | Interpretar observación válida | Sensor | `SceneInterpreter` | `TransitionDetected` |
| **CU-02** | Rechazar observación por baja confianza | Sensor | `SceneInterpreter` | *(discard)* |
| **CU-03** | Rechazar transición ilegal | Sensor | `SceneInterpreter` | *(discard)* |
| **CU-04** | Detectar duplicado | Sensor | `SceneInterpreter` | *(discard)* |
| **CU-05** | Detectar dwell excedido | ClockSweeper | `ClockSweeper` | `DwellExceeded` |
| **CU-06** | Detectar señal perdida | ClockSweeper | `ClockSweeper` | `SignalLost` |

---

### 3. USER STORIES (12 stories)

#### SceneInterpreter (6 stories)

```
US-SE-01: Como sistema, debo convertir una Observation en SceneFact
          estable, filtrando ruido por confianza mínima.
          → SceneInterpreterImpl paso 1: CONFIANZA
          → Test: SceneInterpreterConfidenceSpec

US-SE-02: Como sistema, debo prevenir "parpadeo" entre estados
          requiriendo que una señal sea sostenida (hysteresis).
          → SceneInterpreterImpl paso 5: HYSTERESIS
          → Test: SceneInterpreterHysteresisSpec

US-SE-03: Como sistema, debo detectar cuando el monitor se
          desconectó y cuándo volvió.
          → SceneInterpreterImpl paso 2: RECUPERACION DE SENSOR
          → Test: SceneInterpreterSensorRecoverySpec

US-SE-04: Como sistema, debo rechazar transiciones ilegales
          según el DAG.
          → SceneInterpreterImpl paso 4: TRANSICION ILEGAL
          → Test: SceneInterpreterIllegalSpec

US-SE-05: Como sistema, debo detectar observaciones duplicadas.
          → SceneInterpreterImpl paso 3: DUPLICADO
          → Test: SceneInterpreterDuplicateSpec

US-SE-06: Como sistema, debo aplicar calibración por residente
          (María exige 0.9, José acepta 0.7).
          → PolicyCalibrationAdapter.toSceneCalibration()
          → Test: SceneInterpreterPerResidentSpec
```

#### ClockSweeper (4 stories)

```
US-SE-07: Como sistema, debo detectar cuando un residente lleva
          demasiado tiempo de pie (dwell exceeded).
          → ClockSweeperImpl + DwellCatalog
          → Test: ClockSweeperExceededSpec

US-SE-08: Como sistema, debo emitir un pre-aviso (warning) antes
          del umbral de dwell.
          → ClockSweeperImpl (warning threshold)
          → Test: ClockSweeperWarningSpec

US-SE-09: Como sistema, debo ser idempotente — dos sweeps al
          mismo tiempo no deben duplicar hechos.
          → DwellMarks
          → Test: ClockSweeperIdempotentSpec

US-SE-10: Como sistema, debo detectar cuando el sensor no envía
          heartbeat en 90 segundos.
          → ClockSweeperImpl.checkHeartbeat()
          → Test: ClockSweeperSignalLostSpec
```

#### Integración (2 stories)

```
US-SE-11: Como ingeniero, debo poder repetir un incidente
          histórico (caída a las 3:00) y obtener el mismo resultado.
          → scene-batch verify
          → Test: LaCaidaDeLas03Spec + SceneBatchIntegrationSpec

US-SE-12: Como sistema, debo aplicar calibración de Politica
          (PolicyCalibration → SceneCalibration) sin contaminar
          el dominio.
          → PolicyCalibrationAdapter (ACL)
          → Test: PoliticaToSceneIntegrationSpec
```

---

### 4. TESTS CON DATOS REALES

#### 4.1 Pipeline del SceneInterpreter (6 pasos)

```
PASO 1: CONFIANZA
─────────────────
  Entrada:  Observation(BED_EDGE, confidence=0.7)
  Config:   minConfidence(BED_EDGE) = 0.8
  Resultado: ❌ Discard(CONFIDENCE_TOO_LOW)
  Twin:     no cambia

  Entrada:  Observation(BED_EDGE, confidence=0.9)
  Config:   minConfidence(BED_EDGE) = 0.8
  Resultado: ✅ pasa al siguiente paso

PASO 2: RECUPERACION DE SENSOR
──────────────────────────────
  Entrada:  twin.signal.lost=true, twin.state=STANDING
            Observation(STANDING, confidence=0.9)
  Resultado: ✅ SignalRecovered + DUPLICATE (mismo estado)
  Twin:     signal.lost=false

  Entrada:  twin.signal.lost=true, twin.state=STANDING
            Observation(BED_EDGE, confidence=0.9)
  Resultado: ✅ SignalRecovered + TransitionDetected(STANDING→BED_EDGE)
  Twin:     state=BED_EDGE, signal.lost=false

PASO 3: DUPLICADO
─────────────────
  Entrada:  twin.state=LYING
            Observation(IN_BED, confidence=0.9)  ← IN_BED mapea a LYING
  Resultado: ❌ Discard(DUPLICATE)
  Twin:     no cambia

PASO 4: TRANSICION ILEGAL
─────────────────────────
  Entrada:  twin.state=LYING
            Observation(OUT_OF_ROOM, confidence=0.9)  ← OUT_OF_ROOM mapea a ABSENT
  Config:   RELEASE_1 (5 estados, LYING→ABSENT no existe)
  Resultado: ❌ Discard(ILLEGAL_TRANSITION)
  Twin:     no cambia

PASO 5: HYSTERESIS
──────────────────
  Entrada:  twin.state=LYING, twin.stateSince=03:00:00
            Observation(BED_EDGE, confidence=0.9) at 03:00:01
  Config:   hysteresis(LYING→BED_EDGE) = 1500ms
  Tiempo:   1s < 1.5s
  Resultado: ❌ Discard(HYSTERESIS_NOT_MET)

  Entrada:  twin.state=LYING, twin.stateSince=03:00:00
            Observation(BED_EDGE, confidence=0.9) at 03:00:02
  Tiempo:   2s > 1.5s
  Resultado: ✅ TransitionDetected(LYING→BED_EDGE)
  Twin:     state=BED_EDGE, stateSince=03:00:02

PASO 6: TRANSICION VALIDA
─────────────────────────
  → emite TransitionDetected(bed=bed-3, night=night-1, from=LYING, to=BED_EDGE)
```

#### 4.2 Pipeline del ClockSweeper (3 checks)

```
CHECK 1: DWELL WARNING
──────────────────────
  Twin:     state=STANDING, stateSince=03:00:00
  Config:   STANDING warning=4m, exceeded=5m
  Sweep at: 03:04:00 → 4 min >= 4 min warning
  Resultado: ✅ DwellWarning(STANDING)

  Twin:     state=STANDING, stateSince=03:00:00
  Sweep at: 03:03:00 → 3 min < 4 min warning
  Resultado: ❌ no emite

CHECK 2: DWELL EXCEEDED
───────────────────────
  Twin:     state=STANDING, stateSince=03:00:00
  Config:   STANDING warning=4m, exceeded=5m
  Sweep at: 03:05:00 → 5 min >= 5 min exceeded
  Resultado: ✅ DwellExceeded(STANDING)

  Twin:     state=STANDING, stateSince=03:00:00
  Sweep at: 03:04:00 → 4 min < 5 min exceeded
  Resultado: ❌ no emite

CHECK 3: SIGNAL LOST
────────────────────
  Twin:     lastHeartbeat=02:58:00
  Config:   heartbeatTimeout=90s
  Sweep at: 03:00:00 → 2 min > 90s
  Resultado: ✅ SignalLost(monitor=m1)

  Twin:     lastHeartbeat=02:59:30
  Sweep at: 03:00:00 → 30s < 90s
  Resultado: ❌ no emite

CHECK 4: IDEMPOTENCIA
─────────────────────
  Sweep 1 at 03:05:00 → DwellExceeded ✅
  Sweep 2 at 03:05:00 → nada (marca ya existe en DwellMarks)
  Total: 1 solo DwellExceeded
```

#### 4.3 Escenario Golden: "La Caída de las 03:00"

```
ARCHIVO: events.dat
───────────────────
t=0s    OBS IN_BED confidence=0.95       ← María dormida
t=2s    OBS BED_EDGE confidence=0.92     ← Se sienta en el borde
t=4s    OBS STANDING confidence=0.90     ← Se pone de pie
t=4m0s  OBS STANDING confidence=0.95     ← Sigue de pie (repetido)
t=6s    OBS IN_HALLWAY confidence=0.88   ← Camina al pasillo
t=8s    OBS OUTDOOR confidence=0.85      ← Sale al exterior

CONFIGURACIÓN: run.yaml
───────────────────────
  bed: bed-3, resident: maria, monitor: m1
  transitions: RELEASE_2 (13 estados)
  dwell: STANDING warning=4m exceeded=5m
  heartbeat: timeout=90s
  start: 2024-01-01T03:00:00Z

SALIDA ESPERADA: expected.out
─────────────────────────────
t=2s      TRANSITION LYING → BED_EDGE           ← evento 6
t=4s      TRANSITION BED_EDGE → STANDING         ← evento 7
t=4m      SIGNAL_LOST monitor=m1                  ← evento 8
t=6s      TRANSITION STANDING → IN_HALLWAY        ← evento 9
t=8s      TRANSITION IN_HALLWAY → OUTDOOR         ← evento 10

SALIDA REAL: facts.jsonl (cada línea es un JSON)
─────────────────────────────────────────────────
{"t":"2s","type":"TransitionDetected","from":"LYING","to":"BED_EDGE"}
{"t":"4s","type":"TransitionDetected","from":"BED_EDGE","to":"STANDING"}
{"t":"4m","type":"SignalLost","monitor":"m1","lastHeartbeat":"2024-01-01T02:59:00Z"}
{"t":"6s","type":"TransitionDetected","from":"STANDING","to":"IN_HALLWAY"}
{"t":"8s","type":"TransitionDetected","from":"IN_HALLWAY","to":"OUTDOOR"}
```

#### 4.4 Test de Integración: LaCaidaDeLas03Spec

```
ESCENARIO: María se levanta a las 03:00
────────────────────────────────────────
  Estado inicial: LYING desde 02:59:58

  Paso 1 (03:00:00): obs(BED_EDGE, 0.9)
    → TransitionDetected(LYING→BED_EDGE)
    → twin.state = BED_EDGE

  Paso 2 (03:00:02): obs(STANDING, 0.95)
    → TransitionDetected(BED_EDGE→STANDING)
    → twin.state = STANDING

  Paso 3-7 (03:01:02 → 03:05:02): sweep cada minuto
    → minuto 1-3: no emite (3 min < 4 min warning)
    → minuto 4: DwellWarning(STANDING) ← 4 min >= 4 min
    → minuto 5: DwellExceeded(STANDING) ← 5 min >= 5 min

  RESULTADO: 4 hechos en total
    [0] TransitionDetected(LYING→BED_EDGE)
    [1] TransitionDetected(BED_EDGE→STANDING)
    [2] DwellExceeded(STANDING)
    [3] twin.state = STANDING, stateSince = 03:00:02
```

#### 4.5 Calibración por Residente

```
ESCENARIO: Misma observación, diferentes residentes
────────────────────────────────────────────────────
  María: minConfidence(BED_EDGE) = 0.9
  José:  minConfidence(BED_EDGE) = 0.7

  Observation(BED_EDGE, confidence=0.8)

  María: 0.8 < 0.9 → ❌ CONFIDENCE_TOO_LOW
  José:  0.8 >= 0.7 → ✅ TransitionDetected(LYING→BED_EDGE)
```

---

### 5. FACTS RESULTANTES (11 tipos de SceneFact)

```
SceneFact (sealed interface)
├── NightOpened          ← inicio de turno
├── TransitionDetected   ← cambio de PersonState (from→to)
├── DwellWarning         ← pre-aviso (~80% umbral)
├── DwellExceeded        ← umbral superado
├── SceneStateChanged    ← cambio en SceneState (staff, wheelchair, rails)
├── SceneDwellWarning    ← pre-aviso de scene
├── SceneDwellExceeded   ← umbral de scene superado
├── StaffPresenceDetected← personal detectado
├── SignalLost           ← monitor desconectado (>90s sin heartbeat)
├── SignalRecovered      ← monitor reconectado
└── NightClosed          ← cierre de turno con NightSummary
```

---

### 6. DISCARDS (5 causas de rechazo)

```
DiscardCause
├── CONFIDENCE_TOO_LOW   ← confidence < minConfidence(state)
├── DUPLICATE            ← Observation mapea al mismo estado actual
├── ILLEGAL_TRANSITION   ← TransitionTable.isLegal() = false
├── HYSTERESIS_NOT_MET   ← tiempo en estado < hysteresis mínima
└── (Sensor Recovery)    ← signal.lost=true → emite SignalRecovered primero
```

---

### 7. PERSON STATES (13 estados + risk groups)

```
SAFE          → Lying, SittingInBed, InBathroom, InRoom, InChair, InWheelchair
AT_RISK       → AttemptingExit, BedEdge, Standing, InHallway, Outdoor, Absent
UNKNOWN       → Unknown(SIGNAL_LOST), Unknown(SCENE)
```

---

### 8. OBSERVACIONES → MAPEO (24 ObservationKinds)

```
ObservationKind          →  PersonState
──────────────────────────────────────
IN_BED                   →  Lying
SITTING_IN_BED           →  SittingInBed
ATTEMPTING_EXIT          →  AttemptingExit
BED_EDGE                 →  BedEdge
STANDING                 →  Standing
IN_BATHROOM              →  InBathroom
IN_ROOM                  →  InRoom
IN_HALLWAY               →  InHallway
OUTDOOR                  →  Outdoor
IN_CHAIR                 →  InChair
IN_WHEELCHAIR            →  InWheelchair
OUT_OF_ROOM              →  Absent
HEARTBEAT                →  Lying (no-op, solo actualiza heartbeat)
UNCLASSIFIED             →  Unknown(SCENE)
STAFF_ENTERED/LEFT/...   →  SceneState change (no PersonState)
WHEELCHAIR_PRESENT/ABSENT→  SceneState change
WALKER_PRESENT/ABSENT    →  SceneState change
BED_RAILS_UP/DOWN        →  SceneState change
COVER_ON/OFF             →  SceneState change
```

---

### 9. PATRONES DDD EN EL CÓDIGO

| Patrón | Fowler/Vernon | Código |
|--------|---------------|--------|
| **Decider** | Event Sourcing puro | `DigitalTwin.evolve(fact)` |
| **Specification** | Pipeline de specs | `SceneInterpreterImpl` 6 pasos |
| **Aggregate Root** | Guardián de invariantes | `DigitalTwin` (por cama) |
| **Anti-Corruption Layer** | Desacoplar contextos | `PolicyCalibrationAdapter` |
| **Pure Domain + Thin Shell** | Ports & Adapters | `scene-domain` sin infra |
| **Idempotency via Marks** | Evitar duplicados | `DwellMarks` |
| **Published Language** | Contratos versionados | `SceneFact` en `../../../../platform/contracts` |

---

### 10. FLUJO COMPLETO: Observation → SceneFact

```
ia-cell emite: perception.observation.v1.<bed>
    │
    ▼
NATS JetStream
    │
    ▼
SceneEngineApplication.subscribe("perception.observation.v1.>")
    │
    ▼
SceneInterpreter.interpret(twin, observation, now)
    │
    ├─→ 1. CONFIANZA: ¿confidence >= minConfidence(kind)?
    │     NO → Discard(CONFIDENCE_TOO_LOW)
    │
    ├─→ 2. RECUPERACION: ¿signal.lost?
    │     SÍ → emitir SignalRecovered, continuar
    │
    ├─→ 3. DUPLICADO: ¿Observation mapea al mismo estado?
    │     SÍ → Discard(DUPLICATE)
    │
    ├─→ 4. TRANSICION ILEGAL: ¿TransitionTable.isLegal(from, to)?
    │     NO → Discard(ILLEGAL_TRANSITION)
    │
    ├─→ 5. HYSTERESIS: ¿(now - stateSince) >= hysteresis(from, to)?
    │     NO → Discard(HYSTERESIS_NOT_MET)
    │
    └─→ 6. TRANSICION VALIDA → emitir TransitionDetected
          │
          ▼
    DigitalTwin.evolve(fact) → nuevo twin
          │
          ▼
    SceneEngineApplication.publish("scene.fact.v1.<bed>", fact)
          │
          ▼
    Hub.ingest(fact)  ← System of Record
```

---

### 11. RESUMEN NUMÉRICO

| Categoría | Cantidad |
|-----------|----------|
| Casos de uso | 6 |
| User stories | 12 |
| Specs de test (scene-domain) | 20 |
| Specs de test (scene-batch) | 1 |
| Specs de test (contracts) | 5 |
| Tipos de SceneFact | 11 |
| Causas de discard | 5 |
| PersonStates | 13 |
| ObservationKinds | 24 |
| Archivos .dat/.out golden | 2 escenarios |

---

¿Querés que profundicemos en algún caso de uso específico, o que revisemos el código de algún componente en detalle?
