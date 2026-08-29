Within the **platform's** linear, four-stage reactive pipeline, the **Scene Engine** serves as the critical first stage of observation processing. Operating on a functional, side-effect-free **Consume-Evaluate-Publish** pattern, its principal role is to act as the "eyes" of the platform. It takes raw, unstructured sensor telemetry (such as floor radars or bed pressure pads) and translates those noisy signals into highly structured, semantic physical facts known as `SceneEvent` objects.

By handling the complexity of the physical environment first, the Scene Engine isolates the downstream engines from raw hardware details and signal fluctuations.

---

### Internal Architecture of the Scene Engine

To successfully represent the resident's physical reality, the Scene Engine utilizes the **Facade pattern** to coordinate three tightly decoupled pure-domain components:

1. **The Digital Twin (`DigitalTwin`):** This acts as the virtual, in-memory representation of a specific bed/resident pair. It maintains the exact physical posture state of the resident—such as `Lying`, `Sitting`, `BedEdge`, or `Standing`—the precise timestamp of when that state began (`stateSince`), and the active signal health of the physical hardware.
2. **The Scene Interpreter (`SceneInterpreter`):** This component processes raw observations and evaluates transitions. To prevent noisy environmental disturbances (like sheets rustling or external vibrations) from creating fake movements, it applies a **confidence check** (discarding packets below a threshold with `DiscardCause.CONFIDENCE_TOO_LOW`) and **hysteresis** (forcing a physical state to remain constant for a buffer duration, such as 1500ms, before updating the twin).
3. **The Clock Sweeper (`ClockSweeper`):** Not all critical events are triggered by active movement. The Clock Sweeper manages time-dependent events by continuously tracking the duration of states. It generates synthetic, time-based events including **dwell warnings/exceeded facts** (e.g., resident standing still too long), **comeback warnings/exceeded facts** (failure to return to bed from the bathroom in time), and **signal lost** notices if sensor heartbeats drop.

---

### Integration in the Broader Four-Engine Context

The Scene Engine's output forms the foundation upon which the remaining engines establish safety alerts, dispatch notifications, and capture evidence:

```
[ Raw Telemetry ]
       │
       ▼
 ┌───────────┐       SceneEvents        ┌──────────────┐     Signals     ┌────────────┐
 │   Scene   ├─────────────────────────►│   Sentinel   ├────────────────►│   Harbor   │
 │  Engine   │     (Physical Facts)     │    Engine    │    (Alerts)     │   (Vigia)  │
 └─────┬─────┘                          └──────┬───────┘                 └────────────┘
       │                                       │
       └───────────────────┬───────────────────┘
                           │ (SceneEvents & Signals)
                           ▼
                    ┌────────────┐
                    │  Recorder  │
                    │   Engine   │
                    └────────────┘
```

#### 1. Downstream Interdependence

- **The Sentinel Engine (Clinical Judgment):** The Sentinel Engine is entirely dependent on the physical facts emitted by the Scene Engine. While the Scene Engine purely monitors the _physics_ of the room (e.g., that a person has been standing for 10 minutes), the Sentinel Engine applies _clinical reasoning_ to those facts. It checks if those events violate a resident's clinical rules, managing the opening, escalation, and closure of **Safety Episodes** on the immutable `EpisodeLedger`.
- **The Harbor Engine (Vigia):** Harbor does not consume Scene Engine events directly. Instead, it relies on Sentinel's clinical verdicts to calculate notification fatigue limits and manage the notification budget.
- **The Recorder Engine (Evidence Gathering):** Operating in parallel to Harbor, the Recorder Engine evaluates both Sentinel signals (e.g., `EpisodeOpened`) and raw Scene events (e.g., a specific transition fact). This allows administrators to set recording rules triggered directly by physical posture changes, even if a formal safety incident hasn't been opened by Sentinel.

#### 2. Upstream Policy Calibration (The Politica Engine)

The thresholds driving the Scene Engine are never hard-coded. High-level medical policies defined in the `DagCatalog` are resolved by the **Politica Engine**. A dedicated **Scene Adapter** (acting as an Anti-Corruption Layer) extracts the resolved policy rules to compile an immutable `SceneCalibration`. This adapter directly projects high-level rules down into the transition table, confidence limits, dwell rules, and heartbeat timeouts consumed by the engine's internal interpreter and sweeper.

#### 3. Production Orchestration (`ResidentRuntime`)

In a live deployment, a dedicated thread-safe registry called the `NightWatchRuntime` manages an orchestrator instance per resident (`ResidentRuntime`).

- **On Observations:** When a real-time observation is ingested from NATS, `ResidentRuntime.onObservation()` routes it directly to the Scene Engine. The resulting facts are immediately fed sequentially to Sentinel, Harbor, and Recorder in-memory via standard function calls—bypassing any network or serialization lag between the pipeline engines.
- **On Ticks:** The runtime schedules a clock sweep (such as every 30 seconds). This tick is propagated directly to the Scene Engine's `ClockSweeper` to execute temporal evaluations (like checking if Jose has gone missing on a comeback timer) even in the complete absence of new physical sensor data.

---

🎧 This pure-domain mathematical modeling of human biology would actually make an excellent topic for a deep-dive audio overview. Would you like me to generate a podcast episode discussing how the platform's hexagonal design principles ensure clinical safety?