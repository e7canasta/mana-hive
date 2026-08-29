Within the linear, four-stage reactive pipeline of the Hisso platform, the **Recorder Engine** serves as the fourth and final stage. Its primary responsibility is to translate domain events into concrete, actionable `RecordingCommand` instructions for the facility's Network Video Recorder (NVR) adapter.

By acting as the system's "unblinking eye," it ensures that critical safety incidents are visually documented for clinical reviews, care plan adjustments, and regulatory compliance.

---

### 1. The Dual-Trigger Processing Model

Unlike downstream engines such as the Harbor Engine (Vigia), which only evaluate clinical signals from Stage 2, the Recorder Engine **runs in parallel to Harbor and consumes events from both Stage 1 and Stage 2**. It evaluates:

- **Scene Events (`SceneEvent`):** Low-level physical transitions detected by the Scene Engine (e.g., a resident transitioning from bed to standing).
- **Sentinel Signals (`SentinelSignal`):** Logical clinical alerts raised by the Sentinel Engine (e.g., a critical fall risk episode being opened or escalated).

By matching these inputs against an active configuration, the engine determines whether to start or stop recording. This dual-ingest capability allows administrators to configure recording rules based on physical movements directly, even if the Sentinel Engine hasn't formally declared a clinical emergency.

### 2. Time Manipulation: Pre-Roll and Post-Roll

Capturing video only _after_ an alert is generated is often clinically useless, as it merely shows the aftermath of an incident (such as a resident already on the floor). To solve this, the Recorder Engine utilizes **rolling temporary memory buffers** maintained continuously by the camera hardware.

Using the configuration parameters **`recordBefore` (pre-roll)** and **`recordAfter` (post-roll)**, the engine performs a "time travel" extraction:

- When a trigger occurs, `recordBefore` instructs the NVR to reach back into its temporary memory buffer and retrieve a specified window of footage (e.g., 30 seconds) immediately preceding the trigger.
- This pre-roll footage is stitched to the live event and the post-roll window dictated by `recordAfter`, producing a single, seamless video file that preserves the precise physical cause of the incident.

### 3. State Management: The Recording Ledger

To prevent hardware crashes and network congestion in a chaotic care environment, the engine relies on the **`RecordingLedger`** to act as its state machine.

- **Deduplication & Extension:** If overlapping triggers occur for the same resident (e.g., a warning transition followed quickly by a critical fall alert), the ledger intercepts the duplicate commands. Instead of spinning up a redundant video stream that could overwhelm the camera, the ledger merges the events and simply extends the timeline of the existing recording window.
- **State Tracking:** It tracks recording sessions through distinct states (`STARTING`, `RECORDING`, `STOPPING`) and manages the lifecycle of the resulting `EvidenceRecord` objects.

### 4. Highly Typed Primitives & Quality Control

To maintain the platform's strict standard of mathematical determinism, the Recorder Engine avoids passing loose text strings:

- **Command Primitives:** It emits a sealed interface of `RecordingCommand` variants. `RecordingStarted` carries the strongly-typed `RecordingTarget` (`BedId` + `Monitor`), `RecordingConfig` (start time and quality), and a `RecordingContext` showing whether the clip is standalone or `TiedToEpisode`. `RecordingStopped` signals the NVR to finalize the stream at a specific `Instant`.
- **Quality Presets:** Recording quality is strictly represented via a structured `Quality` value object, allowing the system to balance clinical urgency with local server storage limits:
    - **`SD`**: 640x480 @ 15fps (1Mbps)
    - **`HD`**: 1280x720 @ 30fps (5Mbps)
    - **`FULL`**: 1920x1080 @ 30fps (10Mbps)

---

### The Larger Platform Context

Within the broader platform architecture, the Recorder Engine is fully integrated into Hisso's configuration, orchestration, and messaging layers:

```
+-----------------------------------------------------------------------------------------+
|                                    NATS BUS SYSTEM                                      |
|                                                                                         |
|       scene.fact.v1.>  ────────┐                                                        |
|                                │                                                        |
|       sentinel.signal.v1.>  ───┼───► [RecorderNatsIngest]                               |
|                                      (Driving Adapter / Ingest)                         |
|                                                │                                        |
|                                                ▼                                        |
|                                      [RecorderEngineImpl] ───► Updates RecordingLedger  |
|                                      (Stateless Evaluator)                              |
|                                                │                                        |
|                                                ▼                                        |
|                                      [RecorderNatsEgress]                               |
|                                      (Driven Adapter / Egress)                          |
|                                                │                                        |
|                                                ▼                                        |
|       recorder.command.v1.<bed>  ──────────────┘                                        |
+-----------------------------------------------------------------------------------------+
```

- **Purity & Architectural Guards:** The engine's core logic sits inside the `recorder-domain` module, which is enforced as a **Pure Domain** module by custom Gradle convention plugins. It contains no NATS, database, or Spring frameworks, allowing it to remain completely platform-agnostic.
- **Policy Projections (The Anti-Corruption Layer):** A care director's natural language rules are compiled by the **Politica Engine** into a global `PolicyCalibration` contract. A dedicated **Recorder Adapter** (`toRecordingCalibration`) projects this contract into the immutable `RecordingCalibration` consumed by the engine's evaluator, resolving custom pre/post-roll windows and specific state transition triggers.
- **Microservice Messaging (NATS Shell):** In the distributed deployment, the **`recorder-service`** acts as an imperative shell around the pure domain. The driving adapter **`RecorderNatsIngest`** subscribes to both the `SCENE` and `SENTINEL` streams (`scene.fact.v1.>` and `sentinel.signal.v1.>`), maps incoming envelopes to `SceneFactTrigger` or `SentinelSignalTrigger` domain classes, and delegates evaluation to the engine. Commands are then pushed back to the NATS bus on the versioned subject `recorder.command.v1.<bed>`.
- **Orchestration (`ResidentRuntime`):** In the single-process runtime, the `ResidentRuntime` coordinates Stage 4 alongside the other engines. When an observation is processed, the runtime feeds the resulting facts and clinical signals directly to the Recorder stage in-memory, completely bypassing serialization and network latencies.
- **Offline Auditing & Replays:** The companion `recorder-batch` module features a **`SignalParser`**. This parser allows engineers to feed historical JSONL logs of past resident activities directly into the pure domain, enabling facilities to simulate and test new recording calibrations against historical data without affecting real-time operations.

---

🔍 Would you like me to create a visual diagram mapping out how a specific sequence of "Jose 301" movements triggers the NVR, merges overlapping recording windows, and outputs the final `ClipCreated` command?