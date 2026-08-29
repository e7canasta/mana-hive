The **Hisso** platform is a resident safety monitoring system designed for care facilities to process real-time sensor observations, detect resident activity, manage safety episodes, and dispatch alerts to staff. To ensure mathematical predictability and absolute reliability, the system isolates its core clinical logic inside a **hermetically sealed "Pure Domain"**. This domain code does not perform I/O, query databases, or directly interact with web frameworks like Spring Boot or NATS, keeping the core decision-making brain completely detached from infrastructure.

---

### The Four-Engine Core Pipeline

At the heart of the Hisso platform is a linear, four-stage reactive pipeline that operates on a **Consume-Evaluate-Publish** pattern. Each stage is a stateless, pure domain engine that receives inputs, evaluates them against specific calibrations, updates an internal state, and generates output without side effects.

#### 1. The Scene Engine

As the first stage of the pipeline, the **Scene Engine** translates raw, noisy sensor data (telemetry like radar or floor pressure) into high-level physical facts. It is responsible for maintaining a **Digital Twin** (the real-time virtual representation of the resident's current position).

- **State Mapping:** The engine translates telemetry into semantic human states like _Lying, Sitting, Bed Edge,_ or _Standing_.
- **Noise Filtering:** To prevent rapid "flickering" or "flapping" of states caused by minor tossing and turning, the engine uses **hysteresis** (a mandatory time buffer of, for example, 1500ms) and **confidence thresholds** (discarding telemetry below configured probabilities).
- **Time-Based Sweeps:** A component called the **ClockSweeper** tracks the passage of time rather than physical movements. It triggers "dwell warnings" if a resident stays in an unsafe state too long, "comeback exceeded" facts if a resident fails to return to bed from the bathroom, or "signal lost" events if sensor heartbeats drop.

#### 2. The Sentinel Engine

The physical facts produced by the Scene Engine flow into the **Sentinel Engine**, which applies clinical judgment to manage the lifecycle of a **Safety Episode**.

- **Episode Ledger:** It evaluates incoming physical events against a resident's clinical rules (the _SentinelCalibration_) and records active risks in an immutable, append-only **Episode Ledger**.
- **Severity Ramps & Escalation:** The Sentinel Engine supports increasing the severity of an existing episode (e.g., escalating from _Warning_ to _Critical_ if a resident exceeds their allowed standing time) rather than triggering multiple confusing alerts.
- **Umbrella Events:** To reduce cognitive overload, subsequent movements during an active crisis are grouped as "umbrella events" under the active episode, providing context (e.g., "resident is now in the bathroom") without generating duplicate alarms.
- **Auto-Recovery & Closure:** If a resident returns to a safe position on their own, the engine emits an `AutoRecovery` signal to dim active UI alerts, though the episode remains recorded. Episodes formally close based on conditions such as requiring staff presence, resident safety, or both.

#### 3. The Harbor Engine (Vigia)

The **Harbor Engine** (known internally as **Vigia**) handles delivery logistics and mitigates the massive medical issue of **alarm fatigue**. It transforms clinical findings from the Sentinel Engine into physical notifications.

- **Notice Channels:** Harbor routes notices to distinct channels such as mobile _Push_ notifications, local _Tablets_ outside rooms, centralized _Ward Boards_, or silent system _Consoles_.
- **Notification Budget:** It implements a strict `maxPerShift` limit for low-level warnings, tracking dispatched counts via a `NotificationBudget`. If the staff is overloaded and the budget is maxed out, non-essential alarms are suppressed.
- **Life Safety Override:** Critical safety events are entirely immune to the budget; life-safety alarms bypass all filters to ensure immediate routing and escalation.

#### 4. The Recorder Engine

Operating in parallel to Harbor, the **Recorder Engine** decides when to trigger video evidence collection for clinical reviews or legal audits.

- **Time Manipulation (Pre/Post-roll):** Since a video starting at the moment of an alarm only captures the aftermath of an incident, the engine uses rolling temporary camera buffers. When triggered, it grabs a set amount of footage _prior_ to the event (`recordBefore` pre-roll) and stitches it to the live stream (`recordAfter` post-roll) to preserve the incident's physical cause.
- **Ledger Synchronization:** An internal state machine prevents redundant or conflicting hardware commands by merging overlapping recording windows on a single camera.

---

### The Larger Platform Context

While the four engines process observations linearly, they depend on an orchestration, configuration, and messaging layer that connects them to the broader Hisso architecture.

#### 1. Runtime Orchestration

- **ResidentRuntime:** In production, this orchestrator wires the four synchronous domain engines together inside a single process, passing domain values sequentially through standard function calls. This avoids network and serialization overhead between stages. It guarantees thread safety and state isolation by dedicating a single-threaded executor per resident with synchronized access.
- **NightWatchRuntime:** This registry manages multiple `ResidentRuntime` instances concurrently. It queries a **Census** service to map physical `BedId` sensor data to the correct `ResidentId` pipeline and schedules background wall-clock ticks to trigger timed events across all active runtimes.

#### 2. Policy-Driven Calibration & Precedence

The pipeline's behavior is entirely dynamic, driven by policies that compile clinical intent into low-level rules.

- **The Politica Engine:** This engine acts as the system translator. It takes global facility laws (a `DagCatalog` structured as a Directed Acyclic Graph) and resident-specific risk profiles (`AlarmProfile`) to resolve a unified `PolicyCalibration` contract.
- **Precedence & Pre-Warning Derivation:** The resolver implements a three-layer model where **Overrides** beat **Templates**, which beat **Base Catalogs**. If a director sets an alarm deadline without a warning threshold, the resolver automatically derives a pre-warning at 50% of the limit.
- **Policy Adapters:** Once resolved, a suite of adapters (an Anti-Corruption Layer) projects the unified calibration into immutable, engine-specific domain configurations (e.g., `SceneCalibration`, `SentinelCalibration`).

#### 3. Messaging Backbone & Auditability

- **NATS JetStream:** Outside the pure domain, the microservices communicate asynchronously using NATS JetStream. Services utilize "Driving" and "Driven" NATS adapters to ingest facts and publish verdicts. A strict subject taxonomy (e.g., `scene.fact.v1.<bed>`) isolated by bed/resident IDs ensures data integrity and supports backward compatibility.
- **Resilience:** The connection strategy employs infinite asynchronous reconnection loops (`maxReconnects(-1)`) to ensure the application starts up and remains running even if the NATS broker is temporarily down.
- **Auditability and Fingerprinting:** To prevent "black-box" decision-making, policy resolutions are wrapped in an `Explained` object carrying an immutable, step-by-step receipt of how the rules were calculated. Furthermore, each resolved calibration includes a cryptographic `Fingerprint` hash, allowing auditors to verify the exact ruleset active during any historical incident.

---

🧩 Would you like to explore how the "ComeBack" inverse-dwell logic is mathematically structured within the Sentinel Engine's ledger, or shall we look at how the BDD testing blueprints simulate a resident's timeline?

