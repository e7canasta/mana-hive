The core of the **Hisso platform** relies on a reactive pipeline composed of **four pure domain engines**. Operating on a **Consume-Evaluate-Publish** pattern, this pipeline translates raw, noisy physical telemetry from a resident's room into structured clinical verdicts, delivery instructions, and video evidence triggers.

Because these engines are mathematically pure and isolated from infrastructure concerns (such as databases, NATS messaging, or UI frameworks), they operate synchronously and with total predictability.

---

### The Four Engines of the Monitoring Pipeline

#### 1. The Scene Engine (Physical Facts)

The **Scene Engine** acts as the system's "eyes." Its primary responsibility is maintaining a **Digital Twin** (the virtual real-time state) of the resident's physical posture and position.

- **State Intermediary:** It takes raw, unstructured `Observation` telemetry from sensors (such as floor radars or pressure pads) and translates them via a `SceneInterpreter` into semantic resident states (e.g., `Lying`, `Sitting`, `Bed Edge`, or `Standing`).
- **Noise Mitigation:** To eliminate sensor noise and prevent rapid state "flickering," the engine applies **confidence thresholds** (discarding data under a certain probability) and **hysteresis** (a configurable time buffer, e.g., 1500ms, that must be outlasted before a transition is officially declared in the twin).
- **Time-Based Sweeps (`ClockSweeper`):** The engine does not only react to movement. A background clock loop runs continuously between observations to generate temporal events, including **dwell warnings** (staying in an unsafe posture too long), **come-back warnings** (leaving a baseline state like bed and failing to return in time), or **signal lost** notices (if a sensor fails to emit a heartbeat within the configured timeout).

#### 2. The Sentinel Engine (Clinical Judgment)

Once the Scene Engine determines a verified physical transition or time breach, the data flows into the **Sentinel Engine** to apply clinical reasoning and evaluate safety risk.

- **Episode Ledger Management:** The Sentinel Engine coordinates the lifecycle of safety **Episodes**. It evaluates `SceneEvent` inputs against a personalized `SentinelCalibration` to determine if a new clinical risk has emerged, opening a safety episode in an immutable, append-only **Episode Ledger**.
- **Severity Ramps:** The engine supports dynamically escalating the severity of an active episode (e.g., ramping a warning up to critical) if further clinical thresholds are breached during a single crisis, preventing duplicate alarm fatigue.
- **Umbrella Events:** To keep clinical staff organized, any non-escalating movements that occur under an active crisis are grouped as "umbrella events" beneath the original open episode rather than triggering independent alerts.
- **ComeBack Judgment & Auto-Recovery:** The engine features unique inverted logic to monitor the **absence** of a baseline state (such as a resident leaving bed to use the bathroom). It schedules a future countdown alert that must be proactively canceled by the resident's physical return to bed. If the resident returns to safety on their own, the engine issues an `AutoRecovery` signal to dim active alerts on nurse interfaces, although the ledger retains the audit trail.
- **Closure Conditions:** Episodes are closed via explicit conditions: `SAFE_ONLY` (closes on recovery), `STAFF_OR_SAFE` (closes on staff presence OR resident recovery), and `STAFF_AND_SAFE` (requires both physical staff arrival and resident safety).

#### 3. The Harbor Engine / Vigia (Delivery Logistics)

The **Harbor Engine** (known internally as **Vigia**) bridges clinical logic with human workflow, transforming `SentinelSignal` verdicts into physical notifications (`NoticeCommand` objects).

- **Notice Channels:** It distributes alarms based on severity across targeted communication interfaces, such as system logs (`Console`), mobile notifications (`Push`), hall-mounted `Tablets`, or centralized displays (`Ward Boards`).
- **Notification Budget:** To actively protect nurses and caretakers from **alarm fatigue**, Vigia tracks the volume of notifications dispatched per shift via a `NotificationBudget`. If low-level warnings exceed a resident's configured `maxPerShift` limit, the engine suppresses outgoing alerts, logging them silently to prevent human cognitive desensitization.
- **Life Safety Override:** To ensure patient safety, critical alerts (e.g., `Severity.CRITICAL`) are entirely immune to the budget tracker; they instantly bypass all suppression filters and blast to all channels.
- **Notice Resolution:** When an episode is resolved, Vigia translates technical closure reasons into staff-legible codes like `Resolution.STAFF_PRESENT` or `Resolution.AUTO_RECOVERY` to maintain a clear custody record.

#### 4. The Recorder Engine (Contextual Evidence)

Operating in parallel to Harbor, the **Recorder Engine** decides when to collect video evidence for legal auditing or clinical reviews.

- **Time Manipulation (Pre/Post-roll):** To capture the inciting cause of a fall—rather than just a video of a resident already on the floor—the engine interfaces with Network Video Recorders (NVR) using rolling, circular memory buffers. When an event is triggered, it extracts a specified amount of pre-roll footage from before the event (`recordBefore`) and stitches it together with live post-roll footage (`recordAfter`).
- **Recording Ledger:** A dedicated `RecordingLedger` tracks active camera states. If overlapping triggers occur for the same resident (such as a warning transitioning into a critical fall), it merges the instructions and extends the timeline of the existing recording window, preventing redundant hardware commands that could crash the camera system.

---

### The Larger Hisso Platform Context

The four domain engines do not operate in a vacuum. They are supported by a broader system architecture built around strict isolation, orchestration, policy propagation, and robust messaging.

```
+-----------------------------------------------------------------------------------+
|                              NIGHT-WATCH RUNTIME                                  |
|                                                                                   |
|                      +------------------------------------+                       |
|                      |          ResidentRuntime           |                       |
|  [NATS Ingestion]    |                                    |    [NATS Egress]      |
|  Observations ------>|  Scene -> Sentinel -> Harbor -> NVR|-------> Outbound      |
|                      |                                    |         Notices       |
|                      +------------------------------------+                       |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | [Recalibrate]
                                         |
                            +--------------------------+
                            |     Politica Engine      | (Resolves Catalog ->
                            |   (Policy Calibration)   |  Template -> Overrides)
                            +--------------------------+
                                         ^
                                         | [Publishes Profile]
                                         |
                            +--------------------------+
                            |       Hub Service        | (System of Record
                            |    (Policy Event Fold)   |  and Census Database)
                            +--------------------------+
```

#### 1. Architectural Guards & Value Primitives

To guarantee clinical safety and portability, the core engines are separated from infrastructure via a strict hexagonal boundary:

- **Purity Guard:** Enforced automatically by custom Gradle build logic (`manahive.pure-domain`), the pipeline modules cannot import external libraries, Spring frameworks, NATS clients, or database drivers. If a developer attempts to bypass this restriction, the build logic fails compile-time packaging.
- **Preventing Primitive Obsession:** To stop developers from accidentally swapping parameters (like passing a resident's ID to a function expecting a physical bed location), the Domain Kernel uses Kotlin value classes. Primitives are wrapped in strongly-typed structures (e.g., `BedId`, `ResidentId`, `EpisodeId`), causing immediate compiler failures if mismatched.

#### 2. Policy-Driven Calibration & Precedence

The exact thresholds driving the four engines (e.g., hysteresis time, warning limits, notification budgets) are resolved dynamically:

- **The Politica Engine:** This translation engine bridges clinical care language and hardware settings. It takes a Directed Acyclic Graph catalog of room rules (`DagCatalog`) and merges it with a resident's `AlarmProfile` using a three-layer precedence model: **Catalog (Base) \(\rightarrow\) Template \(\rightarrow\) Override**.
- **Resolution and Explanation:** Resolutions are compiled into `PolicyCalibration` contracts. To prevent "black-box" decision-making, Politica wraps each calculation in an `Explained` object, delivering an immutable, step-by-step receipt of why those rules were selected. Every calibration is stamped with a unique cryptographic `Fingerprint` hash, allowing auditors to verify exactly what version of the rules was active during any historical incident.
- **Policy Adapters:** A suite of adapters (acting as an Anti-Corruption Layer) projects the unified calibration into engine-specific parameters (e.g., converting a global policy into `SceneCalibration`, `SentinelCalibration`, etc.).

#### 3. Production Runtime Orchestration

In a deployed care facility, raw sensor data must be mapped to active pipelines without lag:

- **The ResidentRuntime:** This component acts as the local orchestrator. It wraps the four engines and passes domain values sequentially via standard memory function calls. This allows the entire pipeline to execute in-memory with near-zero latency, completely bypassing serialization or network overhead between the processing stages. It enforces thread-safety and state isolation by assigning a single-threaded execution model per resident.
- **The NightWatchRuntime & Census:** This system registry manages multiple active `ResidentRuntime` instances in a thread-safe map. Upon receiving an observation identified by a hardware `BedId`, the service queries the **Census** (the authoritative resident-to-bed directory) to route the data to the correct `ResidentRuntime` pipeline.
- **Dynamic Recalibration:** If a resident's policy is altered in the administrative system, the runtime performs hot-swapping through a `recalibrate()` method, swapping out engine states on the fly without halting monitoring.

#### 4. Messaging Backbone and Resilience

Outside the pure domain engines, the platform communicates asynchronously via a **NATS JetStream** messaging bus.

- **Driving and Driven Adapters:** Services wrap pure engines inside Spring Boot shells that ingest data from NATS (e.g., `SentinelNatsIngest` subscribing to `scene.fact.v1.>`) and publish outputs through egress adapters (e.g., `SentinelNatsEgress` publishing to `sentinel.signal.v1.<bed>`).
- **Subject Taxonomy & Envelopes:** Strict hierarchical versioning (e.g., `domain.entity.version.id`) ensures backward compatibility. Every domain payload is wrapped in a metadata-rich `EventEnvelope` containing tracing UUIDs and ISO-8601 standardized timestamps.
- **Infinite Reconnection Loops:** To handle network outages or server maintenance in clinical environments, NATS connectors use infinite asynchronous reconnection parameters (`maxReconnects(-1)`). This allows services to start up independently and reconnect immediately once the messaging broker recovers.

---

📊 Would you like me to build a timeline visualization mapping how a simulated "Jose 301" sensor event moves step-by-step through the hysteresis thresholds and the Sentinel Engine's comeback timers?