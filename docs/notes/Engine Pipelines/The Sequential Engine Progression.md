The core pipeline of the ** platform** is architected as a linear, four-stage reactive pipeline that isolates complex clinical decision-making within a hermetically sealed, pure domain core. Designed around hexagonal architecture principles, this pipeline decouples the mathematical and care logic of the system from infrastructure concerns like database transactions, web REST APIs, and message brokers.

Every stage in this pipeline is a stateless, pure domain engine that operates on a synchronous **Consume-Evaluate-Publish** pattern. Each engine receives input, evaluates it against a personalized calibration, updates its local representation of state, and emits outbound verdicts without causing external side effects or executing network I/O.

---

### The Sequential Engine Progression

The data flow travels in a strict, single-threaded sequence, transforming raw environmental metrics into human-comprehensible care and evidence-gathering actions:

```
 Raw Observation
       │
       ▼
┌──────────────┐   SceneFacts    ┌──────────────┐  SentinelSignals  ┌──────────────┐
│ Stage 1:     ├────────────────►│ Stage 2:     ├──────────────────►│ Stage 3:     │
│ Scene Engine │                 │ Sentinel Eng.│                   │ Harbor (Vigia│
└──────┬───────┘                 └──────┬───────┘                   └──────────────┘
       │                                │
       └───────────────┬────────────────┘
                       │ (SceneFacts & SentinelSignals)
                       ▼
               ┌──────────────┐
               │ Stage 4:     │
               │ Recorder Eng.│
               └──────────────┘
```

#### 1. Stage 1: The Scene Engine (Translating Physics)

As the entry point of the pipeline, the **Scene Engine** ingests raw, unstructured sensor telemetry (e.g., floor radars or pressure bed outputs). It maintains a virtual **Digital Twin** representing the resident's real-time physical state (such as `Lying`, `Sitting`, or `Standing`).

- **Noise Filtering:** To prevent noisy environments from causing fake alerts, the engine applies confidence thresholds and **hysteresis** (a configurable time buffer that must be outlasted to confirm a transition).
- **Time-Based Facts:** By using a background **ClockSweeper**, the engine generates temporal movement facts (like `DwellExceeded` or `ComeBackExceeded`) even when the sensors are completely silent. It then publishes these verified occurrences as `SceneEvents` (or `SceneFacts`).

#### 2. Stage 2: The Sentinel Engine (Applying Clinical Judgment)

The **Sentinel Engine** consumes the physical realities produced by Stage 1 and applies medical logic to evaluate safety risk.

- **Lifecycle Management:** It evaluates the `SceneEvents` against a personalized clinical profile (`SentinelCalibration`) to manage active safety **Episodes** inside an immutable, append-only `EpisodeLedger`.
- **Alert Ramps and Umbrella Events:** It manages the escalation of an episode's severity (e.g., ramping a warning to critical if standing limits are breached) and groups minor sub-movements under the active crisis as "umbrella events" to keep dashboards uncluttered. Once finished, it publishes logical verdicts known as `SentinelSignals`.

#### 3. Stage 3: The Harbor Engine / Vigia (Managing Notice Logistics)

The **Harbor Engine** (known internally as **Vigia**) takes the logical `SentinelSignals` from Stage 2 and translates them into physical delivery commands (`NoticeCommand`).

- **Channel Distribution:** It routes notifications across distinct channels like console logs, mobile pushes, room tablets, or station-central ward boards based on escalation timers and severities.
- **The Notification Budget:** To actively combat **alarm fatigue**, Vigia tracks outbound shift alerts. If non-essential warnings exceed a resident's configured shift maximum, the engine suppresses the physical dispatch command to safeguard caretakers' cognitive bandwidth—though life-safety critical alarms always override suppression.

#### 4. Stage 4: The Recorder Engine (Contextual Evidence)

The **Recorder Engine** operates in parallel, consuming events from both Stage 1 and Stage 2 to instruct NVR (Network Video Recorder) hardware when to document incidents.

- **Pre/Post-Roll Buffers:** By using rolling temporary camera buffers, the engine extracts footage from before the trigger occurred (`recordBefore`) and stitches it to the post-event footage (`recordAfter`) to capture the catalyst and aftermath of a fall.
- **Deduplication:** A state machine (`RecordingLedger`) prevents redundant camera requests by merging overlapping triggers on the same bed, protecting the facility's camera hardware from system crashes.

---

### Production Orchestration and Purity

By dividing system responsibilities sequentially, Hisso avoids the common pitfall of mixing environmental data parsing with communication and reporting logistics.

In a production environment, this linear flow is wired and driven by the **`ResidentRuntime` orchestrator**. Instead of leveraging NATS messaging streams to pass data sequentially between these four domains—which would introduce latency, deserialization overhead, and transaction handshakes—the orchestrator manages the active engine states in-memory. When a raw observation is received, the orchestrator passes values sequentially down the engine chain using standard synchronous Kotlin function calls, compiling all final commands, alerts, and recording requests into a unified **`Outbound` container** for downstream adapters to distribute.

---

🧩 I can map out how a specific sequence of "Jose 301" movements triggers the NVR, merges overlapping recording windows, and outputs the final commands if you'd like to visualize the pipeline in action.