The **ResidentRuntime** serves as the primary execution unit and the physical orchestrator of the Hisso platform's core pipeline. While the platform's architectural blueprint is designed around a decentralized, asynchronous NATS JetStream messaging bus, executing this multi-stage pipeline across network boundaries for every micro-movement would introduce unacceptable latency and serialization overhead.

To solve this, the `ResidentRuntime` encapsulates the entire stateful four-engine pipeline (**Scene > Sentinel > Harbor > Recorder**) for a single resident within a single process. By orchestrating these stateless, pure-domain engines in-memory, the runtime coordinates complex clinical decision-making with maximum speed and mathematical predictability.

---

### In-Memory Execution: Ingestion and Ticking

The `ResidentRuntime` manages data ingress and background timing through two core methods, executing on a strict **Consume-Evaluate-Publish** pattern:

#### 1. Real-Time Processing (`onObservation`)

When raw sensor telemetry is received, it is routed to the runtime's entry point, `onObservation()`.

- **Temporal Integrity:** The method first enforces temporal ordering. Observations that arrive with a timestamp older than the runtime's recorded `lastObservedAt` are immediately discarded to prevent clock corruption in the resident's state model.
- **Linear Stage Composition:** If valid, the runtime executes the four engines sequentially using standard, synchronous Kotlin function calls on pure domain values:
    1. **Scene Stage:** Passes the raw `Observation` to the **Scene Engine** to update the resident's virtual `DigitalTwin` and produce interpreted `SceneFacts` (such as `TransitionDetected` or `DwellExceeded`).
    2. **Sentinel Stage:** Passes those physical facts directly to the **Sentinel Engine**, which evaluates clinical rules, updates the resident's `EpisodeLedger`, and generates logical `SentinelSignals`.
    3. **Harbor Stage:** Forwards those signals to the **Harbor Engine (Vigia)**, which evaluates the active notification budget and generates `NoticeFor` pairings matching clinical alerts to target delivery commands.
    4. **Recorder Stage:** Finally, the **Recorder Engine** evaluates both the Scene facts and Sentinel signals in parallel to emit concrete `RecordingCommand` instructions for the camera hardware.

#### 2. Temporal Sweeping (`onTick`)

Because critical care events frequently occur due to the _absence_ of movement (such as a resident remaining frozen in an unsafe state or failing to return from the bathroom in time), the pipeline cannot rely on sensor telemetry alone. The runtime uses `onTick()`, which is driven by background wall-clock ticks. Every 30 seconds, `onTick` executes background stopwatch sweeps across the engines, triggering time-based events like "Dwell Exceeded," "ComeBack Exceeded," or hardware "Signal Lost" completely independent of active sensor data.

---

### Pipeline Data Consolidation

To deliver these computed results back to the infrastructure layer, the orchestrator utilizes two highly typed data containers:

- **`NoticeFor`:** A specialized runtime container that explicitly pairs a `SentinelSignal` with its resulting `NoticeCommand`. This guarantees that when a notification is dispatched, downstream NATS adapters preserve the complete clinical context of _why_ the message was generated.
- **`Outbound`:** Rather than broadcasting messages incrementally mid-execution, the runtime collects all side effects produced during a single pipeline run into a single `Outbound` container. It gathers `sceneFacts`, clinical `signals`, `notices` (`NoticeFor` pairings), and NVR `recordings`. This container is handed off in a single transaction to the surrounding Spring Boot microservice shell (such as the `NightWatchService`), which distributes the payloads to their respective NATS JetStream subjects.

---

### Thread Safety, Isolation, and Resiliency

Because the Hisso platform monitors high-stakes patient health, the `ResidentRuntime` is engineered with defensive runtime constraints to eliminate concurrency errors:

- **Strict Thread Isolation:** The runtime is explicitly designed for **single-threaded execution per resident**. The global registry—the `NightWatchRuntime`—manages active `ResidentRuntime` instances inside a thread-safe `ConcurrentHashMap`. This ensures that while multiple rooms are processed in parallel, the telemetry stream for any single resident is processed in strict, sequential order to maintain state integrity.
- **State Encapsulation:** All internal engine states (`DigitalTwin`, `EpisodeLedger`, `HarborState`, `RecordingLedger`) are private variables within the runtime. No external thread can modify a resident's physical or clinical state outside of the linear pipeline execution.
- **Dynamic Recalibration:** When a resident's care settings or watch levels are updated via the administrative Hub Service, the orchestrator executes a hot-swap using `recalibrate(EngineCalibrations)`. The runtime marks its `calibrations` field as `@Volatile`. This allows the system to instantly replace the active engine calibrations on the fly without halting or restarting the real-time monitoring pipeline.

---

🔍 Since we have detailed the orchestrator, would you like to discuss the **Politica Engine's** multi-layer resolution model and how it compiles these dynamic clinical overrides down into the `EngineCalibrations` consumed by the runtime?