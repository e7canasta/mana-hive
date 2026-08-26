# Context Map — mana-hive

> Vaughn Vernon, *Implementing Domain-Driven Design*
> Martin Fowler, *Patterns of Enterprise Application Architecture*

## Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PATIO (Hub)                                  │
│  System of Record · Event Ledger · Policy Catalog                   │
│  Publica: EffectiveRules                                            │
│  Responsabilidad: almacenar eventos, resolver policies efectivas    │
└──────────────┬──────────────────────────────────────────────────────┘
               │
               │ EffectiveRules (PolicyCalibration)
               │ Upstream: Hub → Downstream: Sentinel
               │ Relationship: Published Language + Conformist
               │
┌──────────────▼──────────────────────────────────────────────────────┐
│                     VIGILANCIA (Sentinel)                           │
│  Juicio Clínico · Reglas de Alerta · Episodios                     │
│  Publica: SentinelSignals                                           │
│  Responsabilidad: juzgar si un SceneEvent merece alerta             │
│  AGGREGATE: EpisodeLedger (estado de episodios abiertos)            │
└──────┬───────────────┬──────────────────────────────────────────────┘
       │               │
       │               │ Signal (EpisodeOpened/Closed)
       │               │ Upstream: Sentinel → Downstream: Harbor
       │               │ Relationship: Published Language + Conformist
       │               │
       │       ┌───────▼──────────────────────────────────────────────┐
       │       │                FARO (Harbor)                         │
       │       │  Entrega · Notificaciones · Fatiga · Escalación     │
       │       │  Publica: AlarmEvents                                │
       │       │  Responsabilidad: entregar notificaciones al staff   │
       │       │  AGGREGATE: HarborState (NoticeRegistry + Budget)    │
       │       └──────────────────────────────────────────────────────┘
       │
       │ Signal (EpisodeOpened/Closed)
       │ Upstream: Sentinel → Downstream: Recorder
       │ Relationship: Published Language + Conformist
       │
┌──────▼──────────────────────────────────────────────────────────────┐
│                    GRABADORA (Recorder)                             │
│  Grabación · NVR · Evidencia · Ventanas de tiempo                  │
│  Publica: RecordingCommands + EvidenceRecords                       │
│  Responsabilidad: decidir cuándo grabar video                       │
│  AGGREGATE: RecordingLedger (estado de grabaciones activas)         │
└─────────────────────────────────────────────────────────────────────┘
       ▲
       │ SceneEvent (TransitionDetected, DwellExceeded)
       │ Upstream: Scene → Downstream: Recorder
       │ Relationship: Published Language + Conformist
       │
┌──────┴──────────────────────────────────────────────────────────────┐
│                     ESCENA (Scene)                                  │
│  Percepción · Gemelo Digital · Detección de Estados                 │
│  Publica: SceneEvents                                               │
│  Responsabilidad: detectar hechos del mundo real                    │
│  AGGREGATE: DigitalTwin (estado del mundo real)                     │
└─────────────────────────────────────────────────────────────────────┘
       ▲
       │ PolicyCalibration (hysteresis, dwell, confidence)
       │ Upstream: Politica → Downstream: Scene
       │ Relationship: Published Language + Conformist
       │
┌──────┴──────────────────────────────────────────────────────────────┐
│                     REGLAS (Politica)                               │
│  Catálogo · Templates · Overrides · Resolución                     │
│  Publica: CalibrationChanged / ResponseChanged / ...                │
│  Responsabilidad: resolver reglas efectivas por residente           │
│  AGGREGATE: ResidentPolicy ( buckets por residente)                 │
└─────────────────────────────────────────────────────────────────────┘
```

## Relationships

| Upstream | Downstream | Contract | Relationship | Notes |
|----------|-----------|----------|-------------|-------|
| Scene | Sentinel | `SceneEvent` | Published Language + Conformist | Sentinel juzga SceneEvents |
| Scene | Recorder | `SceneEvent` | Published Language + Conformist | Recorder graba por SceneEvents |
| Sentinel | Harbor | `SentinelSignal` | Published Language + Conformist | Harbor entrega por signals |
| Sentinel | Recorder | `SentinelSignal` | Published Language + Conformist | Recorder graba por signals |
| Politica | Scene | `PolicyCalibration` | Published Language + Anti-Corruption Layer | Scene traduce a SceneCalibration |
| Politica | Sentinel | `ResponseChanged` | Published Language + Conformist | Sentinel usa AlertRules |
| Politica | Harbor | `EscalationChanged` | Published Language + Conformist | Harbor usa EscalationConfig |
| Politica | Recorder | `RecordingChanged` | Published Language + Conformist | Recorder usa RecordingConfig |
| Hub | Sentinel | `EffectiveRules` | Published Language + Conformist | Sentinel recibe reglas efectivas |
| Hub | (all) | `EventEnvelope` | Shared Kernel | Ledger compartido |

## Ubiquitous Language

| Término | Bounded Context | Significado |
|---------|----------------|-------------|
| **SceneEvent** | Escena | Hecho detectado del mundo real (transición, dwell, señal) |
| **SentinelSignal** | Vigilancia | Juicio clínico sobre un SceneEvent (episodio abierto/cerrado) |
| **AlarmEvent** | Faro | Notificación entregada al staff |
| **RecordingCommand** | Grabadora | Instrucción de grabación para el NVR |
| **EvidenceRecord** | Grabadora | Registro de evidencia para auditoría |
| **PolicyCalibration** | Reglas | Reglas efectivas para un residente |
| **DigitalTwin** | Escena | Estado actual del mundo real |
| **EpisodeLedger** | Vigilancia | Estado de episodios abiertos/cerrados |
| **HarborState** | Faro | Estado de notificaciones + budget |
| **RecordingLedger** | Grabadora | Estado de grabaciones activas |
| **ResidentPolicy** | Reglas | Política completa de un residente |

## Invariantes por Bounded Context

### Escena (Scene)
- **Aggregate Root:** `DigitalTwin` — identity: (bed, night)
- **Value Objects:** `SceneEvent` (13 subtypes), `SceneCalibration`
- `DigitalTwin` siempre tiene un estado conocido (jamás `null`)
- Transiciones solo ocurren entre estados adyacentes
- Hysteresis previene oscillación

### Vigilancia (Sentinel)
- **Aggregate Root:** `EpisodeLedger` — identity: (resident, night)
- **Value Objects:** `SentinelSignal`, `SentinelCalibration`, `Episode`
- Un episodio abierto NO puede tener otro episodio abierto para la misma regla
- `SentinelEvaluator` es puro: misma entrada → mismo resultado
- Sentinel SIEMPRE abre episodios cuando la regla matchea (sin budget)

### Faro (Harbor)
- **Aggregate Root:** `HarborState` — identity: (resident, night)
- **Value Objects:** `NoticeRegistry` (value class, state container), `NotificationBudget` (value class, budget tracker)
- **Note:** `NoticeRegistry` es un value object, no un entity. No tiene identity propia — se identifica por su contenido (mapa de notices activas). Es un "state container" inmutable que se copia en cada cambio.
- Un notice solo puede existir una vez por episodio
- CRITICAL nunca se suprime (budget infinito implícito)
- Un notice resuelto no puede reabrirse

### Grabadora (Recorder)
- **Aggregate Root:** `RecordingLedger` — identity: (bed, night)
- **Value Objects:** `RecordingCommand`, `EvidenceRecord`, `RecordingState`
- **Note:** `RecordingLedger` es un value class (inline), no una entity. Es un "state container" inmutable — se identifica por su contenido (mapa de grabaciones activas), no por un ID. Cada operación produce una nueva instancia.
- Una grabación activa por (bed, context) — no duplicar
- Recording window es +/- del trigger time
- Evidence solo se crea para episodios con grabación activa

### Reglas (Politica)
- **Aggregate Root:** `ResidentPolicy` — identity: (residentId)
- **Value Objects:** `SemanticBucket`, `PolicyCalibration`, `PolicyOverride`
- Resolución: catalog → template → override (override gana)
- Cada residente tiene exactamente una política válida por instante
- Templates son inmutables, versionados
