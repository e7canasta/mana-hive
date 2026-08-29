### **Core Responsibility and Pipeline Role**

The **Sentinel Engine** serves as the **clinical judgment center** of the Hisso platform, positioned as the **second stage** in the system's linear, four-stage reactive pipeline. While the preceding **Scene Engine** acts as the "eyes" of the system by translating raw sensor telemetry into physical, semantic movement facts (such as a resident sitting or standing), the Sentinel Engine applies medical and care reasoning to those facts to determine and manage clinical risk.

When the Scene Engine emits a physical fact (`SceneEvent`), the Sentinel Engine consumes it. It evaluates this event against a resident's highly personalized clinical rules to manage the active lifecycle of a **Safety Episode**. It then produces logical alerts and signaling outputs (`SentinelSignal`), which are handed down sequentially to the **Harbor Engine (Vigia)** for routing and cognitive fatigue filtering, and to the **Recorder Engine** to initiate Network Video Recorder (NVR) evidence captures.

---

### **Inside the Pure Domain Logic of the Sentinel Engine**

As a hermetically sealed **Pure Domain** component, the Sentinel Engine is stateless and completely agnostic to databases, messaging networks, or web frameworks. It implements a strict **Consume-Evaluate-Publish** execution pattern:

1. **Evaluation Input and Output:** The engine takes the resident's current state shell—an immutable, append-only **`EpisodeLedger`**—and evaluates an incoming `SceneEvent` against an immutable **`SentinelCalibration`** ruleset. It returns a **`SentinelVerdict`** containing the updated ledger state and a list of generated signals.
    
2. **Episode Lifecycle Management:**
    
    - **Episode Opening:** If an incoming physical event violates a configured clinical rule, and there is no active crisis currently recorded for that room, the engine opens a new safety episode in the ledger and broadcasts an `EpisodeOpened` signal.
    - **Severity Ramps (Escalation):** The Sentinel Engine supports dynamic severity escalation. For instance, if a resident transitions to standing, the system might open an episode at a `WARNING` severity level. If the resident remains standing and breaches a duration (Dwell) limit, the engine evaluates this fact against the open ledger and ramps the existing episode's severity to `CRITICAL` rather than creating a duplicate, confusing alarm.
    - **Umbrella Events:** To actively prevent nurse desensitization and clutter, movements that occur under an active crisis that do not trigger escalation are logged as `UmbrellaEvent` signals. This nests subsequent actions (e.g., the resident shifting from sitting to standing during a fall risk event) under the "umbrella" of the original open episode to maintain rich context without generating new alarms.
    - **Auto-Recovery:** If a resident safely returns to their baseline state (such as lying back down in bed) and the underlying rule is marked as reversible, the engine emits an `AutoRecovery` signal. This instructs the UI layers to visually dim the alert on tablet dashboards, signaling that the immediate physical danger has passed without requiring staff to rush to the room.
    - **Episode Closure Conditions:** An episode formally closes via an `EpisodeClosed` signal, but only when specific **`ClosureCondition`** parameters are satisfied:
        - _`SAFE_ONLY`_: Closes automatically once the resident returns to a safe position.
        - _`STAFF_OR_SAFE`_: Closes on either physical staff intervention OR resident recovery.
        - _`STAFF_AND_SAFE`_: The most restrictive clinical rule—requiring a physical nurse arrival (RFID check-in) AND verified physical safety to close.
3. **ComeBack Judgment (Inverse Dwell) Logic:** The Sentinel Engine possesses specialized logic to monitor the **absence** of a baseline state (typically `LYING` down in bed). When a resident leaves bed to visit the bathroom, the engine schedules a future countdown alert. If the resident returns safely within the designated timeline, the countdown is dissolved. If the timer expires, the engine fires a `ComeBackExceeded` alarm. It can also generate proactive `ComeBackPreWarning` alerts when a countdown is nearing its limit, giving nurses a gentle, preventative heads-up before an incident escalates.
    

---

### **The Wider Platform Context**

The Sentinel Engine is structurally isolated, yet deeply integrated into the Hisso infrastructure through policy compilation, orchestrations, and asynchronous messaging:

- **Policy Calibration via Politica:** Clinical care requirements are not hard-coded. A nurse director registers high-level care plans (such as watch levels) in the **Hub Service (System of Record)**. The **Politica Engine** resolves these layers (using a Catalog \(\rightarrow\) Template \(\rightarrow\) Override precedence model) into unified `PolicyCalibration` contracts. A dedicated **Sentinel Adapter** (acting as an Anti-Corruption Layer) extracts the resolved alert rules, trigger types, and closure rules to compile the immutable `SentinelCalibration` used by the engine.
- **Production Orchestration (`ResidentRuntime`):** In a deployed care facility, physical observation pipelines must execute with near-zero latency. The `ResidentRuntime` orchestrator hosts all four engines sequentially in memory. When an observation is processed, the runtime executes the Scene Engine, passes its output directly to the Sentinel Engine's evaluator in-memory, and immediately feeds the resulting `SentinelVerdict` to Harbor and Recorder—entirely bypassing network or serialization bottlenecks.
- **Infrastructure Messaging (NATS JetStream):** In the microservices topology, the domain engines are wrapped in Spring Boot shells that interact via an asynchronous NATS bus. The **`sentinel-service`** employs a driving **`SentinelNatsIngest`** adapter to subscribe to the stream of Scene facts (`scene.fact.v1.>`). It maintains active, thread-safe `EpisodeLedger` instances per resident in an in-memory `ConcurrentHashMap`, executes the Sentinel domain logic on incoming events, and utilizes **`SentinelNatsEgress`** to publish resulting signals back to the persistent bus under the versioned subject taxonomy `sentinel.signal.v1.<bed>`.

---

🎧 Would you like to generate a brief audio overview focusing on how the Sentinel Engine's "ComeBack" inverse-dwell timers are mathematically modeled to prevent patient falls?