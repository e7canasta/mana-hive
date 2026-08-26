Here is a comprehensive analysis of the Sentinel Engine.

---

## Sentinel Engine: Complete Domain Analysis

### 1. What is Sentinel's Domain?

Sentinel is the **clinical decision engine** that judges scene facts against policies and produces actionable signals. It is described as "the clinical judge" -- it takes distilled facts from the Scene Engine and, using compiled business rules for each resident, decides whether an episode should be opened, escalated, suppressed, or closed.

The domain model follows Vernon's "Policy Engine" pattern: rules are compiled by the Politica Engine (upstream), and Sentinel consults those rules to decide what action to take when a scene fact arrives.

**What Sentinel IS responsible for:**
- Episode lifecycle (open, track events, close)
- Severity assignment from rules
- Umbrella event classification (notifiable vs suppressed)
- Gap of assistance tracking
- Auto-recovery logic (reversible vs non-reversible)
- Fatigue budget enforcement

**What Sentinel is NOT responsible for:**
- Delivery (handled by Harbor/Harbor)
- Confirmation tracking (Harbor/Harbor)
- Escalation routing (Harbor/Harbor)
- NVR recording (external)

---

### 2. Core Domain Classes (`sentinel-domain/`)

#### `SentinelEvaluator` (Interface + Implementation)

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluator.kt`

The central domain service. Pure function: same input -> same output. Created with calibration (immutable for the evaluator's lifetime), state flows through as parameters (never stored). Implements `Engine` from the kernel.

```kotlin
public interface SentinelEvaluator : Engine {
    public fun evaluate(
        fact: SceneFact,
        episodes: EpisodeLedger,
        now: Instant,
    ): Explained<SentinelVerdict>
}
```

`SentinelVerdict` is the output: signals to emit + next episode state.

#### `SentinelEvaluatorImpl` (Internal)

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelEvaluatorImpl.kt`

The implementation (452 lines) handles three SceneFact types:
- `TransitionDetected` -- the primary trigger (person state change)
- `StaffPresenceDetected` -- marks staff presence, can close STAFF_AND_SAFE episodes
- `DwellExceeded` -- opens or emits umbrella events for prolonged states

Key decision logic:
1. **No open episode** -> look up rule for the trigger state -> open episode (if fatigue budget allows)
2. **Open episode exists** -> if new state is LYING (safe) -> handle safe state -> potentially close
3. **Open episode + higher severity** -> escalate (severe -> critical)
4. **Open episode + notifiable state** -> emit UmbrellaEvent (not a new episode)
5. **Staff presence** -> mark episode -> if closure conditions met, close
6. **Dwell exceeded** -> either opens new episode or emits umbrella event

#### `SentinelCalibration`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/SentinelCalibration.kt`

Compiled business rules for one resident. Analogous to SceneCalibration for SceneInterpreter. Created from `EffectiveRules` (Politica Engine output) + fatigue budget config. Contains:
- `rulesByTrigger: Map<StateKind, AlertRule>` -- rules indexed by trigger state for fast lookup
- `ruleIds: Set<RuleId>` -- all rule IDs for this resident
- `fatigue: FatigueBudget` -- fatigue budget configuration
- `fingerprint: String` -- rules fingerprint for reproducibility

#### DSL: `sentinelCalibration { ... }`

A type-safe Kotlin DSL for building calibration instances:

```kotlin
val calibration = sentinelCalibration {
    resident("maria")
    fatigue { maxPerShift = 5 }
    rule("r-fall") {
        trigger = StateKind.BED_EDGE
        severity = Severity.CRITICAL
        closureCondition = ClosureCondition.STAFF_AND_SAFE
        reversible = false
        requiresNvr = true
        requiresConfirmation = true
        confirmationWindow = Duration.ofSeconds(30)
        umbrellaEvents(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
    }
    rule("r-sit") {
        trigger = StateKind.SITTING_IN_BED
        severity = Severity.WARNING
        closureCondition = ClosureCondition.SAFE_ONLY
        reversible = true
    }
}
```

#### `EpisodeLedger`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-domain/src/main/kotlin/com/manahive/sentinel/EpisodeLedger.kt`

Decision state of the evaluator for ONE resident. Foldable from scene facts, passed in as parameter and returned as next value. One ledger per resident. Episodes follow the resident across bed changes.

Contains:
- `open: Map<BedId, Episode>` -- one open episode per bed at most
- `fatigue: FatigueBudget` -- current fatigue state

#### `Episode`

The core domain entity -- "the arc between leaving a safe state and returning to it stably." One episode per bed, tracking the full lifecycle. Vernon's Aggregate Root: the episode guards its own invariants.

Key fields:
- `id: EpisodeId` -- generated UUID
- `bed: BedId` -- the bed this episode is for
- `trigger: StateKind` -- what triggered the episode
- `severity: Severity` -- INFO, WARNING, or CRITICAL
- `closureCondition: ClosureCondition` -- SAFE_ONLY or STAFF_AND_SAFE
- `reversible: Boolean` -- whether resident can self-close
- `events: List<EpisodeEvent>` -- all events under this episode's umbrella
- `staffPresent: Boolean` -- whether staff has been present since opening
- `lastSafeState: Instant?` -- when resident last returned to safe state
- `alertedRules: Set<RuleId>` -- rules that have already fired (prevents duplicates)

Business methods: `canClose()`, `duration()`, `gapDuration()`, `withStaffPresent()`, `withSafeState()`, `withEvent()`, `escalate()`

#### `EpisodeEvent`

A single event under an episode's umbrella. Preserves the original fact's criticity even though the event is reported as "under umbrella."

#### `FatigueBudget`

Alarm fatigue as a design budget, not a staff complaint. `exceeded: Boolean` = `interruptionsThisShift >= maxPerShift`.

#### Configuration Layer

- `SentinelConfig` -- domain-specific config data class
- `SentinelConfigSource` -- interface (hexagonal port) for config loading
- `TomlSentinelConfigSource` -- TOML-based adapter (loads from `/etc/mana-hive/residents`)
- `HubSentinelConfigSource` -- placeholder for future Hub integration (not yet implemented)

---

### 3. Batch Layer (`sentinel-batch/`)

#### CLI Entry Point: `SentinelBatchApp.kt`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-batch/src/main/kotlin/com/manahive/sentinel/batch/SentinelBatchApp.kt`

Three commands:
- `sentinel-batch run <run.yaml>` -- execute simulation
- `sentinel-batch verify <run.yaml> <expected.out>` -- verify against expected (fail-fast)
- `sentinel-batch diff <expected.out> <actual.out>` -- compare two output files

#### `RunCommand`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-batch/src/main/kotlin/com/manahive/sentinel/batch/commands/RunCommand.kt`

Reads a YAML config file, parses events.dat, creates a SentinelCalibration, creates a SentinelEvaluator, then folds all events through the evaluator. Writes signals.jsonl, signals.out, and engine.log. Clock mode: event-time.

#### `VerifyCommand`

Same as Run but also checks emitted signals against an expected.out file. Stops on first mismatch (fail-fast).

#### `DiffCommand`

Compares two .out files line by line, shows MATCH/MISMATCH/MISSING/EXTRA.

#### `SentinelBatchProcessor`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-batch/src/main/kotlin/com/manahive/sentinel/batch/SentinelBatchProcessor.kt`

Shared batch processing logic. For each event:
1. Convert batch event to `SceneFact` (Transition, StaffPresent, DwellExceeded, DwellWarning, SignalLost, SignalRecovered)
2. Run evaluator
3. Write signals (JSONL + .out)
4. Write explanation to log
5. Console output

#### `BatchConfig` and `BatchConfigLoader`

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-batch/src/main/kotlin/com/manahive/sentinel/batch/config/BatchConfig.kt`

YAML-based configuration for batch runs. Rich Domain Model: the config knows how to create its own domain objects (SentinelCalibration) without leaking framework details.

#### Batch DSL: `sentinelBatchConfig { ... }`

Type-safe DSL for programmatic config:

```kotlin
val config = sentinelBatchConfig {
    resident {
        id = "maria"
        bed = "301"
        night = "night-1"
    }
    rule("r-fall") {
        trigger = StateKind.BED_EDGE
        severity = Severity.CRITICAL
        closure = ClosureCondition.STAFF_AND_SAFE
        reversible = false
        nvr = true
        umbrella(StateKind.STANDING, StateKind.ATTEMPTING_EXIT)
    }
    fatigue { maxPerShift = 5 }
    events { source = "events.dat" }
}
```

#### Events Input Format (`SceneFactEventParser`)

File: `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-batch/src/main/kotlin/com/manahive/sentinel/batch/events/SceneFactEventParser.kt`

Parses `events.dat` files. Format per line: `t=<offset> <FACT_TYPE> <details>`

Example events:
```
t=0s    TRANSITION from LYING to BED_EDGE
t=10s   STAFF_PRESENT staff nurse-1
t=40s   DWELL_EXCEEDED state STANDING threshold PT5M
t=50s   DWELL_WARNING state STANDING threshold PT4M
t=60s   SIGNAL_LOST monitor cam-1 lastHeartbeat 2026-08-22T02:00:00Z
t=70s   SIGNAL_RECOVERED monitor cam-1
```

#### Output Writers

- `SignalJsonlWriter` -- writes SentinelSignal instances as JSONL (one JSON object per line)
- `SignalOutWriter` -- writes human-readable `.out` format: `t=<offset>  <SIGNAL_TYPE> <details>`
- `LogWriter` -- writes engine logs as JSONL

---

### 4. DSL / Calibration

Sentinel has **two layers of DSL**:

1. **Calibration DSL** (`sentinelCalibration { ... }`) -- builds `SentinelCalibration` from rules, fatigue budget, and resident ID. Annotated with `@SentinelDsl` marker.

2. **Batch Config DSL** (`sentinelBatchConfig { ... }`) -- builds `BatchConfig` for batch simulations. Annotated with `@SentinelBatchDsl` marker.

The calibration is the core business rules compilation. Rules are keyed by `StateKind` (trigger state) and each rule specifies:
- `trigger: StateKind` -- what person state triggers this rule
- `severity: Severity` -- INFO, WARNING, or CRITICAL
- `closureCondition: ClosureCondition` -- SAFE_ONLY or STAFF_AND_SAFE
- `reversible: Boolean` -- whether the resident can self-close
- `requiresConfirmation: Boolean` -- whether staff must verify
- `requiresNvr: Boolean` -- whether NVR recording is needed
- `confirmationWindow: Duration?` -- time window for confirmation
- `umbrellaEvents: Set<StateKind>` -- events notifiable under this episode

Rules come from the Politica Engine via `EffectiveRules` (catalog + profile + overrides -> resolved rules per resident).

---

### 5. Events / Facts Produced

Sentinel produces `SentinelSignal` (sealed interface) published on `sentinel.signal.v1.<bed>`:

| Signal Type | When | Description |
|---|---|---|
| `EpisodeOpened` | New episode opens | Trigger + rule -> severity. Includes episode ID, rule ID, trigger, severity, reversible, requiresNvr, confirmationWindow |
| `UmbrellaEvent` | Event under open episode | Reported with original criticality, not as new episode. Includes state, originalSeverity |
| `AutoRecovery` | Resident returns to safe state without staff | If reversible: episode closes automatically. If non-reversible: staff must verify (confirmation required) |
| `EpisodeClosed` | Episode closes | Cause: STAFF_AND_SAFE or AUTO_RECOVERY. Includes gapDuration (time without staff) |
| `SuppressedWithRecord` | Alarm suppressed | Every suppression has a record for audit. Cause: STAFF_PRESENT, EPISODE_ALREADY_ALERTED, FATIGUE_BUDGET, NO_MATCHING_RULE |

Every signal cites the `rulesFingerprint` that governed it -- decisions are reproducible.

---

### 6. Relationship to Scene Engine

The Scene Engine and Sentinel Engine form a **pipeline**:

```
Observations -> Scene Engine -> SceneFacts -> Sentinel Engine -> SentinelSignals
```

**Scene Engine** (upstream):
- Takes raw observations (sensor data) and produces distilled `SceneFact` objects
- Manages the `DigitalTwin` (person state FSM with 13 states)
- Handles hysteresis, confidence thresholds, dwell tracking, signal health
- Does NOT decide whether anything deserves an alarm -- that is Sentinel's job

**Sentinel Engine** (downstream):
- Takes `SceneFact` objects from the Scene Stream (`scene.fact.v1.<bed>`)
- Judges them against compiled `AlertRule` policies (from Politica Engine)
- Produces `SentinelSignal` objects published to `sentinel.signal.v1.<bed>`

**Key architectural points:**
- Sentinel receives `SceneFact` via NATS JetStream subscription (`scene.fact.v1.>`)
- Sentinel maintains `EpisodeLedger` state per resident (persisted locally, rebuilt from scene stream on cold start)
- Sentinel consults `EffectiveRules` from the Politica Engine for policy resolution
- Sentinel's output goes to Harbor/Harbor for delivery (notifications, NVR, staff dispatch)
- The Scene Engine explicitly states it is "NOT responsible for: deciding whether anything deserves an alarm (sentinel)"

**Parallel architectural pattern:** Both engines follow the same structure:
- Pure domain layer (scene-domain / sentinel-domain)
- Batch tool (scene-batch / sentinel-batch) with run/verify/diff commands
- Service shell (scene-service / sentinel-service) with NATS ingest/egress
- Calibration DSL for configuring rules
- `Engine` interface from the kernel (same input -> same output, pure function)
- `Explained<T>` output (every decision carries its "why")

---

### Summary

Sentinel is the "clinical judge" in the mana-hive architecture. It consumes distilled scene facts from the Scene Engine, applies compiled per-resident alert rules (from Politica Engine), and manages a complete episode lifecycle: opening episodes on risky transitions, tracking umbrella events, handling staff presence, managing auto-recovery (reversible vs non-reversible), enforcing fatigue budgets, and producing auditable signals with full explanation trails. Its batch tooling enables deterministic simulation, verification, and diff testing of scenarios.