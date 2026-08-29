## Resident Safety Monitoring Platform Architecture and Engine Pipelines

La plataforma **mana-hive** es un sistema avanzado de monitoreo para la seguridad de residentes en centros de cuidado, diseñado bajo una **arquitectura hexagonal** y principios de dominio puro. El núcleo tecnológico consiste en un **pipeline reactivo de cuatro etapas** que procesa datos de sensores en tiempo real mediante motores especializados: **Scene** interpreta movimientos físicos, **Sentinel** gestiona riesgos clínicos, **Harbor** coordina las notificaciones al personal y **Recorder** activa evidencias en video. Este flujo está orquestado por el **Night-Watch Runtime**, que vincula la infraestructura de mensajería **NATS** con la lógica de negocio para supervisar a múltiples residentes de forma simultánea. Un componente crítico llamado **Politica** traduce las instrucciones de cuidado humano en configuraciones técnicas precisas, asegurando que el comportamiento del sistema sea **personalizable y auditable**. Además, el proyecto utiliza **Kotlin** y un sistema de construcción robusto que garantiza la pureza del código y facilita la verificación mediante escenarios de prueba denominados **blueprints**.

Discuss what these sources say about Core Pipeline (Four Engines), in the larger context of Hisso Resident Safety Monitoring Platform.
The **Hisso Resident Safety Monitoring Platform** is designed for care facilities to process real-time telemetry from bed sensors, cameras, or floor radars in order to detect resident activity, manage safety episodes, and notify staff when thresholds are exceeded. At the heart of Hisso is a linear, four-stage reactive pipeline composed of **"Pure Domain Engines"**.

This core pipeline operates on a stateless **Consume-Evaluate-Publish** pattern. The architecture relies on **Pure Domain and Hexagonal design principles**, separating all clinical evaluation logic from databases, APIs, and the messaging infrastructure.

---

### The Four Core Engines

The processing pipeline sequentially channels resident monitoring data through four specialized domain engines:

```
Observation ──> [ Scene ] ──SceneEvent──> [ Sentinel ] ──SentinelSignal──> [ Harbor ] ──> NoticeCommand
                                 │                                  │
                                 └─────────> [ Recorder ] <─────────┘ ──> RecordingCommand
```

#### 1. Scene Engine

The **Scene Engine** is the entry stage of the pipeline.

- **Responsibility**: It maintains a **Digital Twin** (the software state container) representing the physical state of a resident—such as _Lying_, _Sitting_, _Bed Edge_, or _Standing_.
- **Input**: Raw `Observation` telemetry (containing a sensor kind, value, and timestamp).
- **Output**: Semantic `SceneEvent` objects.
- **Key Logic**: To filter physical and environmental noise, the engine coordinates three components via a Facade pattern: the `SceneInterpreter` (manages transition tables with hysteresis and minimum confidence filters), the `ClockSweeper` (checks time-based thresholds), and the `DigitalTwin`.
- **Derived Events**: Generated events include `TransitionDetected`, `DwellWarning`, `DwellExceeded` (resident stays in a state too long), `ComeBackExceeded` (resident fails to return to baseline), and `SignalLost` (monitor heartbeat timeout).

#### 2. Sentinel Engine

The **Sentinel Engine** applies clinical judgment to the physical facts emitted by the Scene Engine.

- **Responsibility**: It evaluates safety risks and manages the lifecycle of **Safety Episodes** (incidents).
- **Input**: `SceneEvent` objects.
- **Output**: `SentinelSignal` objects.
- **Key Logic**: The engine is functionally pure; it accepts a state shell called the `EpisodeLedger` along with the input event, applies evaluation rules (`AlertRule`), and returns a `SentinelVerdict` containing the updated ledger state and a list of output signals.
- **Episode Lifecycle Signals**:
    - **`EpisodeOpened`**: Emitted when a risk rule matches and a new safety episode begins (with support for escalating the severity of an existing episode, known as a "Severity Ramp").
    - **`UmbrellaEvent`**: Contextual resident movements occurring while an episode is already active that do not cause a severity escalation.
    - **`AutoRecovery`**: Emitted if the resident safely returns to their baseline state (typically _Lying_) without staff intervention, allowing UI dashboards to "dim" the alert while keeping the episode open.
    - **`EpisodeClosed`**: Finalizes an episode once configured closure conditions (`SAFE_ONLY`, `STAFF_OR_SAFE`, or `STAFF_AND_SAFE`) are satisfied.
- **ComeBack Judgment**: Features distinct logic tracking the _absence_ of a baseline state (e.g., alert if the resident is "not in bed" for a specific time window).

#### 3. Harbor Engine (Vigia)

The **Harbor Engine** shifts the focus from clinical evaluation to communication delivery and logistics.

- **Responsibility**: It transforms logical alerts into concrete, staff-facing instructions while actively fighting **alarm fatigue**.
- **Input**: `SentinelSignal` objects.
- **Output**: `NoticeCommand` dispatch or resolution events.
- **Key Logic**: It directs notifications across several delivery channels (`CONSOLE`, `PUSH`, `TABLET`, and `WARD_BOARD`). To prevent staff desensitization, it evaluates dispatches against a configurable **Notification Budget** (`NotificationBudget`). If active notifications exceed the shift maximum, low-priority alerts are suppressed, though `CRITICAL` severity events are hard-coded to _never_ be suppressed. It tracks active dispatches inside an immutable `NoticeRegistry` to seamlessly handle alert resolutions.

#### 4. Recorder Engine

The **Recorder Engine** operates in parallel to Harbor, bridging clinical events to video evidence collection.

- **Responsibility**: It translates domain events into explicit instructions for Network Video Recorders (NVRs).
- **Input**: Both physical `SceneEvents` and clinical `SentinelSignals`.
- **Output**: `RecordingCommand` variants (e.g., `RecordingStarted`, `RecordingStopped`, and `ClipCreated` acknowledgments).
- **Key Logic**: It utilizes a "window" system to capture vital context before and after an incident. This includes pre-roll (`recordBefore`) and post-roll (`recordAfter`) video segments. The configurations carry specific video quality profiles, such as `SD` (640x480 @ 15fps, 1Mbps), `HD` (1280x720 @ 30fps, 5Mbps), or `FULL` (1920x1080 @ 30fps, 10Mbps).

---

### The Larger Platform Context

For these engines to protect residents in a production environment, they are surrounded by orchestration, policy translation, and messaging layers:

#### 1. Runtime In-Process Orchestration

In a live care facility, running these engines sequentially via distributed network calls would introduce massive latency and serialization overhead. Instead, Hisso resolves this via the **Night-Watch Runtime** microservice:

- **`ResidentRuntime`**: For each monitored resident, an individual `ResidentRuntime` instance is instantiated. It encapsulates the state of all four engines, piping telemetry through standard synchronous Kotlin function calls on pure domain values.
- **Strict State Isolation**: Runtimes are strictly **single-threaded per resident**. A thread-safe parent registry, the `NightWatchRuntime`, uses a `ConcurrentHashMap` to manage these runtimes by `ResidentId`. This design guarantees that while different residents are monitored in parallel, the telemetry stream for a single individual is evaluated sequentially to prevent clock corruption and state desynchronization.
- **Production Shell**: The Spring-managed `NightWatchService` handles ingestion from the message bus, schedules sweeps (every 30 seconds to trigger time-dependent alerts like "Dwell Exceeded"), and coordinates status reporting.

#### 2. Clinical Policy Translation (Politica)

System behaviors are never hard-coded; they are dynamic and derive from customized clinical guidelines. High-level care intents must be translated into technical configurations:

- **The Hub Service**: Serving as the System of Record (SoR), the Hub stores event-sourced policy histories. These four layers (Level, Template, Manual Adjustments, and Time Windows) are folded (`foldPolicyLayers`) into a consolidated `AlarmProfile`.
- **Politica Engine**: This dedicated translation engine processes the resident's `AlarmProfile` against the global state catalog (`DagCatalog`) to output a unified `PolicyCalibration` contract. For security and auditability, Politica hashes every resolution into a unique `Fingerprint` and packs decisions inside an `Explained<T>` wrapper detailing precisely _why_ a threshold was designated.
- **Policy Adapters & Hot-Swapping**: The `policy-adapters` module acts as an Anti-Corruption Layer, slicing the unified calibration into immutable sub-calibrations for each engine (`SceneCalibration`, `SentinelCalibration`, `HarborCalibration`, and `RecorderCalibration`). When a policy changes, the `ResidentRuntime` performs an in-memory hot-swap of its engine calibrations via a volatile reference.

#### 3. Asynchronous Messaging Backbone (NATS)

Outside the in-process execution cores, microservices communicate asynchronously via **NATS JetStream**.

- **Subject Taxonomy**: Highly structured, versioned subject pathways enforce resident isolation and facilitate seamless upgrades (e.g., `perception.observation.v1.<bed>` or `scene.fact.v1.<bed>`).
- **Hexagonal Ingest/Egress Adapters**: Services use concrete adapters (such as `SentinelNatsIngest` or `SceneNatsEgress`) to act as the "Imperative Shell" around the pure cores. They ingest bytes from NATS, map them to an `EventEnvelope` containing tracing metadata, deserialize the payload using a centralized `NatsObjectMapper` configured with the `JavaTimeModule` (to preserve ISO-8601 timestamps), execute the domain evaluation, and publish the resulting verdict back to NATS.
- **Infrastructure Resilience**: Streams utilize a limits-based 7-day retention policy. To guarantee 24/7 availability, services connect asynchronously using a "never-surrender" connection strategy featuring infinite reconnect attempts, preventing system startup from blocking if NATS is temporarily offline.

🧩 Would you like me to construct an interactive flow or diagram mapping a hypothetical resident movement (e.g., sitting on the edge of the bed for over 2 minutes) all the way through the engines to the dispatch of a Push notification?

---


Discuss what these sources say about Scene Engine, in the larger context of Core Pipeline (Four Engines).

Within the **Hisso Resident Safety Monitoring Platform**, the **Scene Engine** serves as the **first stage** of the linear, four-stage reactive pipeline. Its primary responsibility is to act as the gateway for raw physical data, translating low-level telemetry into semantic **human movement events**.

Operating on a functional, stateless core pattern, the engine receives raw sensor data, evaluates it against a resident-specific calibration, and produces high-level facts to drive clinical judgment in downstream stages.

---

### Internal Architecture and the Facade Pattern

The Scene Engine employs the **Facade pattern** to coordinate its processing logic, exposing a simplified API that integrates three highly specialized internal components:

1. **`DigitalTwin`**: The aggregate state container representing the resident’s current physical status and connection health. It tracks the active `PersonState` (such as `Lying`, `BedEdge`, `Standing`, or `Unknown`), the temporal context (`stateSince` records when the current state began), the last monitor heartbeat, and the active `SceneCalibration` configuration.
2. **`SceneInterpreter`**: The logic block responsible for processing raw telemetry and executing physical state transitions.
3. **`ClockSweeper`**: The time-dependent component that evaluates state durations and triggers events based on the passage of time rather than incoming observations.

---

### Processing Logic and Noise Filtering

Raw observations—consisting of a sensor kind (e.g., `BED_PRESSURE`), a value, and a timestamp—are prone to environmental and physical noise. To guarantee data integrity before the pipeline evaluates clinical risk, the `SceneInterpreter` subjects every incoming observation to three processing layers:

- **Confidence Checks**: The interpreter checks the telemetry confidence score against the minimum required confidence (`minConfidence`) specified in the resident's calibration. If the confidence is too low, the observation is discarded immediately under `DiscardCause.CONFIDENCE_TOO_LOW`.
- **Transition Validation**: The interpreter consults a `TransitionTable` to determine if a transition from the twin's current state to the newly observed state is legally defined.
- **Hysteresis Filtering**: To prevent "flapping" or "flickering" (rapidly bouncing between states due to unstable sensor readings), the transition must persist continuously for a configured hysteresis duration (e.g., 1500 milliseconds) before the `DigitalTwin` officially updates its state. Once a valid transition is finalized, the engine publishes a `TransitionDetected` event.

---

### Time-Based Analysis via the `ClockSweeper`

While the `SceneInterpreter` reacts directly to incoming telemetry, the **`ClockSweeper`** evaluates physical behavior when no new observations are arriving. As time advances, it sweeps across time increments to generate the following critical semantic `SceneEvent` facts:

- **`DwellWarning` / `DwellExceeded`**: Triggered when a resident remains in a non-safe physical state (such as standing or sitting on the edge of the bed) longer than their customized care safety threshold.
- **`ComeBackWarning` / `ComeBackExceeded`**: Handles "inverse dwell" scenarios. If a resident leaves their safe baseline state (typically `LYING`) and fails to return to bed within a pre-defined window, a safety alert is queued.
- **`SignalLost`**: Fired if the system fails to receive a heartbeat or telemetry packet from the room's monitor within the configured `heartbeatTimeout` duration.

---

### The Scene Engine in the Larger Platform Context

For the Scene Engine to protect residents in a production care facility, it is integrated into Hisso's broader runtime, policy translation, and messaging infrastructures:

#### 1. Runtime In-Process Orchestration (`ResidentRuntime`)

Executing the core pipeline via sequential, asynchronous network calls would introduce latency and serialization overhead. Instead, the **`Night-Watch Runtime`** service coordinates execution within a single process:

- **Sequential Telemetry Pipeline**: The `ResidentRuntime` encapsulates the state of all four engines for a single resident. Raw observations enter `ResidentRuntime.onObservation()`, which first checks timestamps to ensure strict temporal order and prevent "clock corruption" in the `DigitalTwin`.
- **Synchronous Pipeline Execution**: It forwards the telemetry to the Scene Engine, which processes the observation, sweeps the timers, and emits a list of `SceneFacts` (the `SceneEvent` objects). These facts are synchronously and immediately passed into Stage 2—the **Sentinel Engine**—to determine if they warrant opening or escalating a safety episode.
- **Temporal Sweeps**: To guarantee that time-based events (like `DwellExceeded`) fire even when no sensor data is arriving, the orchestrator triggers an `onTick()` execution on the wall clock. This loop advances the Scene Engine's internal clock and invokes the `ClockSweeper`'s time-advancement sweeps.

#### 2. Policy-Driven Calibration (_Politica_)

The parameters driving the Scene Engine are never hard-coded. The **Politica Engine** resolves high-level clinical profiles (e.g., "Alert if Elena sits up for more than 2 minutes") into a unified `PolicyCalibration` contract.

A dedicated **Scene Adapter** serves as an Anti-Corruption Layer (ACL), slicing the calibration to construct an immutable, resident-specific `SceneCalibration`. This object maps clinical settings directly into the Scene Engine's technical primitives (hysteresis tables, state confidence levels, dwell catalogs, and heartbeat timeouts). When a clinician updates a resident's policy at the Central Hub, the `ResidentRuntime` hot-swaps the active `SceneCalibration` via a volatile reference.

#### 3. Asynchronous Messaging Egress (NATS)

In distributed testing or batch replay environments, the Scene Engine is wrapped in a NATS messaging adapter. The **`SceneNatsEgress`** driving adapter subscribes to raw perception telemetry, deserializes the bytes, runs the domain logic, and serializes the resulting polymorphic `SceneFact` events inside a standardized `EventEnvelope`. These envelopes are published back to the NATS JetStream bus under versioned subjects (e.g., `scene.fact.v1.<bed>`) where they are asynchronously ingested by the standalone Sentinel, Recorder, and archiving services.

📊 Would you like me to generate a flowchart mapping how a raw Bed Pressure observation flows through the Scene Engine's interpreter and sweeper to produce a transition event?


---

> Es un gemelo digital que traduce observaciones fisicas en alertas procesables

> **Scene**: Traduce los datos a eventos
> **Sentinel**: Evalua riesgo de episodios
> **Harbor**: Enruta alertas al personal
> **Recorder**: Captura evidencia NVR

***Scene Engine***
Traduce **observaciones** a **objetos semanticos** de alton nivel como `transiciones`.
_SceneEvent_: acostado, sentado en cama

***Sentinel Engine***
Monitorea **eventos** para gestionar el `ciclo de Episodio` y emite **senales de riesgo**.
_SentinelSignal_: 

***Harbor Engine***
Getiona el `presupuesto de notificaciones` y enruta **senales clinicas** a `canales`.
_SentinelSignal_: 

***Recorder Engine***
Monitorea **eventos** y desencadena la `captura de video` NVR antes y despues del incidente.
`Pre-Roll|Post-Roll` SensorHub -> DecisionEngine -> VideoRecorder


>
* BedId: Id de Cama
* ResidentId: Id de Residente
* EpisodeId: Evento continuo
* RuleId: Regla clinica
* EventRef: Hechos originales



**EventEnvelope**: [ `origen`, `tipo`, `id-unico`, `marca-temporal`, `payload` ]