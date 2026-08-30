Now I have a comprehensive picture. Here is the full report:

---

## Event Emission/Publishing in mana-hive

### 1. Subject Taxonomy (the "where")

**File:** `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/Subjects.kt`

This is the central registry of all NATS subjects. Every subject is versioned (`v1`) so breaking changes create new subjects rather than mutating old ones.

| Method / Constant | Subject Pattern | Purpose |
|---|---|---|
| `perceptionObservation(bed)` | `perception.observation.v1.{bed}` | Inbound from edge |
| `sceneEvent(bed)` / `SCENE_WILDCARD` | `scene.fact.v1.{bed}` / `scene.fact.v1.>` | Scene facts emitted by runtime |
| `sentinelSignal(bed)` / `SENTINEL_WILDCARD` | `sentinel.signal.v1.{bed}` / `sentinel.signal.v1.>` | Episode signals |
| `alarmEvent(alert)` / `ALARM_WILDCARD` | `alarm.event.v1.{alert}` / `alarm.event.v1.>` | Alarm notifications |
| `recordingCommand(bed)` / `RECORDER_WILDCARD` | `recorder.command.v1.{bed}` / `recorder.command.v1.>` | Recording orders |
| `evidenceRecord(bed)` / `EVIDENCE_WILDCARD` | `evidence.record.v1.{bed}` / `evidence.record.v1.>` | Archived evidence |
| `policyChangeDetected()` | `hub.policy.change.v1` | Policy changes from hub |
| `effectiveRules(resident)` | `hub.policy.effective-rules.v1.{resident}` | Effective rules to sentinel |
| `residentProfile()` | `hub.policy.profile.v1` | Full resident profile |
| `CENSUS_SNAPSHOT` | `hub.census.snapshot.v1` | Bed-to-resident census |

---

### 2. NATS Egress Components (the "who publishes")

#### A. NightWatchService -- The Central Egress (the real publisher)

**File:** `/home/visiona/workspace/mana-hive/engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/NightWatchService.kt`

This is the **primary event emitter** for the entire pipeline. It is the monolithic egress that publishes ALL output events. It replaces what would otherwise be separate Sentinel, Recorder, and Harbor egress classes.

**Key publishing method (lines 211-244):**
```kotlin
private fun publish(bed: BedId, out: Outbound) {
    // Scene facts always go out, even with no episode
    for (fact in out.sceneFacts) {
        emit(Subjects.sceneEvent(bed), "SceneEvent", fact.at, SceneEventSerializer.toJson(fact))
    }
    // Signal published BEFORE alarm so alarm can cite the signal's real sequence
    val seqOf = mutableMapOf<SentinelSignal, EventRef>()
    for (signal in out.signals) {
        val ref = emit(
            Subjects.sentinelSignal(bed), "SentinelSignal", signal.at,
            SentinelSignalSerializer.toJson(signal),
        )
        if (ref != null) seqOf[signal] = ref
    }
    // Alarm events for dispatch commands
    for ((signal, command) in out.harborCommands.map { it.signal to it.command }) {
        val event = toAlarmEvent(signal, command, seqOf[signal]) ?: continue
        emit(
            Subjects.alarmEvent(event.alert), "AlarmEvent", event.at,
            mapper.writeValueAsString(event),
        )
    }
    // Recording commands
    for (command in out.recorderCommands) {
        emit(
            Subjects.recordingCommand(bed), "RecordingCommand", Instant.now(),
            mapper.writeValueAsString(command),
        )
    }
}
```

**The low-level `emit` method (lines 274-288):**
```kotlin
private fun emit(subject: String, type: String, at: Instant, payload: String): EventRef? = try {
    val envelope = EventEnvelope(
        eventId = java.util.UUID.randomUUID().toString(),
        type = type,
        version = 1,
        occurredAt = at,
        source = "night-watch-runtime",
        payloadJson = payload,
    )
    val ack = jetStream.publish(subject, mapper.writeValueAsBytes(envelope))
    EventRef(stream = ack.stream, seq = ack.seqno)
} catch (e: Exception) {
    log.error("No se pudo publicar {} en {}: {}", type, subject, e.message)
    null
}
```

The `EventRef` return value is critical -- it captures the JetStream `stream` and `seqno` so that downstream events (like alarms) can cite their origin in the event chain.

---

#### B. SceneNatsEgress

**File:** `/home/visiona/workspace/mana-hive/engines/scene-engine/scene-service/src/main/kotlin/com/manahive/scene/service/nats/SceneNatsEgress.kt`

Publishes `SceneFact` events to `scene.fact.v1.{bed}` via JetStream. Wraps facts in `EventEnvelope` with `source = "scene-engine"`.

```kotlin
public fun publishFact(fact: SceneFact) {
    val subject = Subjects.sceneFact(fact.bed)
    val envelope = EventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = fact::class.simpleName ?: "SceneFact",
        version = 1,
        occurredAt = fact.at,
        source = "scene-engine",
        payloadJson = SceneEventSerializer.toJson(fact),
    )
    val data = mapper.writeValueAsBytes(envelope)
    jetStream.publish(subject, data)
}
```

---

#### C. PolicyNatsEgress

**File:** `/home/visiona/workspace/mana-hive/hub/hub-service/src/main/kotlin/com/manahive/hub/nats/PolicyNatsEgress.kt`

Implements `PolicyEventPublisher`. Publishes two kinds of events:

1. **PolicyChangeDetected** to `hub.policy.change.v1` -- consumed by Politica Engine
2. **EffectiveRules** to `hub.policy.effective-rules.v1.{resident}` -- consumed by Sentinel

```kotlin
// Policy change
override fun publishPolicyChange(residentId: ResidentId, snapshot: AlarmProfile, at: Instant) {
    val subject = Subjects.policyChangeDetected()
    val envelope = EventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = "PolicyChangeDetected",
        version = 1, occurredAt = at, source = "hub",
        payloadJson = mapper.writeValueAsString(change),
    )
    jetStream.publish(subject, mapper.writeValueAsBytes(envelope))
}

// Effective rules
public fun publishEffectiveRules(residentId: ResidentId, rules: EffectiveRules, at: Instant) {
    val subject = Subjects.effectiveRules(residentId)
    val envelope = EventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = "EffectiveRules",
        version = 1, occurredAt = at, source = "hub",
        payloadJson = mapper.writeValueAsString(rules),
    )
    jetStream.publish(subject, mapper.writeValueAsBytes(envelope))
}
```

---

### 3. Sentinel/Recorder/Harbor Egress -- Referenced but Not Yet Implemented

The Ingest components in Sentinel, Recorder, and Harbor all **reference** dedicated egress classes that do not yet exist as standalone files:

- `SentinelNatsEgress` -- referenced in `/home/visiona/workspace/mana-hive/engines/sentinel/sentinel-service/src/main/kotlin/com/manahive/sentinel/service/nats/SentinelNatsIngest.kt` (line 35)
- `RecorderNatsEgress` -- referenced in `/home/visiona/workspace/mana-hive/engines/recorder/recorder-service/src/main/kotlin/com/manahive/recorder/service/nats/RecorderNatsIngest.kt` (line 35)
- `HarborNatsEgress` -- referenced in `/home/visiona/workspace/mana-hive/engines/harbor/harbor-service/src/main/kotlin/com/manahive/harbor/service/nats/HarborNatsIngest.kt` (line 32)

These classes are injected via constructor but have no implementation files on disk. The NightWatchService currently handles all event emission centrally instead.

---

### 4. Supporting Infrastructure

**EventEnvelope** -- `/home/visiona/workspace/mana-hive/platform/contracts/src/main/kotlin/com/manahive/contracts/EventEnvelope.kt`
```kotlin
public data class EventEnvelope(
    public val eventId: String,    // idempotency key, doubles as Nats-Msg-Id
    public val type: String,       // discriminator for deserialization
    public val version: Int,
    public val occurredAt: Instant,
    public val source: String,     // which service emitted it
    public val payloadJson: String, // domain event serialized separately
)
```

**NatsTopology** -- `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/NatsTopology.kt` -- declares all 7 JetStream streams (PERCEPTION, SCENE, SENTINEL, ALARM, POLICY, RECORDER, EVIDENCE) with 7-day limits-based retention.

**BusContract** -- `/home/visiona/workspace/mana-hive/platform/messaging/src/main/kotlin/com/manahive/messaging/BusContract.kt` -- documents the full bus contract: what is received, what is emitted, and the ordering guarantees (scene facts always, signals before alarms, deterministic AlertIds).

**BusEvents / BusConnector** -- the connection lifecycle layer that allows services to start before NATS and reconnect gracefully.

---

### 5. End-to-End Blueprint

**File:** `/home/visiona/workspace/mana-hive/blueprints/nats-e2e/src/main/kotlin/natse2e/Main.kt`

This standalone test validates the full bus contract against a real NATS server. It demonstrates the publishing pattern for all event types and verifies that deserialized events arrive typed and correct on the other side.

---

### Summary: Event Flow Diagram

```
Edge Server
  ── perception.observation.v1.{bed} ──────────────────────▶ NightWatchService
                                                              │
                                                              ▼
                                                         scene.fact.v1.{bed}
                                                         sentinel.signal.v1.{bed}
                                                         alarm.event.v1.{alert}
                                                         recorder.command.v1.{bed}

Hub (registration)
  ── hub.policy.change.v1 ──────────────────────────────────▶ PolicyNatsEgress
  ── hub.policy.profile.v1 ─────────────────────────────────▶ NightWatchService
```

The NightWatchService is the single centralized egress for the observation-to-alarm pipeline, while PolicyNatsEgress handles hub-originated policy events. The standalone Sentinel/Recorder/Harbor egress classes are referenced in constructor injections but not yet implemented as separate files.