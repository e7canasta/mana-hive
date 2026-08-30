I now have a comprehensive picture. Let me compile the complete analysis.

---

## ANALYSIS: hive-bridge and mana-hub Integration Compatibility with mana-hive

### 1. WHAT THE BRIDGE CURRENTLY DOES

There are **two distinct components** in mana-hub related to the bridge:

#### A. `hive-bridge` (library module)
**Location:** `/home/visiona/workspace/mana-hub/hive-bridge/`

This is a **shared library** (not a deployable application). It contains a copy of the NATS infrastructure from mana-hive's `platform/messaging`:
- **EventEnvelope** (identical to mana-hive's `platform/contracts`)
- **Subjects** (identical subject taxonomy, but uses `String` IDs instead of kernel value types)
- **NatsClientConfiguration**, **NatsConfig**, **NatsTopology** (connection factory, 7 JetStream streams)
- **BusConnector**, **BusEvents** (connection lifecycle management)
- **NatsObjectMapper** (Jackson with KotlinModule + JavaTimeModule)
- **HubPolicyPayload** -- typed policy change DTO with `HubRiskLevel`, `HubMobilityAid`, `HubPolicyMode` enums

Dependencies: `jnats:2.21.4`, Jackson, Spring (compileOnly). No dependency on mana-hive kernel.

#### B. `event-bridge` (deployable Spring Boot app, port 8090)
**Location:** `/home/visiona/workspace/mana-hub/event-bridge/`

A **Translation Layer** application:
- **NatsIngestService**: Subscribes to 4 NATS JetStream durable consumers (push mode), polls messages in daemon threads, and forwards them as `POST /webhooks/events` to the main mana-hub app (default `http://localhost:8080`).
- **WebhookController**: Exposes `POST /webhooks/policy-change` that receives policy change payloads from mana-hub and publishes them to NATS subject `hub.policy.change.v1`.
- **BridgeController**: Health check only.
- **BridgeConfig**: Creates a blocking `Nats.connect(url)` connection and a Jackson ObjectMapper.

The bridge stores nothing -- it is purely a push-through translator.

---

### 2. SUBJECTS SUBSCRIBED/PUBLISHED

#### event-bridge subscribes to (hive -> hub direction):
| Subject | Durable Name | Handler |
|---------|-------------|---------|
| `perception.observation.v1.>` | bridge-perception | `pollLoop` -> `forwardToHub` |
| `scene.fact.v1.>` | bridge-scene | `pollLoop` -> `forwardToHub` |
| `sentinel.signal.v1.>` | bridge-sentinel | `pollLoop` -> `forwardToHub` |
| `alarm.event.v1.>` | bridge-alarm | `pollLoop` -> `forwardToHub` |

#### event-bridge publishes to (hub -> hive direction):
| Subject | Source |
|---------|--------|
| `hub.policy.change.v1` | `WebhookController.onPolicyChange()` |

#### Missing subscriptions/publications:
- **NOT subscribed**: `recorder.command.v1.>`, `evidence.record.v1.>` (documented but not implemented)
- **NOT published by hub**: `hub.policy.effective-rules.v1.<resident>`, `hub.policy.profile.v1`, `hub.census.snapshot.v1`

#### hive-bridge library declares 7 streams (via `NatsTopology.ensureAll()`):
PERCEPTION, SCENE, SENTINEL, ALARM, POLICY, RECORDER, EVIDENCE -- all with 7-day retention, file storage, 10-minute duplicate window.

---

### 3. DATA MODELS: WHAT BRIDGE USES vs WHAT HIVE PUBLISHES vs WHAT HUB EXPECTS

#### 3a. Perception (Observation)

| Aspect | mana-hive publishes | event-bridge forwards | mana-hub expects |
|--------|-------------------|---------------------|-----------------|
| Type | `Observation` (data class with kernel types) | Raw `EventEnvelope.payloadJson` string | `IngestEventRequest` (flat DTO) |
| Bed ID | `BedId` (value class) | String in JSON | `bedId: String?` |
| Monitor | `MonitorId` (value class) | String in JSON | `monitorKey: String` |
| Kind | `ObservationKind` enum (25 values: IN_BED, SITTING_IN_BED, ATTEMPTING_EXIT, BED_EDGE, STANDING, IN_BATHROOM, etc.) | String in JSON | `kind: String` |
| Confidence | `confidence: Double` (0.0..1.0) | String in JSON | **MISSING** |
| Timestamp | `observedAt: Instant` | String in JSON | `occurredAt: Instant` |
| Extra hub fields | N/A | N/A | `roomState`, `state`, `sleeping`, `payloadJson` |

**Gap**: The bridge does no field mapping. It forwards the raw envelope to `/webhooks/events`, but the correct endpoint is `POST /internal/v1/events`. The `confidence` field from hive is lost because hub's `IngestEventRequest` has no `confidence` field. Hub uses `monitorKey: String` vs hive's typed `MonitorId`.

#### 3b. Scene Events

| Aspect | mana-hive publishes | event-bridge forwards | mana-hub expects |
|--------|-------------------|---------------------|-----------------|
| Type | `SceneEvent` sealed interface (14 subtypes) | Raw JSON | `IngestSceneEventRequest` (flat DTO) |
| Subtypes | `NightOpened`, `TransitionDetected`, `DwellWarning`, `DwellExceeded`, `SceneStateChanged`, `SceneDwellWarning`, `SceneDwellExceeded`, `StaffPresenceDetected`, `StaffLeftDetected`, `SignalLost`, `SignalRecovered`, `ComeBackWarning`, `ComeBackExceeded`, `NightClosed` | String `"type"` field | Single flat `eventType: String` |
| Person state | `PersonState` sealed interface (13 states with `StateKind` enum) | String in JSON | `fromState: String?`, `toState: String?` |
| Night session | `night: NightId` | String in JSON | **MISSING entirely** |
| NightSummary | `NightSummary(transitions, minutesUnknown, episodes)` | N/A | **MISSING** |
| Scene field | `field: String` (e.g., `bed.left`) | N/A | **MISSING** |
| Duration | `threshold: Duration`, `since: Instant` | N/A | **MISSING** |
| Staff info | `StaffPresenceDetected(staff: StaffId?)` | N/A | **MISSING** |

**Gap**: CRITICAL. Hive publishes 14 distinct subtypes with rich typed fields. Hub expects a single flat DTO. The bridge does not deserialize or route by subtype. The hub's `IngestSceneEventRequest` only handles `TransitionDetected` and `DwellExceeded` semantics (fromState/toState/triggerType), and all other subtypes would be lost or misinterpreted. `NightId` is entirely absent from hub's model.

#### 3c. Sentinel Signals

| Aspect | mana-hive publishes | event-bridge forwards | mana-hub expects |
|--------|-------------------|---------------------|-----------------|
| Type | `SentinelSignal` sealed interface (7 subtypes) | Raw JSON | `CreateEpisodeRequest` + `UpdateEpisodeRequest` |
| Subtypes | `EpisodeOpened`, `EpisodeClosed`, `AutoRecovery`, `UmbrellaEvent`, `SuppressedWithRecord`, `DwellPreWarning`, `ComeBackPreWarning` | String `"type"` field | Two endpoints: `POST /api/v1/episodes` and `PATCH /api/v1/episodes/{id}` |
| Episode ID | `episode: EpisodeId` (hive-generated) | N/A | Hub generates its own `id` |
| Rule ID | `rule: RuleId` | N/A | **MISSING** (Episode has no ruleId field in create) |
| Severity | `Severity` (INFO, WARNING, **HIGH**, CRITICAL) | N/A | `EpisodeSeverity` (INFO, WARNING, CRITICAL, **EMERGENCY**) |
| Rules fingerprint | `rulesFingerprint: String` | N/A | **MISSING** |
| Trigger | `trigger: StateKind?`, `field: String?` | N/A | **MISSING** |
| Reversible | `reversible: Boolean` | N/A | **MISSING** |
| Requires NVR | `requiresNvr: Boolean` | N/A | **MISSING** |
| Confirmation window | `confirmationWindow: Duration?` | N/A | **MISSING** |
| Closure cause | `cause: ClosureCause` | N/A | **MISSING** |
| Gap duration | `gapDuration: Duration?` | N/A | **MISSING** |
| Suppression | `SuppressedWithRecord(rule, cause, evidence)` | N/A | **No handling at all** |
| Pre-warnings | `DwellPreWarning`, `ComeBackPreWarning` | N/A | **No handling at all** |

**Gap**: CRITICAL. The 7 subtypes map to fundamentally different hub operations. `EpisodeOpened` -> `POST /api/v1/episodes`, `EpisodeClosed` -> `PATCH`, `UmbrellaEvent`/`SuppressedWithRecord`/`DwellPreWarning`/`ComeBackPreWarning` -> no hub endpoint. Severity enums **diverge**: hive has `HIGH` (rank 2), hub has `EMERGENCY` (rank 3). Episode identity is split: hive generates the episode ID, hub generates its own.

#### 3d. Alarm Events

| Aspect | mana-hive publishes | event-bridge forwards | mana-hub expects |
|--------|-------------------|---------------------|-----------------|
| Type | `AlarmEvent` sealed interface (8 subtypes) | Raw JSON | `IngestNotificationRequest` (flat DTO) |
| Subtypes | `AlertRaised`, `DeliveryOrdered`, `Delivered`, `Seen`, `Acknowledged`, `Escalated`, `Silenced`, `ResolvedByPresence`, `ResolvedManually` | String `"type"` field | Single flat `eventType: String` |
| Alert key | `AlertKey(bed, rule, episode)` | N/A | **MISSING** |
| Channel | `Channel` (PUSH, TABLET, WARD_BOARD, CONSOLE) | N/A | **MISSING** |
| Recipients | `recipients: List<StaffId>` | N/A | **MISSING** |
| Step | `step: Int` (escalation level) | N/A | **MISSING** |
| Escalation | `EscalationCause` | N/A | **MISSING** |

**Gap**: CRITICAL. 8 alarm subtypes collapsed to a single flat DTO. Multi-step escalation, delivery tracking, and staff acknowledgment are all lost.

#### 3e. Policy Change (hub -> hive)

| Aspect | hive expects | hub publishes (WebhookController) | Gap |
|--------|-------------|----------------------------------|-----|
| Event type | `PolicyChangeDetected(residentId, at, snapshot: AlarmProfile)` | `PolicyChangeDetected` envelope with `HubPolicyChange` payload | Partial match |
| AlarmProfile | Typed: `residentId`, `riskLevel`, `mobilityAid`, `autopilot`, `mode`, `templateId?`, `overrides: Map<RuleId, PolicyOverride>`, `catalogVersion`, `validFrom` | `HubPolicyChange`: `residentId`, `at`, `riskLevel`, `templateId?`, `mobilityAid?`, `autopilot`, `mode?`, `overridesJson: String`, `fingerprint`, `source` | See below |
| Overrides | Typed sealed interface: `HysteresisOverride(ruleId, key: TransitionKey, value: Duration)`, `DwellOverride(ruleId, state: StateKind, value: DwellThreshold)`, `ComeBackOverride(ruleId, baseline: StateKind, value: DwellThreshold, severity?, closureCondition?)` | `overridesJson: String` (untyped JSON blob) | **CRITICAL**: hive cannot parse untyped JSON into typed overrides |
| Catalog version | `catalogVersion: CatalogVersion` (value class) | **MISSING** | Hive cannot verify catalog compatibility |
| Valid from | `validFrom: Instant` | Not present as `AlarmProfile.validFrom` (present as envelope `at`) | **MISSING**: hive's AlarmProfile needs validFrom |
| Fingerprint | N/A in AlarmProfile itself | `fingerprint: String` in HubPolicyChange | Present but differently positioned |

**Gap**: CRITICAL. The bridge payload uses untyped `overridesJson` while hive expects typed `Map<RuleId, PolicyOverride>`. Hive's Politica Engine cannot safely deserialize arbitrary JSON into `TransitionKey`, `StateKind`, `DwellThreshold` without the proper typed contract. `catalogVersion` is missing, which means hive cannot detect catalog drift.

#### 3f. ResidentProfile (NEW model in hive)

| Aspect | hive publishes | hub has | Gap |
|--------|---------------|---------|-----|
| Model | `ResidentProfile` (rich document: subjects, aspects, transitions, rules, windows, provenance) | `AlarmProfileVersion` (flat: riskLevel, mobilityAid, autopilot, mode, templateId) | **ENTIRE MODEL ABSENT in hub** |
| Wire format | `ResidentProfileDto` with String durations, flat structures | `AlarmProfileResponse` with nested DTOs | Different structure |
| Subject/Aspect model | `Subject(kind: AspectKind, aspects: Map<String, Aspect>)` | N/A | **MISSING** |
| Profile rules | `ProfileRule(window, warningAfter, alertAfter, severity, closure, notify, record)` | N/A | **MISSING** |
| Windows | `PolicyWindow(id, from, to)` | N/A | **MISSING** |
| Provenance | `Provenance(template, templateVersion, authoredBy, authoredAt, reason)` | `updatedBy: String?` | Partial |
| ProfileEndpoints | `activeProfiles()`, `current(residentId)`, `versions(residentId)`, `asOf(residentId, at)`, `publish(residentId, profile)` | `AlarmProfileController` (different API shape) | **Interface not implemented** |
| NATS subject | `hub.policy.profile.v1` | **MISSING** | No subject in bridge Subjects.kt |

**Gap**: The entire `ResidentProfile` model (the new document-based profile with subjects/aspects/transitions/states) does not exist in mana-hub. Hub still uses the old `AlarmProfileVersion` model. The `ProfileEndpoints` interface from `profile-api` is not implemented.

---

### 4. SPECIFIC COMPATIBILITY GAPS (Summary)

**CRITICAL GAPS:**

1. **Bridge routing is broken**: `NatsIngestService.forwardToHub()` sends ALL events to `POST /webhooks/events` regardless of subject type. The correct endpoints are `/internal/v1/events` (perception), `/internal/v1/scene-events` (scene), `/api/v1/episodes` (sentinel), `/internal/v1/notifications` (alarm). There is no routing logic.

2. **SceneEvent sealed interface not supported**: Hub's `IngestSceneEventRequest` is a flat DTO that can only represent `TransitionDetected` and `DwellExceeded` semantics. 12 of 14 hive subtypes (`NightOpened`, `DwellWarning`, `SceneStateChanged`, `SceneDwellWarning`, `SceneDwellExceeded`, `StaffPresenceDetected`, `StaffLeftDetected`, `SignalLost`, `SignalRecovered`, `ComeBackWarning`, `ComeBackExceeded`, `NightClosed`) have no representation.

3. **SentinelSignal type system lost**: 7 subtypes collapsed to `eventType: String`. Hub has no handling for `UmbrellaEvent`, `AutoRecovery`, `SuppressedWithRecord`, `DwellPreWarning`, `ComeBackPreWarning`. Episode ID ownership is split (hive generates, hub generates).

4. **Severity enum divergence**: hive = `{INFO, WARNING, HIGH, CRITICAL}`, hub = `{INFO, WARNING, CRITICAL, EMERGENCY}`. The mapping `HIGH -> ?` and `? -> EMERGENCY` is undefined.

5. **Policy overrides untyped**: `HubPolicyChange.overridesJson: String` vs hive's typed `Map<RuleId, PolicyOverride>` with `TransitionKey`, `StateKind`, `DwellThreshold`. Hive's Politica Engine cannot safely consume untyped JSON.

6. **ResidentProfile model absent in hub**: The entire new document-based profile model (`ResidentProfile` with `subjects`, `aspects`, `transitions`, `ProfileStateRule`, `ProfileRule`, `PolicyWindow`, `Provenance`) does not exist in mana-hub. Hub still uses the flat `AlarmProfileVersion`.

7. **Missing `catalogVersion` in bridge payload**: Hive's `AlarmProfile.catalogVersion: CatalogVersion` is not present in `HubPolicyChange`, so hive cannot verify catalog compatibility.

**MODERATE GAPS:**

8. **NightId absent from hub**: Hive's `SceneEvent.night: NightId` tracks shift/session identity. Hub has no concept of this.

9. **Observation.confidence missing**: Hive's `Observation.confidence: Double` is not in hub's `IngestEventRequest`.

10. **Recorder and Evidence not subscribed**: Event-bridge only subscribes to 4 of 6 hive subjects. `recorder.command.v1.>` and `evidence.record.v1.>` are not subscribed.

11. **EffectiveRules not published by hub**: `hub.policy.effective-rules.v1.<resident>` subject is defined in Subjects.kt but no code publishes to it. Hive's Sentinel depends on this.

12. **CensusSnapshot not published by hub**: `hub.census.snapshot.v1` is defined but not published. Hive's `NightWatchRuntime` needs this for bed-resident assignment.

13. **Outbox pattern not implemented**: The documented `hub_policy_outbox` table and `HubPolicyOutboxRelay` do not exist. `AlarmProfileApplicationService` publishes `AlarmProfileChangedEvent` via `DomainEventPublisher` but there is no relay to NATS.

**MINOR GAPS:**

14. **NatsConfig connection name**: Bridge uses `connectionName("mana-hive")` -- should be `connectionName("mana-hub-bridge")`.

15. **Bridge uses blocking connect**: `BridgeConfig.natsConnection()` uses `Nats.connect()` (blocking), while hive-bridge library provides `NatsConfig.connectAsync()` for non-blocking startup.

16. **EventBridge ObjectMapper missing JavaTimeModule**: `BridgeConfig.bridgeObjectMapper()` does not register `JavaTimeModule`, which will fail on `Instant` serialization in policy payloads.

---

### 5. WHAT MANA-HUB MODELS WOULD NEED TO CHANGE

#### Priority 1: SceneEvent model (CRITICAL)

**File to change:** `/home/visiona/workspace/mana-hub/observation/src/main/kotlin/com/hub/observation/domain/model/SceneEvent.kt`

Current hub model is a flat data class with `eventType: String`, `fromState: String?`, `toState: String?`, `triggerType: String?`. This needs to become a **sealed interface** (or at minimum a type-discriminated hierarchy) that can represent all 14 hive subtypes:

- Add `nightId: String?` field (or a dedicated type)
- Add fields for dwell: `threshold: Duration?`, `since: Instant?`
- Add fields for scene state: `field: String?`, `fromSceneState: String?`, `toSceneState: String?`
- Add fields for staff: `staffId: String?`
- Add fields for signal: `monitorId: String?`, `lastHeartbeat: Instant?`
- Add fields for night summary: `transitions: Int?`, `minutesUnknown: Long?`, `episodes: Int?`
- Consider changing `fromState`/`toState` to use hive's `StateKind` names (LYING, SITTING_IN_BED, etc.) rather than free-form strings

**File to change:** `/home/visiona/workspace/mana-hub/observation/src/main/kotlin/com/hub/observation/application/dto/ObservationDtos.kt`

`IngestSceneEventRequest` needs expansion to support all subtypes, or a new type-discriminated request class.

#### Priority 2: Episode model (CRITICAL)

**File to change:** `/home/visiona/workspace/mana-hub/surveillance/src/main/kotlin/com/hub/surveillance/domain/model/Episode.kt`

Add missing fields:
- `ruleId: String?` (hive's `SentinelSignal.EpisodeOpened.rule`)
- `sourceEventId: String?` (for idempotency with hive's eventId)
- `rulesFingerprint: String?` (hive's `rulesFingerprint`)
- `trigger: String?` (StateKind name)
- `triggerField: String?` (scene field)
- `reversible: Boolean?`
- `requiresNvr: Boolean?`
- `confirmationWindowSeconds: Long?`
- `closureCause: String?` (ClosureCause name)
- `gapDurationSeconds: Long?`
- `requiresConfirmation: Boolean?`

**File to change:** `/home/visiona/workspace/mana-hub/surveillance/src/main/kotlin/com/hub/surveillance/domain/model/EpisodeSeverity.kt`

Either:
- Add `HIGH` and remove `EMERGENCY`, OR
- Add mapping function: hive `HIGH` -> hub `CRITICAL`, hive `CRITICAL` -> hub `EMERGENCY` (semantic shift)
- The cleanest fix: align both to `{INFO, WARNING, HIGH, CRITICAL}` (hive's model)

**File to change:** `/home/visiona/workspace/mana-hub/surveillance/src/main/kotlin/com/hub/surveillance/application/dto/EpisodeDtos.kt`

`CreateEpisodeRequest` needs: `ruleId`, `sourceEventId`, `rulesFingerprint`, `trigger`, `reversible`, `requiresNvr`, `confirmationWindowSeconds`, `closureCause`, `gapDurationSeconds`.

#### Priority 3: NotificationEvent model (HIGH)

**File to change:** `/home/visiona/workspace/mana-hub/observation/src/main/kotlin/com/hub/observation/domain/model/NotificationEvent.kt`

Add fields:
- `alertKeyBedId: String?`
- `alertKeyRuleId: String?`
- `alertKeyEpisodeId: String?`
- `channel: String?`
- `step: Int?`
- `recipients: String?` (JSON list)
- `escalationCause: String?`

**File to change:** `/home/visiona/workspace/mana-hub/observation/src/main/kotlin/com/hub/observation/application/dto/ObservationDtos.kt`

`IngestNotificationRequest` needs expansion for multi-step escalation lifecycle.

#### Priority 4: Policy model (CRITICAL for hub->hive)

**File to change:** `/home/visiona/workspace/mana-hub/hive-bridge/src/main/kotlin/com/manahive/contracts/policy/HubPolicyPayload.kt`

`HubPolicyChange` needs:
- `catalogVersion: String?` (hive's `AlarmProfile.catalogVersion`)
- `overrides` should be a **structured type**, not a JSON string. At minimum, define `HubPolicyOverride` sealed interface matching hive's `PolicyOverride`:
  ```kotlin
  sealed interface HubPolicyOverride {
      val ruleId: String
  }
  data class HubHysteresisOverride(override val ruleId: String, val from: String, val to: String, val durationMs: Long) : HubPolicyOverride
  data class HubDwellOverride(override val ruleId: String, val state: String, val warningMs: Long, val alertMs: Long) : HubPolicyOverride
  data class HubComeBackOverride(override val ruleId: String, val baseline: String, val warningMs: Long, val alertMs: Long, val severity: String?, val closure: String?) : HubPolicyOverride
  ```

**File to change:** `/home/visiona/workspace/mana-hub/policy/src/main/kotlin/com/hub/policy/application/service/AlarmProfileApplicationService.kt`

`updateResidentProfile()` must persist and relay structured overrides (not JSON strings) and publish `hub.policy.effective-rules.v1.<resident>` after resolving.

#### Priority 5: New ResidentProfile model (HIGH)

**New files needed** in mana-hub (or extend existing):
- Create `profile` module or extend `policy` module with:
  - `ResidentProfile` aggregate (matching hive's document model)
  - `Subject`, `Aspect`, `ProfileTransition`, `ProfileStateRule`, `ProfileRule` value objects
  - `PolicyWindow`, `Provenance`, `NotifyRule`, `RecordWindow`
  - `ProfileEndpoints` implementation (REST)
- Wire `hub.policy.profile.v1` subject to the NATS egress

#### Priority 6: Event-bridge routing (HIGH)

**File to change:** `/home/visiona/workspace/mana-hub/event-bridge/src/main/kotlin/com/hub/bridge/ingest/NatsIngestService.kt`

`forwardToHub()` must route by subject prefix:
- `perception.observation.v1.*` -> `POST /internal/v1/events`
- `scene.fact.v1.*` -> `POST /internal/v1/scene-events` (with proper payload translation)
- `sentinel.signal.v1.*` -> `POST /api/v1/episodes` or `PATCH /api/v1/episodes/{id}` (with subtype-based routing)
- `alarm.event.v1.*` -> `POST /internal/v1/notifications`

Plus, implement the translation layer that deserializes the hive payload from `payloadJson` and maps it to the correct hub DTO.

#### Priority 7: Outbox and domain events (MODERATE)

- Create `V11__hub_policy_outbox.sql` migration
- Implement `HubPolicyOutboxRelay` in hub
- Wire `AlarmProfileApplicationService.updateResidentProfile()` to write outbox entry in same TX
- Publish to both `hub.policy.change.v1` and `hub.policy.effective-rules.v1.<resident>`

#### Priority 8: Bridge ObjectMapper fix (MINOR)

**File to change:** `/home/visiona/workspace/mana-hub/event-bridge/src/main/kotlin/com/hub/bridge/BridgeConfig.kt`

Add `.registerModule(JavaTimeModule())` to the ObjectMapper to handle `Instant` serialization correctly.