### Position and Core Responsibility in the Pipeline

Within the Hisso platform’s four-stage, linear reactive pipeline (**Scene \(\rightarrow\) Sentinel \(\rightarrow\) Harbor \(\rightarrow\) Recorder**), the **Harbor Engine** (known internally as **Vigia**, translating from Spanish and Portuguese to "lookout" or "watchman") serves as the **third stage**.

While the preceding engines establish the physical state of the room (**Scene Engine**) and apply clinical judgment to manage safety episodes (**Sentinel Engine**), the Harbor Engine focuses strictly on **delivery logistics and notification fatigue**. It acts as a system safeguard—or "shield of empathy"—to translate clinical alerts into physical notification instructions while mathematically protecting the staff's cognitive bandwidth from the dangerous effects of **alarm fatigue**.

---

### Signal Translation and Notice Commands

Operating as a stateless, pure domain evaluator, the Harbor Engine receives incoming clinical verdicts (`SentinelSignal` objects) and transforms them into physical dispatch instructions (`NoticeCommand` objects).

```
 ┌────────────────┐              ┌────────────────┐              ┌───────────────┐
 │ Sentinel Engine│             │ Harbor Engine  │             │ Downstream    │
 │ (Clinical      ├────────────►│ (Vigia - Pure  ├────────────►│ Notification  │
 │  Signals)      │             │  Domain Core)  │             │ Channels)     │
 └────────────────┘             └───────┬────────┘             └───────────────┘
                                        │
                                        ▼
                                 [NoticeRegistry]
                             (Tracks active dispatches)
```

The engine maps clinical events to output actions via the following lifecycle logic:

- **`EpisodeOpened`**: After checking the notification budget, the engine generates a new alert via `NoticeCommand.Dispatch`.
- **`EpisodeClosed`**: The engine retrieves the active alert from its immutable `NoticeRegistry` (which indexes active notices by `EpisodeId` to track custody) and dispatches a `NoticeCommand.Resolve`.
- **`AutoRecovery`**: If the clinical rule is marked as reversible, it resolves the active notice. If the rule is non-reversible, it dispatches a confirmation notice to ensure staff follow-up.
- **`DwellPreWarning`**: It issues a low-priority notice to console or tablet channels.

#### Resolution Mapping

When closing an episode, Harbor translates underlying clinical conditions into staff-legible status messages:

- `STAFF_AND_SAFE` maps to **`Resolution.STAFF_PRESENT`**.
- Reversible `AUTO_RECOVERY` maps to **`Resolution.AUTO_RECOVERY`**.
- `MANUAL_CLOSE` maps to **`Resolution.MANUAL`**.

---

### Fatigue Management and the Notification Budget

The most critical operational feature of Vigia is its **Notification Budget**, which treats nurse attention as a finite resource.

1. **Shift Limits (`maxPerShift`)**: Harbor tracking is governed by a `NotificationBudget`. When a notification is dispatched, the current `HarborState` is updated using `withFatigueTrack`, which increments a counter for that specific alarm severity.
2. **Suppression**: If the dispatched counter meets or exceeds the configured `maxPerShift` limit for that severity, subsequent low-level alerts of that class are recorded in the `NoticeRegistry` (preserving the audit trail) but **no physical `Dispatch` command is issued**. The alert is actively muted to safeguard the team's attention span.
3. **Life Safety Override**: Crucially, **`Severity.CRITICAL`** signals are completely immune to budget suppression. Life safety always takes absolute precedence; critical emergencies instantly bypass fatigue filters to ensure immediate dispatch.

---

### Notice Channels and Severity Escalation

The engine's behaviors—such as where notifications are delivered and how quickly they escalate—are governed by an immutable, resident-specific **`HarborCalibration`**.

#### Multi-Channel Routing

The system routes notice commands across four distinct channels based on severity:

- **`CONSOLE`**: Silent, system-level logs and monitoring dashboards.
- **`PUSH`**: Mobile notifications sent directly to staff phones.
- **`TABLET`**: Dedicated devices mounted outside resident rooms or carried by hand.
- **`WARD_BOARD`**: Central displays placed at the main nursing station.

#### Escalation Timeouts

The calibration defines specific `escalationTimeouts` per severity level. If an alert is dispatched but its corresponding episode is not resolved or acknowledged within the designated timeout, the infrastructure (Vigia shell) escalates the notification, widening its distribution radius (e.g., routing it to a supervisor) until a nurse physically arrives.

If specific channels are not configured in a custom policy, the Harbor Adapter applies default projections compiled from global settings:

- **`INFO`**: Default to `CONSOLE` with a **30-minute** escalation timeout.
- **`WARNING`**: Default to `PUSH, TABLET` with a **5-minute** escalation timeout.
- **`CRITICAL`**: Default to `PUSH, TABLET, WARD_BOARD, CONSOLE` with **Immediate (0)** escalation.

---

### Integration in the Larger Platform Context

#### 1. In-Memory Composition (`ResidentRuntime`)

In production, the platform coordinates all four engines inside a single-threaded process called the **`ResidentRuntime`**. This orchestrator executes the pipeline sequentially: it passes observations to the Scene Engine, feeds the resulting physical facts directly to the Sentinel Engine, forwards the clinical signals to the Harbor Engine to generate notice pairings, and executes the Recorder Engine in parallel. Because execution occurs in-memory via direct function calls, Hisso completely eliminates serialization and network lag between the logical stages.

#### 2. Policy Propagation (The Politica Adapter ACL)

The Harbor Engine’s settings are entirely dynamic. When a care director updates a policy in the **Hub Service** (the platform's System of Record), the **Politica Engine** resolves the change against a three-layer precedence model (**Catalog \(\rightarrow\) Template \(\rightarrow\) Override**).

A dedicated **Harbor Adapter**—acting as an Anti-Corruption Layer (ACL)—projects the resolved policy rules to compile the immutable `HarborCalibration` used by the active runtime.

#### 3. Messaging Shell (`VigiaApplication`)

Outside the pure clinical core, the microservices communicate asynchronously via NATS JetStream. The **`VigiaApplication`** acts as the Spring Boot imperative shell surrounding the pure Harbor domain. It utilizes a driving NATS adapter (**`HarborNatsIngest`**) to subscribe to sentinel signals (`sentinel.signal.v1.>`), serializes events using a shared `NatsObjectMapper` configured with the `JavaTimeModule` for ISO-8601 compliance, and publishes dispatch instructions to the persistent bus under the versioned subject taxonomy `alarm.event.v1.<severity>`.

---

🔍 Would you like to review how the Harbor Adapter's default fallback logic is structurally validated under the BDD testing blueprints, or should we examine the NATS JetStream stream definitions for the `ALARM` stream?