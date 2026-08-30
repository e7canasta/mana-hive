# ANEXO B: NightWatch Application — Flujo Completo

## 1. Ciclo de Vida de la Aplicación

```mermaid
sequenceDiagram
    participant Spring as Spring Boot Context
    participant App as NightWatchApplication
    participant Census as Census
    participant CS as CensusSeed
    participant Runtime as NightWatchRuntime
    participant Service as NightWatchService
    participant PS as ProfileSeed
    participant PC as ProfileCalibrator
    participant Bus as BusConnector
    participant NATS as NATS JetStream
    participant Events as BusEvents

    Note over Spring,Events: ═══ FASE 1: ENSAMBLAJE DE CONTEXTO ═══

    Spring->>App: @SpringBootApplication
    App->>Census: Census()
    App->>CS: CensusSeed(census, "census.json")
    CS-->>Census: load() → ConcurrentHashMap<BedId, CensusEntry>
    App->>Runtime: NightWatchRuntime() (empty)
    App->>Bus: BusConnector(url, events)
    Bus->>NATS: connectAsync(url)

    Note over Spring,Events: ═══ FASE 2: ARRANQUE EN FRÍO ═══

    App->>Service: @PostConstruct start()
    Service->>PS: ProfileSeed(calibrator, profilesDir).load()
    
    loop Cada perfil JSON en profiles/
        PS->>PS: read(jsonFile)
        PS->>PC: accept(dto, bed, resident)
        PC->>PC: validate(profile)
        PC->>PC: project(timeWindow=now)
        PC-->>PS: EngineCalibrations
        PS->>Runtime: register(resident, bed, calibrations)
        Note over Runtime: Crea ResidentRuntime<br/>con 4 motores
    end

    Service->>Service: status = WAITING_FOR_BUS
    Service->>Events: onConnected { onBusAvailable() }
    Service->>Events: onLost { onBusLost() }

    Note over Spring,Events: ═══ FASE 3: CONEXIÓN AL BUS ═══

    Bus-->>Events: fireConnected()
    Events-->>Service: onBusAvailable()
    
    Service->>NATS: NatsTopology.ensureAll()
    Note over NATS: 7 streams declarados<br/>PERCEPTION, SCENE, SENTINEL,<br/>ALARM, POLICY, RECORDER, EVIDENCE
    
    Service->>NATS: subscribe(perception.observation.v1.>)
    Service->>NATS: subscribe(hub.policy.change.v1)
    Service->>NATS: subscribe(hub.policy.profile.v1)
    Service->>Service: status = RUNNING

    Note over Spring,Events: ═══ FASE 4: OPERACIÓN ═══
```

## 2. Ingesta de Observaciones: NATS → Motores → NATS

```mermaid
sequenceDiagram
    participant Edge as Edge Server
    participant NATS as NATS JetStream
    participant Service as NightWatchService
    participant Census as Census
    participant Runtime as NightWatchRuntime
    participant RT as ResidentRuntime
    participant Scene as SceneEngine
    participant Senti as SentinelEvaluator
    participant Harb as HarborEngine
    participant Rec as RecorderEngine

    Edge->>NATS: publish(perception.observation.v1.bed-4, Observation)
    
    NATS->>Service: Dispatcher callback
    Service->>Service: deserialize EventEnvelope → Observation
    
    Service->>Census: lookup(bed=bed-4)
    Census-->>Service: CensusEntry(resident=jose, night=1, monitor=cam-1)
    
    Service->>Runtime: onObservation(resident=jose, obs)
    Runtime->>Runtime: rt = runtimes["jose"]
    Runtime->>RT: synchronized(rt) { rt.onObservation(obs) }
    
    Note over RT: ═══ 4 Motores ═══
    
    RT->>RT: deduplicate (discard if older than lastObservedAt)
    
    rect rgb(240, 248, 255)
        Note over RT,Scene: STAGE 1: SCENE
        RT->>Scene: interpret(twin, obs, now)
        Scene->>Scene: analyze posture, position, movement
        Scene-->>RT: SceneResult(twin=newTwin, facts=[SceneFact])
    end
    
    rect rgb(255, 248, 240)
        Note over RT,Senti: STAGE 2: SENTINEL
        RT->>Senti: evaluate(fact, episodes, fact.at)
        Senti->>Senti: check rules vs calibration
        Senti->>Senti: open/update/close episodes
        Senti-->>RT: SentinelResult(episodes, signals=[SentinelSignal])
    end
    
    rect rgb(255, 240, 240)
        Note over RT,Harb: STAGE 3: HARBOR
        RT->>Harb: evaluate(signal, harborState, signal.at)
        Harb->>Harb: decide channels by severity
        Harb-->>RT: HarborResult(state, commands=[NoticeFor])
    end
    
    rect rgb(240, 240, 255)
        Note over RT,Rec: STAGE 4: RECORDER
        RT->>Rec: evaluate(trigger, recordingLedger, at)
        Rec->>Rec: match rules vs calibration
        Rec-->>RT: RecorderResult(ledger, commands=[RecordingCommand])
    end
    
    RT-->>Runtime: Outbound(sceneFacts, signals, harborCommands, recorderCommands)
    Runtime-->>Service: out
    
    Note over Service: ═══ PUBLICACIÓN ═══
    
    Service->>NATS: scene.fact.v1.bed-4 → SceneEvent
    Service->>NATS: sentinel.signal.v1.bed-4 → SentinelSignal
    Service->>NATS: alarm.event.v1.ep-001 → AlarmEvent
    Service->>NATS: recorder.command.v1.bed-4 → RecordingCommand
```

## 3. Sweep Periódico (Cada 30s)

```mermaid
sequenceDiagram
    participant Cron as @Scheduled(30s)
    participant Service as NightWatchService
    participant PC as ProfileCalibrator
    participant Runtime as NightWatchRuntime
    participant RT as ResidentRuntime
    participant NATS as NATS JetStream

    Cron->>Service: sweep()
    
    Service->>PC: reprojectOnWindowEdge()
    Note over PC: ¿Cambia la ventana horaria?<br/>ej: 22:00 → noche<br/>Si → recalibrar motores
    
    Service->>Runtime: tickAll(now)
    
    loop Cada residente registrado
        Runtime->>RT: synchronized(rt) { rt.onTick(now) }
        
        Note over RT: ═══ Misma pipeline ═══
        RT->>RT: Scene: tick(twin, now) → fact?
        RT->>RT: Sentinel: tick(episodes, now) → signal?
        Note over RT: dwell detection<br/>come-back check<br/>signal-lost check
        RT->>RT: Harbor: tick(signal) → command?
        RT->>RT: Recorder: tick() → command?
        RT-->>Runtime: Outbound
        
        alt Hay eventos nuevos
            Service->>NATS: publish(bed, out)
        end
    end
```

## 4. Diagrama de Clases: NightWatch Application

```mermaid
classDiagram
    class NightWatchApplication {
        <<Spring Boot>>
        +objectMapper() ObjectMapper
        +census(profilesDir) Census
        +runtime() NightWatchRuntime
        +busEvents() BusEvents
        +busConnector(url, events) BusConnector
    }
    
    class NightWatchService {
        -NightWatchRuntime runtime
        -Census census
        -RuntimeStatusHolder status
        -BusEvents events
        -JetStream jetStream
        -ProfileCalibrator calibrator
        +start()
        +onBusAvailable()
        +onBusLost(reason)
        +sweep()
        -subscribeToObservations(conn)
        -subscribeToPolicyChanges(conn)
        -subscribeToProfiles(conn)
        -handleObservation(obs)
        -handlePolicyChange(envelope)
        -publish(bed, out)
        -emit(subject, type, at, payload) EventRef
    }
    
    class NightWatchRuntime {
        -ConcurrentHashMap~ResidentId,ResidentRuntime~ runtimes
        +register(resident, bed, calibrations)
        +unregister(resident)
        +onObservation(residentId, obs) Outbound
        +tickAll(now) Map~ResidentId,Outbound~
        +get(residentId) ResidentRuntime?
        +size int
    }
    
    class ResidentRuntime {
        +ResidentId residentId
        +BedId bed
        +SceneEngine scene
        +SentinelEvaluator sentinel
        +HarborEngine harbor
        +RecorderEngine recorder
        +DigitalTwin twin
        +EpisodeLedger episodes
        +HarborState harborState
        +RecordingLedger recordingLedger
        +Instant lastObservedAt
        +onObservation(obs) Outbound
        +onTick(now) Outbound
    }
    
    class Census {
        -ConcurrentHashMap~BedId,CensusEntry~ entries
        +lookup(bed) CensusEntry?
        +load(entries)
    }
    
    class CensusSeed {
        -Census census
        -File file
        +load()
    }
    
    class ProfileSeed {
        -ProfileCalibrator calibrator
        -File profilesDir
        +load()
    }
    
    class ProfileCalibrator {
        -NightWatchRuntime runtime
        -Census census
        +accept(dto, bed?, resident?) EngineCalibrations
        +reprojectOnWindowEdge()
        -mapScene(config, bed, timeWindow) SceneCalibration
        -mapSentinel(config, bed, timeWindow) SentinelCalibration
        -mapHarbor(config, bed, timeWindow) HarborCalibration
        -mapRecorder(rules, bed, timeWindow) RecorderCalibration
    }
    
    class EngineCalibrations {
        +SceneCalibration scene
        +SentinelCalibration sentinel
        +HarborCalibration harbor
        +RecorderCalibration recorder
    }
    
    class Outbound {
        +List~SceneFact~ sceneFacts
        +List~SentinelSignal~ signals
        +List~NoticeFor~ harborCommands
        +List~RecordingCommand~ recorderCommands
    }
    
    class BusConnector {
        -String url
        -BusEvents events
        +connect()
    }
    
    class BusEvents {
        -Connection connection
        +fireConnected()
        +fireLost(reason)
        +onConnected(block)
        +onLost(block)
    }
    
    class RuntimeStatusHolder {
        +RuntimeState state
        +transition(state, reason)
    }
    
    NightWatchApplication --> NightWatchService
    NightWatchApplication --> NightWatchRuntime
    NightWatchApplication --> Census
    NightWatchApplication --> BusConnector
    NightWatchApplication --> BusEvents
    
    NightWatchService --> NightWatchRuntime
    NightWatchService --> Census
    NightWatchService --> ProfileCalibrator
    NightWatchService --> RuntimeStatusHolder
    NightWatchService --> BusEvents
    
    NightWatchRuntime --> ResidentRuntime
    ResidentRuntime --> SceneEngine
    ResidentRuntime --> SentinelEvaluator
    ResidentRuntime --> HarborEngine
    ResidentRuntime --> RecorderEngine
    
    ProfileCalibrator --> EngineCalibrations
    ProfileSeed --> ProfileCalibrator
    CensusSeed --> Census
```

## 5. Observación → 4 Motores → Publicación

```mermaid
flowchart TB
    subgraph "NATS Inbound"
        OBS["perception.observation.v1.bed-4"]
    end
    
    subgraph "NightWatchService"
        DES[Deserialize]
        LOOKUP[census.lookup]
        ROUTE[runtime.onObservation]
    end
    
    subgraph "ResidentRuntime (jose, bed-4)"
        direction TB
        DEDUP[deduplicate]
        
        subgraph "Stage 1: Scene"
            SC[SceneEngine.interpret]
            SC_OUT["facts: [TransitionDetected]"]
        end
        
        subgraph "Stage 2: Sentinel"
            SE[SentinelEvaluator.evaluate]
            SE_OUT["signals: [EpisodeOpened]"]
        end
        
        subgraph "Stage 3: Harbor"
            HA[HarborEngine.evaluate]
            HA_OUT["commands: [Dispatch]"]
        end
        
        subgraph "Stage 4: Recorder"
            RE[RecorderEngine.evaluate]
            RE_OUT["commands: [RecordingStarted]"]
        end
    end
    
    subgraph "NATS Outbound"
        S1["scene.fact.v1.bed-4"]
        S2["sentinel.signal.v1.bed-4"]
        S3["alarm.event.v1.ep-001"]
        S4["recorder.command.v1.bed-4"]
    end
    
    subgraph "mana-hub Bridge"
        B1[SceneFactConsumer]
        B2[SentinelSignalConsumer]
        B3[AlarmEventConsumer]
        B4[RecorderConsumer]
    end
    
    OBS --> DES --> LOOKUP --> ROUTE
    ROUTE --> DEDUP
    DEDUP --> SC --> SC_OUT
    SC_OUT --> SE --> SE_OUT
    SE_OUT --> HA --> HA_OUT
    SE_OUT --> RE --> RE_OUT
    HA_OUT --> S3
    RE_OUT --> S4
    SC_OUT --> S1
    SE_OUT --> S2
    
    S1 --> B1
    S2 --> B2
    S3 --> B3
    S4 --> B4
```

## 6. José E1: Timeline Completo

```mermaid
gantt
    title José E1: 17 Minutos Sentado
    dateFormat HH:mm
    axisFormat %H:%M
    
    section Sensor
    Observación SittingUp     :milestone, obs1, 23:15, 0h
    Observación LyingDown     :milestone, obs2, 23:32, 0h
    
    section Scene
    Transition Lying→Sitting   :active, scene1, 23:15, 23:17
    Transition Sitting→Lying   :active, scene2, 23:32, 23:32
    
    section Sentinel
    Episode Open (NORMAL)      :active, sent1, 23:15, 23:27
    Episode Updated (WARNING)  :crit, sent2, 23:27, 23:30
    Episode Updated (EXCEEDED) :crit, sent3, 23:30, 23:32
    Episode Closed             :done, sent4, 23:32, 23:32
    
    section Harbor
    No dispatch (NORMAL)       :milestone, h1, 23:15, 0h
    Dispatch PUSH+TABLET       :crit, h2, 23:27, 23:27
    Dispatch PUSH+TABLET+CALL  :crit, h3, 23:30, 23:30
    Resolve AUTO_RECOVERY       :done, h4, 23:32, 23:32
    
    section Recorder
    Recording Started           :active, r1, 23:15, 23:32
    Recording Stopped           :done, r2, 23:32, 23:32
```
