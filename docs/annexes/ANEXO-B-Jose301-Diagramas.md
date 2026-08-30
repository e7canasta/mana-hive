# Diagrama de Secuencia: José E1 — Flujo Completo

## 1. Arranque en Frío (Cold Start)

```mermaid
sequenceDiagram
    participant App as MainPipeline
    participant PS as ProfileSeed
    participant PC as ProfileCalibrator
    participant SC as SceneCalibration
    participant SE as SentinelCalibration
    participant HC as HarborCalibration
    participant RC as RecorderCalibration

    App->>PS: load("profiles/jose.json")
    PS-->>App: ResidentProfile (v2, immutable)

    App->>PC: accept(profile, bed=bed-4, resident=jose)
    
    Note over PC: Validate profile version
    
    PC->>SC: map(sceneConfig, bed, timeWindow=23:00-06:00)
    SC-->>PC: SceneCalibration (hysteresis=2min)
    
    PC->>SE: map(sentinelConfig, bed, timeWindow)
    SE-->>PC: SentinelCalibration (comeBack 12/15m)
    
    PC->>HC: map(harborConfig, bed, timeWindow)
    HC-->>PC: HarborCalibration (channels=[PUSH, TABLET])
    
    PC->>RC: map(recordingRules, bed, timeWindow)
    RC-->>PC: RecorderCalibration (onTransition + onComeBack)
    
    PC-->>App: Calibrations (4 engines)
    
    Note over App: Compare JSON vs DSL configBasica
    App->>App: verifyCalibrations(jsonCal, dslCal)
```

## 2. Pipeline Completa: Cada Observación

```mermaid
sequenceDiagram
    participant Edge as Edge Server
    participant NW as NightWatchRuntime
    participant NWS as NightWatchService
    participant Scene as SceneEngine
    participant DigT as DigitalTwin
    participant Senti as SentinelEvaluator
    participant Harb as HarborEngine
    participant Rec as RecorderEngine
    participant NATS as NATS JetStream

    Note over Edge,NATS: ═══════════════════════════════════<br/> cold start ya completo<br/> calibraciones cargadas<br/> ═══════════════════════════════════

    rect rgb(240, 248, 255)
        Note over Edge,NATS: OBSERVACIÓN 1: 23:15:00 — Jose se sienta
        Edge->>NW: Observation(bed=bed-4, ts=23:15:00, type=SittingUp)
        NW->>NWS: process(bed, observation)
        
        NWS->>Scene: evaluate(digitalTwin, calibration, observation)
        Scene->>DigT: transition(Lying → SittingInBed)
        DigT-->>Scene: TransitionDetected(from=Lying, to=SittingInBed, trigger=hysteresis)
        Scene-->>NWS: Outbound.sceneFacts += [TransitionDetected]
        
        NWS->>Senti: evaluate(signals, calibration, sceneEvent)
        Senti->>Senti: no episode open yet → nothing
        Senti-->>NWS: Outbound.signals += []
        
        NWS->>Harb: evaluate(harborCal, signals)
        Harb-->>NWS: Outbound.harborCommands += []
        
        NWS->>Rec: evaluate(recorderCal, sceneEvent)
        Rec->>Rec: onTransition rule matches
        Rec-->>NWS: Outbound.recorderCommands += [RecordingStarted]
        
        NWS->>NATS: publish(scene.fact.v1.bed-4, SceneEvent)
        NWS->>NATS: publish(recorder.command.v1.bed-4, RecordingStarted)
        
        Note over NATS: scene.out: t=0s TRANSITION Lying → SittingInBed<br/>recorder.out: t=0s RECORDING_STARTED
    end

    rect rgb(255, 248, 240)
        Note over Edge,NATS: OBSERVACIÓN 2: 23:17:00 — Jose se acuesta (2 min)
        Edge->>NW: Observation(bed=bed-4, ts=23:17:00, type=LyingDown)
        NW->>NWS: process(bed, observation)
        
        NWS->>Scene: evaluate(digitalTwin, calibration, observation)
        Scene->>DigT: transition(SittingInBed → Lying)
        DigT-->>Scene: TransitionDetected(from=SittingInBed, to=Lying)
        Scene-->>NWS: Outbound.sceneFacts += [TransitionDetected]
        
        NWS->>Senti: evaluate(signals, calibration, sceneEvent)
        Senti->>Senti: episode open? YES (2 min < 12m threshold)
        Senti->>Senti: dwell = 2min < comeBack.warning(12m)
        Senti-->>NWS: Outbound.signals += [] (still within threshold)
        
        NWS->>Harb: evaluate(harborCal, signals)
        Harb-->>NWS: Outbound.harborCommands += []
        
        NWS->>Rec: evaluate(recorderCal, sceneEvent)
        Rec->>Rec: onTransition rule matches
        Rec-->>NWS: Outbound.recorderCommands += [RecordingStopped]
        
        NWS->>NATS: publish(scene.fact.v1.bed-4, TransitionDetected)
        NWS->>NATS: publish(recorder.command.v1.bed-4, RecordingStopped)
        
        Note over NATS: scene.out: t=2m TRANSITION SittingInBed → Lying<br/>recorder.out: t=2m RECORDING_STOPPED
    end
```

## 3. Escenario E1 Completo: 17 Minutos Sentado

```mermaid
sequenceDiagram
    participant Edge as Edge Server
    participant NW as NightWatchRuntime
    participant Scene as SceneEngine
    participant Senti as SentinelEvaluator
    participant Harb as HarborEngine
    participant Rec as RecorderEngine
    participant NATS as NATS JetStream
    participant Hub as mana-hub (Bridge)

    Note over Edge,Hub: ═══════════════════════════════════════<br/> José E1: se sienta 23:15, vuelve 23:32 (17 min)<br/> ComeBack: 12m warning / 15m exceeded<br/> ═══════════════════════════════════════

    rect rgb(240, 255, 240)
        Note over Edge,Hub: t=0s (23:15:00) — INICIO EPISODIO
        Edge->>NW: Observation(SittingUp)
        NW->>Scene: evaluate()
        Scene-->>NW: TransitionDetected(Lying → SittingInBed)
        NW->>Senti: evaluate()
        Note over Senti: Abrir episodio<br/>rule=comeback-lying<br/>severity=NORMAL
        Senti-->>NW: EpisodeOpened(id=ep-001, severity=NORMAL)
        NW->>Harb: evaluate()
        Note over Harb: severity=NORMAL → no dispatch
        Harb-->>NW: []
        NW->>Rec: evaluate()
        Rec-->>NW: RecordingStarted(monitor=unknown, quality=HD)
        
        NW->>NATS: scene.fact.v1.bed-4 → TransitionDetected
        NW->>NATS: sentinel.signal.v1.bed-4 → EpisodeOpened
        NW->>NATS: recorder.command.v1.bed-4 → RecordingStarted
    end

    rect rgb(255, 255, 240)
        Note over Edge,Hub: t=12m (23:27:00) — WARNING
        Note over Senti: dwell = 12min ≥ comeBack.warning<br/>severity → WARNING
        Senti-->>NW: EpisodeUpdated(severity=WARNING)
        NW->>NATS: sentinel.signal.v1.bed-4 → EpisodeUpdated
        
        NW->>Harb: evaluate()
        Note over Harb: severity=WARNING → DISPATCH<br/>channels=[PUSH, TABLET]
        Harb-->>NW: Dispatch(channels=[PUSH, TABLET])
        NW->>NATS: alarm.event.v1.ep-001 → Dispatch
        
        Note over Hub: Bridge consume:<br/>POST /api/v1/episodes<br/>GET /api/v1/wings/{wing}/board → WARNING
    end

    rect rgb(255, 240, 240)
        Note over Edge,Hub: t=15m (23:30:00) — EXCEEDED
        Note over Senti: dwell = 15min ≥ comeBack.exceeded<br/>severity → EXCEEDED
        Senti-->>NW: EpisodeUpdated(severity=EXCEEDED)
        NW->>NATS: sentinel.signal.v1.bed-4 → EpisodeUpdated
        
        NW->>Harb: evaluate()
        Note over Harb: severity=EXCEEDED → DISPATCH URGENT<br/>channels=[PUSH, TABLET, CALL]
        Harb-->>NW: Dispatch(channels=[PUSH, TABLET, CALL])
        NW->>NATS: alarm.event.v1.ep-001 → Dispatch
    end

    rect rgb(240, 240, 255)
        Note over Edge,Hub: t=17m (23:32:00) — CIERRE EPISODIO
        Edge->>NW: Observation(LyingDown)
        NW->>Scene: evaluate()
        Scene-->>NW: TransitionDetected(SittingInBed → Lying)
        
        NW->>Senti: evaluate()
        Note over Senti: Auto-recovery<br/>episode.close(cause=AUTO_RECOVERY)
        Senti-->>NW: EpisodeClosed(cause=AUTO_RECOVERY)
        
        NW->>Harb: evaluate()
        Harb-->>NW: Resolve(resolution=AUTO_RECOVERY)
        
        NW->>NATS: scene.fact.v1.bed-4 → TransitionDetected
        NW->>NATS: sentinel.signal.v1.bed-4 → EpisodeClosed
        NW->>NATS: alarm.event.v1.ep-001 → Resolve
        
        Note over Hub: Bridge consume:<br/>PATCH /api/v1/episodes/ep-001<br/>status=RESOLVED
    end
```

## 4. Diagrama de Clases (Dominio)

```mermaid
classDiagram
    class NightWatchRuntime {
        -ConcurrentHashMap~BedId,ResidentRuntime~ runtimes
        +process(bed, observation) Outbound
    }
    
    class ResidentRuntime {
        +BedId bed
        +ResidentId resident
        +SceneEngine scene
        +SentinelEvaluator sentinel
        +HarborEngine harbor
        +RecorderEngine recorder
        +DigitalTwin digitalTwin
    }
    
    class NightWatchService {
        -JetStream jetStream
        -ObjectMapper mapper
        +process(bed, observation) Outbound
        -publish(bed, out)
        -emit(subject, type, at, payload) EventRef
    }
    
    class SceneEngine {
        +SceneCalibration calibration
        +DigitalTwin digitalTwin
        +evaluate(observation) List~SceneFact~
    }
    
    class SentinelEvaluator {
        +SentinelCalibration calibration
        +EpisodeLedger ledger
        +evaluate(signals, calibration, sceneEvent) List~SentinelSignal~
    }
    
    class HarborEngine {
        +HarborCalibration calibration
        +evaluate(harborCal, signals) List~HarborCommand~
    }
    
    class RecorderEngine {
        +RecorderCalibration calibration
        +evaluate(recorderCal, sceneEvent) List~RecordingCommand~
    }
    
    class ProfileCalibrator {
        +accept(profile, bed, resident) Calibrations
        -mapScene(config, bed, timeWindow) SceneCalibration
        -mapSentinel(config, bed, timeWindow) SentinelCalibration
        -mapHarbor(config, bed, timeWindow) HarborCalibration
        -mapRecorder(rules, bed, timeWindow) RecorderCalibration
    }
    
    class Outbound {
        +List~SceneFact~ sceneFacts
        +List~SentinelSignal~ signals
        +List~HarborCommand~ harborCommands
        +List~RecordingCommand~ recorderCommands
    }
    
    NightWatchRuntime --> ResidentRuntime
    ResidentRuntime --> SceneEngine
    ResidentRuntime --> SentinelEvaluator
    ResidentRuntime --> HarborEngine
    ResidentRuntime --> RecorderEngine
    NightWatchService --> NightWatchRuntime
    ProfileCalibrator --> SceneCalibration
    ProfileCalibrator --> SentinelCalibration
    ProfileCalibrator --> HarborCalibration
    ProfileCalibrator --> RecorderCalibration
    NightWatchService --> Outbound
```

## 5. Mapeo JSON → Calibraciones

```mermaid
flowchart LR
    subgraph "jose.json (Perfil v2)"
        J1[sceneConfig]
        J2[sentinelConfig]
        J3[harborConfig]
        J4[recordingRules]
    end
    
    subgraph "ProfileCalibrator"
        PC1[mapScene]
        PC2[mapSentinel]
        PC3[mapHarbor]
        PC4[mapRecorder]
    end
    
    subgraph "Calibraciones"
        C1[SceneCalibration<br/>hysteresis=2min]
        C2[SentinelCalibration<br/>comeBack 12/15m]
        C3[HarborCalibration<br/>channels=PUSH,TABLET]
        C4[RecorderCalibration<br/>onTransition+onComeBack]
    end
    
    J1 --> PC1 --> C1
    J2 --> PC2 --> C2
    J3 --> PC3 --> C3
    J4 --> PC4 --> C4
    
    C1 --> Scene
    C2 --> Sentinel
    C3 --> Harbor
    C4 --> Recorder
```

## 6. Flujo de Eventos: Hive → Hub

```mermaid
flowchart TB
    subgraph "mana-hive (NightWatchRuntime)"
        OBS[Observation] --> SCENE[SceneEngine]
        SCENE --> SENTI[SentinelEvaluator]
        SENTI --> HARB[HarborEngine]
        SENTI --> REC[RecorderEngine]
    end
    
    subgraph "NATS JetStream"
        S1[scene.fact.v1.bed-4]
        S2[sentinel.signal.v1.bed-4]
        S3[alarm.event.v1.ep-001]
        S4[recorder.command.v1.bed-4]
    end
    
    subgraph "mana-hub (Bridge)"
        B1[SceneFactConsumer]
        B2[SentinelSignalConsumer]
        B3[AlarmEventConsumer]
        B4[RecorderConsumer]
    end
    
    subgraph "PostgreSQL"
        T1[scene_events]
        T2[episodes]
        T3[notification_events]
        T4[clip_windows]
    end
    
    SCENE --> S1 --> B1 --> T1
    SENTI --> S2 --> B2 --> T2
    HARB --> S3 --> B3 --> T3
    REC --> S4 --> B4 --> T4
```
