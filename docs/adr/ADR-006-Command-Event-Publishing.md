# ADR-006: Command/Event Publishing Architecture

## Status
Accepted — 2024-01-16

## Context

Harbor engine generates `NoticeCommand` (domain). The service converts it to `NoticeEvent` (bus) and publishes. This works but couples the service to domain conversion logic. We want engines to own their publications.

## Decision

### Big Picture

```
┌─────────────────────────────────────────────────────────┐
│                    mana-hive engines                      │
│                                                           │
│  SceneEngine    → SceneEvent                              │
│  SentinelEngine → SentinelSignal                          │
│  HarborEngine   → NoticeCommand → NoticeEvent             │
│  RecorderEngine → RecordingCommand → EvidenceRecord        │
│                                                           │
│  Each engine generates DOMAIN OBJECTS.                    │
│  The SERVICE converts domain objects to BUS EVENTS.       │
└─────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │   Event Bus     │  ← mana-hub escucha
                    │  (NATS JetStream)│    system of record
                    └─────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────┐
│              External agents (subscribers)                │
│                                                           │
│  NVR Simulator  → escucha RecordingCommand                │
│                   emite ClipCreated                        │
│  Sender         → escucha NoticeEvent.Dispatch            │
│                   emite NoticeEvent.Sent                   │
│  App (staff)    → emite NoticeEvent.Seen/Confirmed        │
│  Scheduler      → emite NoticeEvent.Escalated/Expired     │
│                                                           │
│  Cada agente acciona sobre COMANDOS.                      │
│  Cada agente publica EVENTOS al mismo canal.              │
└─────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │   mana-hub      │  ← system of record
                    │  (escucha       │    registra TODOS los
                    │   TODOS los     │    eventos: hive +
                    │   eventos)      │    external agents
                    └─────────────────┘
```

### Rule: System of Record = events only, not commands

- **Commands** are instructions to do something (`RecordingCommand`, `NoticeCommand`)
- **Events** are facts that happened (`SceneEvent`, `EvidenceRecord`, `NoticeEvent`)
- **mana-hub** (system of record) consumes EVENTS, not commands
- **External agents** consume commands, act on them, and emit events back

### Event Taxonomy

| Event Type | Source Engine | Subject | Content |
|------------|---------------|---------|---------|
| `SceneEvent` | SceneEngine | `scene.fact.v1.<bed>` | Observations, transitions, signal lost |
| `SentinelSignal` | SentinelEngine | `sentinel.signal.v1.<bed>` | Episodes opened/closed, comeBack alerts |
| `NoticeEvent` | HarborEngine (via service) | `notice.event.v1.<notice>` | Dispatch, Sent, Confirmed, Escalated, Resolved |
| `EvidenceRecord` | RecorderEngine | `evidence.record.v1.<bed>` | Recording started/stopped, clip created |

### Command Taxonomy (internal, not on bus)

| Command | Source | Purpose |
|---------|--------|---------|
| `NoticeCommand` | HarborEngine | Internal to harbor domain |
| `RecordingCommand` | RecorderEngine | Instructions to NVR adapter |

### Future: Engine-Owned Publishing (planned, not implemented)

Each engine receives a publisher interface and publishes directly:

```kotlin
interface EnginePublisher {
    fun publishSceneEvent(bed: BedId, event: SceneEvent)
    fun publishSentinelSignal(bed: BedId, signal: SentinelSignal)
    fun publishNoticeEvent(bed: BedId, event: NoticeEvent)
    fun publishRecordingCommand(bed: BedId, command: RecordingCommand)
    fun publishEvidenceRecord(bed: BedId, record: EvidenceRecord)
}
```

Service implements this and passes to `ResidentRuntime` like `Clock`. Engines publish directly, service doesn't need conversion logic.

## Consequences

- Hub gets complete workflow from all events (hive + external agents)
- External agents can subscribe to commands and emit events independently
- Event versioning via subject taxonomy (v1, v2, etc.)
- Migration path: dual-publish `alarm.event` + `notice.event` during transition
