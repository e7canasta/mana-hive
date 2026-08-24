Here is a comprehensive summary of the mana-hive codebase architecture.
Explore the mana-hive codebase to understand the Sentinel engine, Hub, and other engines:

1. Find all .kt files under engines/sentinel/
2. Find all .kt files under engines/harbor/ or engines/harbor/ (alert delivery)
3. Find all .kt files under hub/
4. Find all .kt files under platform/messaging/ (NATS connectivity)
5. Find any DDD patterns, bounded context definitions, or aggregate roots
6. Look for any use case / actor / specification files

Return a comprehensive summary of the code structure, key classes, interfaces, and relationships between engines.

---

## Top-Level Structure

```
mana-hive/
  platform/           -- shared foundations (pure + lib)
    domain-kernel/    -- Engine, Decider, Explained, typed IDs
    contracts/        -- published language (SceneFact, SentinelSignal, AlarmEvent, etc.)
    messaging/        -- NATS subject taxonomy + JetStream topology
    infrastructure/   -- TOML config parsers, Hub-sourced config adapters
  hub/                -- system of record
    hub-domain/       -- PolicyResolver, EventStore, Ledger, PolicyCatalog
    hub-service/      -- Spring Boot: REST API, NATS ingest, policy catalog
  engines/
    scene-engine/     -- DigitalTwin, SceneInterpreter, ClockSweeper
    sentinel/         -- SentinelEvaluator, EpisodeLedger, FatigueBudget
    harbor/           -- HarborEngine, NoticeRouter, NoticeLifecycle (a.k.a. "harbor")
    politica-engine/  -- ResidentPolicy, PolicyResolver, SemanticBucket
    recorder/         -- RecorderEngine, RecordingLedger, EvidenceRecord
  simulator/          -- night-scenario DSL + scenario bank
```

---

## 1. Sentinel Engine (`../../../../engines/sentinel`)

**31 .kt files** across three modules (domain, service, batch).

### Domain Layer (`sentinel-domain/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluator.kt` | Interface extending `Engine`. One method: `evaluate(fact, episodes, now) -> Explained<SentinelVerdict>`. The "clinical judge" -- takes a `SceneFact` + compiled rules and produces signals. |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluatorImpl.kt` | 452-line pure implementation. Handles `TransitionDetected`, `StaffPresenceDetected`, `DwellExceeded`. Manages episode lifecycle: open, escalate, umbrella events, safe-state, auto-recovery, close. |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelCalibration.kt` | Immutable configuration per resident. Built from `EffectiveRules` + `FatigueBudget`. Includes a full type-safe DSL (`sentinelCalibration { ... }`). |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/EpisodeLedger.kt` | State container: `Map<BedId, Episode>` + `FatigueBudget`. The `Episode` class is explicitly documented as a Vernon Aggregate Root -- it guards its own invariants (closure conditions, staff presence, escalation). |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/config/` | `SentinelConfig`, `SentinelConfigSource` (port), `TomlSentinelConfigSource`, `HubSentinelConfigSource` -- adapter pattern for config loading. |

**Key concept -- Episode lifecycle:** Open (rule triggers) -> Umbrella events (events under the same episode) -> Auto-recovery or Staff-assisted close. `FatigueBudget` caps interruptions per shift. Every decision produces an `ExplanationStep` for audit.

### Service Layer (`sentinel-service/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-service/src/main/kotlin/com/manahive/sentinel/service/SentinelApplication.kt` | Spring Boot shell. Wires NATS ingest/egress. |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-service/src/main/kotlin/com/manahive/sentinel/service/nats/SentinelNatsIngest.kt` | Subscribes to `scene.fact.v1.>`. |
| `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-service/src/main/kotlin/com/manahive/sentinel/service/nats/SentinelNatsEgress.kt` | Publishes to `sentinel.signal.v1.<bed>`. |

### Batch Layer (`sentinel-batch/`)

CLI tools: `RunCommand`, `VerifyCommand`, `DiffCommand` for offline testing. `SignalJsonlWriter`, `SignalOutWriter`, `LogWriter` for output. `SceneFactEventParser` reads JSONL scene facts.

---

## 2. Harbor Engine (`../../../../engines/harbor`) -- a.k.a. "Harbor"

**20 .kt files** across three modules. No `engines/harbor/` directory exists -- the README mentions "harbor" but the actual code module is `harbor/`. The Spring Boot application class is still named `HarborApplication` (in package `com.manahive.harbor.service`), confirming harbor IS the harbor.

### Domain Layer (`harbor-domain/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/HarborEngine.kt` | Interface extending `Engine`. `evaluate(signal, registry, now) -> Explained<HarborVerdict>`. Receives `SentinelSignal`, produces `NoticeCommand`s. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/HarborEngineImpl.kt` | Pure implementation. Handles: `EpisodeOpened` -> creates Notice + dispatch; `EpisodeClosed` -> resolves notice; `AutoRecovery` -> auto-resolve or confirmation alert; `UmbrellaEvent` / `SuppressedWithRecord` -> no-op. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/Notice.kt` | Core domain concept ("Aviso"). Three severity levels: INFO (notice), WARNING (alert), CRITICAL (incident). Lifecycle: `CREATED -> DISPATCHED -> SEEN -> ACKNOWLEDGED -> RESOLVED` (or `ESCALATED`, `CANCELLED`). Channels: PUSH, TABLET, WARD_BOARD, CONSOLE. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/NoticeRouter.kt` | Domain service (`NoticeRouter : Engine`). Routes notices to staff based on coverage snapshot and severity. `DefaultNoticeRouter` implements escalation ladder: INFO -> console; WARNING -> push to ward nurse + tablet to shift nurse; CRITICAL -> all channels immediate. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/NoticeLifecycle.kt` | **Pure Decider** (`NoticeCommand -> LifecycleState -> NoticeEvent`). Explicitly documented as Fowler's Decider Pattern and Vernon's Aggregate Root. Two invariants: one notice per episode; RESOLVED is absorbing. Commands: Create, Dispatch, MarkSeen, Acknowledge, Escalate, Cancel, Resolve. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/HarborCalibrationDsl.kt` | Type-safe DSL: `harborCalibration { resident(...); notice { ... }; alert { ... }; incident { ... } }`. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-domain/src/main/kotlin/com/manahive/harbor/config/` | `HarborConfig`, `HarborConfigSource`, `TomlHarborConfigSource`, `HubHarborConfigSource`. |

### Service Layer (`harbor-service/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-service/src/main/kotlin/com/manahive/harbor/service/HarborApplication.kt` | Spring Boot shell. Wires `HarborCalibration` bean and `HarborEngine` bean. Subscribes to `sentinel.signal.v1.>`. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-service/src/main/kotlin/com/manahive/harbor/service/nats/HarborNatsIngest.kt` | Subscribes to sentinel signals. |
| `/home/visiona/workspace/mana-hive/engines/harbor/harbor-service/src/main/kotlin/com/manahive/harbor/service/nats/HarborNatsEgress.kt` | Publishes to `alarm.event.v1.<alert>`. |

---

## 3. Hub (`../../../../hub`)

**28 .kt files** across two modules.

### Domain Layer (`hub-domain/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/policy/PolicyResolver.kt` | Interface extending `Engine`. `resolve(resident, at, layers) -> Explained<EffectiveRules>`. Layered resolution: WatchLevel -> LevelTemplate -> ManualAdjustment -> TimeWindow. Tie-break: most protective layer wins. |
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/ledger/EventStore.kt` | Wraps `LedgerPort`. Operations: `store`, `storeMerged`, `replay`, `replayStream`. Produces `StoreResult` (Stored/Merged/Conflict/Duplicate). |
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/ledger/InMemoryLedger.kt` | In-memory `LedgerPort` implementation. |
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/ledger/WatermarkCatalog.kt`, `StreamCatalog.kt`, `InMemoryWatermarkStore.kt` | Stream management and watermark tracking. |
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/policy/InMemoryPolicyCatalog.kt`, `InMemorySemanticBucketStore.kt`, `InMemoryRawPolicyStore.kt` | In-memory stores for policy data. |
| `/home/visiona/workspace/mana-hive/hub/hub-domain/src/main/kotlin/com/manahive/hub/policy/WatchLevel.kt` | Watch level enum for policy resolution. |

### Service Layer (`hub-service/`)

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/HubApplication.kt` | Spring Boot application. |
| `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/api/PolicyController.kt`, `PolicyCatalogController.kt`, `RawPolicyController.kt`, `SemanticBucketController.kt`, `LedgerController.kt`, `HealthController.kt` | REST API controllers. |
| `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/api/Dto.kt`, `PolicyCatalogDto.kt`, `PolicyStoreDto.kt`, `Mappers.kt`, `CategoryParser.kt` | DTOs and mapping. |
| `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/nats/NatsIngestListener.kt`, `PolicyNatsEgress.kt` | NATS connectivity -- ingests all bus events, publishes effective rules and census. |
| `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/policy/PolicyService.kt` | Application service coordinating policy operations. |

---

## 4. Platform / NATS Messaging (`../../../../platform/messaging`)

**4 .kt files:**

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/NatsTopology.kt` | Declares 7 JetStream streams idempotently: PERCEPTION, SCENE, SENTINEL, ALARM, POLICY, RECORDER, EVIDENCE. Limits-based retention (7 days). Bus is transport, hub ledger is SoR. |
| `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/Subjects.kt` | Versioned subject taxonomy. All subjects are `domain.event.v1.<entity-id>`. Breaking changes = new subject version. |
| `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/NatsConfig.kt` | NATS connection configuration. |
| `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/NatsObjectMapper.kt` | JSON serialization for NATS messages. |

---

## 5. Platform / Contracts (`../../../../platform/contracts`)

The **published language** -- the Anti-Corruption Layer between all engines:

| Domain | Key Types |
|--------|-----------|
| **Perception** | `Observation`, `ObservationKind` |
| **Scene** | `SceneFact` (11 variants: NightOpened, TransitionDetected, DwellWarning, DwellExceeded, StaffPresenceDetected, SignalLost, etc.), `PersonState`, `SceneState`, `StateKind` |
| **Sentinel** | `SentinelSignal` (5 variants: EpisodeOpened, UmbrellaEvent, AutoRecovery, EpisodeClosed, SuppressedWithRecord), `ClosureCause`, `SuppressionCause` |
| **Policy** | `EffectiveRules`, `AlertRule`, `Severity` (INFO/WARNING/CRITICAL), `ClosureCondition`, `PolicyCalibration`, `PolicyPayload`, `PolicyEvent`, `RawPolicy`, `AlarmCatalog`, `AlarmProfile`, `PolicyOverride`, `PolicyCategory`, `SemanticBucketStore`, `RawPolicyStore` |
| **Alarm** | `AlarmEvent` |
| **Ledger** | `LedgerPort`, `StoredEvent`, `AppendResult`, `WatermarkPort` |
| **DAG** | `SceneDag`, `SceneNode`, `SceneEdge`, `DagStore`, `DagVersion` |
| **Census** | `CensusSnapshot` |

---

## 6. Platform / Domain Kernel (`../../../../platform/domain-kernel`)

**4 .kt files** -- the architectural backbone:

| File | Role |
|------|------|
| `/home/visiona/workspace/mana-hive/platform/domain-kernel/src/main/kotlin/com/manahive/kernel/Engine.kt` | Core interface. Every engine is a pure domain service. `Explained<T>` wraps output with explanation steps and discards. `DiscardCause` enum: ILLEGAL_TRANSITION, CONFIDENCE_TOO_LOW, HYSTERESIS_NOT_MET, DUPLICATE, NO_OCCUPANT, STAFF_PRESENT, EPISODE_ALREADY_ALERTED, FATIGUE_BUDGET_EXCEEDED. |
| `/home/visiona/workspace/mana-hive/platform/domain-kernel/src/main/kotlin/com/manahive/kernel/Decider.kt` | **Event-sourcing aggregate pattern**: `Decider<C, S, E>` with `decide(command, state) -> Decision<E>` and `evolve(state, event) -> S`. Both pure. `Decision` is sealed: `Accepted(events)` or `Rejected(reason)`. |
| `/home/visiona/workspace/mana-hive/platform/domain-kernel/src/main/kotlin/com/manahive/kernel/DecisionRecord.kt` | Durable trace of one engine invocation. The answer to "why did the alarm (not) ring at 03:12?" Triple of (inputs fingerprint, rules fingerprint, engine version) makes every decision machine-reproducible. |
| `/home/visiona/workspace/mana-hive/platform/domain-kernel/src/main/kotlin/com/manahive/kernel/Ids.kt` | Strongly-typed value class IDs: `BedId`, `ResidentId`, `MonitorId`, `StaffId`, `AlertId`, `RuleId`, `EpisodeId`, `NoticeId`, `NightId`, `EventRef`, `DagId`, `NodeId`. Zero runtime cost, compiler-enforced. |

---

## 7. Other Engines

### Scene Engine (`../../../../engines/scene-engine`)

26 .kt files in domain layer. Key types:
- **`SceneInterpreter`** (`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/interpreter/SceneInterpreter.kt`) -- `Engine` interface. `interpret(twin, observation, now) -> Explained<SceneVerdict>`.
- **`DigitalTwin`** (`/home/visiona/workspace/mana-hive/engines/scene-engine/scene-domain/src/main/kotlin/com/manahive/scene/core/DigitalTwin.kt`) -- Immutable value object representing one bed's state. `evolve(fact)` folds scene facts. Explicitly documented as an event-sourced aggregate that folds its own events.
- **`TransitionTable`** -- FSM transition legality (total table).
- **`ClockSweeper`** -- Dwell timer (warning/exceeded thresholds).
- **`SceneCalibration`** -- Compiled rules from Politica's `PolicyCalibration` output.

### Politica Engine (`../../../../engines/politica-engine`)

7 .kt files in domain layer. Key types:
- **`ResidentPolicy`** (`/home/visiona/workspace/mana-hive/engines/politica-engine/politica-domain/src/main/kotlin/com/manahive/politica/ResidentPolicy.kt`) -- **Explicitly documented as Vernon's Aggregate Root**. Groups all `SemanticBucket`s for one resident. Guards invariant: all buckets must belong to same resident.
- **`SemanticBucket`** -- Unit of policy storage. Resident + Category + Version + Payload.
- **`PolicyResolver`** (`/home/visiona/workspace/mana-hive/engines/politica-engine/politica-domain/src/main/kotlin/com/manahive/politica/PolicyResolver.kt`) -- Resolves catalog preset -> resident template -> manual override -> `PolicyCalibration`.
- **`PolicyBucketMapper`**, `PolicyChangeProcessor`, `CalibrationProvider`.

### Recorder Engine (`../../../../engines/recorder`)

12 .kt files. Key types:
- **`RecorderEngine`** -- `Engine` interface. `evaluate(trigger, ledger, now) -> Explained<RecordingVerdict>`. Matches triggers against recording rules, produces NVR commands + evidence records.
- **`RecordingCalibration`**, `RecordingLedger`, `EvidenceRecord`, `RecordingEvent`.

---

## 8. DDD Patterns Found

### Aggregate Roots (explicitly documented)
1. **`Episode`** (sentinel) -- `EpisodeLedger.kt` line 56: *"Vernon's Aggregate Root: the episode guards its own invariants."*
2. **`ResidentPolicy`** (politica) -- `ResidentPolicy.kt` line 11: *"Vernon: 'Aggregate Root' -- the only entry point for modifications."*
3. **`NoticeLifecycle`** (harbor) -- `NoticeLifecycle.kt` line 18: *"Vernon: 'Aggregate Root' -- the lifecycle guards its own invariants."*
4. **`DigitalTwin`** (scene) -- `DigitalTwin.kt` line 36: *"Vernon: 'In event-sourced aggregates, the entity folds its own events.'"*

### Decider Pattern (Event Sourcing)
- **`Decider<C, S, E>`** interface in `../../../../platform/domain-kernel` -- the universal event-sourcing shape.
- **`NoticeLifecycle`** implements the Decider pattern: `NoticeCommand -> LifecycleState -> NoticeEvent`.
- Every engine follows the pattern: `decide(command, state) -> events` + `evolve(state, event) -> state`.

### Aggregate as State Container (non-Event-Sourced)
- **`EpisodeLedger`** -- state flows through as parameter, returned as next value. Shell persists, engine never touches storage.
- **`NoticeRegistry`** -- immutable value class wrapping `Map<EpisodeId, Notice>`.
- **`RecordingLedger`** -- same pattern as EpisodeLedger.

### Published Language / ACL
- **`../../../../platform/contracts`** is the Anti-Corruption Layer. Engines only know contracts, never each other's internals.
- `PolicyCalibration` is explicitly documented: *"Vernon's ACL: this is the public interface of Politica engine."*

### Bounded Contexts (implicit, not formal `BoundedContext` classes)
Each engine is a bounded context with:
- Its own domain layer (pure, no Spring/IO).
- Its own service layer (Spring Boot shell).
- Its own batch layer (CLI tools).
- Communication only through NATS subjects (the published language).

### Type-Safe IDs
- `BedId`, `ResidentId`, `EpisodeId`, `NoticeId`, `RuleId`, etc. -- all `@JvmInline value class` with non-blank validation.

### Explained Decisions
- Every engine output is wrapped in `Explained<T>` with `List<ExplanationStep>` and `List<Discard>`. Every decision is auditable and reproducible via fingerprints.

---

## 9. Data Flow Summary

```
ia-cell (sensors)
    |
    | perception.observation.v1.<bed>
    v
scene-engine (DigitalTwin + SceneInterpreter)
    |
    | scene.fact.v1.<bed>  (TransitionDetected, DwellExceeded, StaffPresenceDetected, ...)
    v
sentinel (SentinelEvaluator + EpisodeLedger)
    |
    | sentinel.signal.v1.<bed>  (EpisodeOpened, UmbrellaEvent, AutoRecovery, EpisodeClosed, SuppressedWithRecord)
    v
harbor/harbor (HarborEngine + NoticeRouter + NoticeLifecycle)
    |
    | alarm.event.v1.<alert>
    v
delivery devices (PUSH, TABLET, WARD_BOARD, CONSOLE)

hub (system of record)
    |--- ingests ALL bus events
    |--- stores in ledger (EventStore + LedgerPort)
    |--- publishes: effective-rules, census, coverage
    |--- answers: "why did the alarm (not) ring at 03:12?"
```

**Cross-cutting:**
- **Politica Engine** resolves `AlarmCatalog + AlarmProfile -> PolicyCalibration` for Scene Engine, and `EffectiveRules` for Sentinel.
- **Recorder Engine** receives sentinel signals + scene facts and produces NVR recording commands + evidence records.
- All engines are **pure domain services** -- same input yields same output regardless of clock or side effects. `Instant` is always injected.