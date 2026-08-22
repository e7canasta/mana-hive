# North Star — Hub

The Hub is the **sole System of Record and Event Store** for the Registro system.
It exposes 111 HTTP endpoints (Rust), persists state in SQLite, and coordinates
three stateless worker binaries over NATS JetStream. The question that orders
every change is:

> Which capability of the Registro are we building, what concepts does it own,
> and what must it never know?

## Architecture

```text
  IA-Edge / IoT sensors
          |
          v
    evt_perception
          |
    +-----+------+
    |             |
mana-engine    mana-hub  (System of Record · HTTP API · SQLite)
(DigitalTwin)     ^  |
(FSM/scene)       |  v
              evt_scene
                  |
            +-----+-----+
            |           |
      mana-sentinel   mana-hub
      (rules/incidents)  (events stored)
            |
            v
        evt_notif
            |
      +-----+-----+
      |           |
mana-vigilancia  mana-hub
(notifications)  (incident log)
            |
            v
        evt_policy (feedback loop to engine)
```

**All binaries:**

| Binary | Role | Persistence |
| --- | --- | --- |
| `mana-hub` | HTTP API, SQLite, event store, cross-context orchestration | SQLite |
| `mana-engine` | DigitalTwin + FSM, scene evaluation | Stateless (via Hub API) |
| `mana-sentinel` | Rule evaluation, incident management | Stateless (via Hub API) |
| `mana-vigilancia` | Notification processing, alert delivery | Stateless (via Hub API) |

## Bounded Contexts

| Context | Domain | Implemented |
| --- | --- | --- |
| `ctx-identidad` | Users, sessions, auth | Yes |
| `ctx-auditoria` | Append-only audit log | Yes |
| `ctx-residencia` | Facilities, wings, rooms, beds | Yes |
| `ctx-poblacion` | Residents and assignments | Yes |
| `ctx-cobertura` | Groups, shifts, coverage | Yes |
| `ctx-cuidado` | Rounds, tasks, notes | Yes |
| `ctx-historia` | Incidents and reviews | Yes |
| `ctx-politica` | Alarm profiles and rules | Yes |
| `ctx-vigilancia` | Alerts and delivery | Yes |
| `ctx-evidence` | Evidences and attachments | Yes |
| `ctx-streams` | Real-time data streams (perception) | Yes |

All 11 contexts are implemented in Rust.

## Architecture Rules

1. **One context does not import another context.** Cross-context coordination
   happens through events (NATS) or via Hub's HTTP API.
2. **One context does not write tables it does not own.** Each context owns its
   SQLite tables exclusively.
3. **Workers are stateless.** Engine, Sentinel, and Vigilancia have no database.
   They read/write state exclusively through Hub's HTTP API.
4. **Hub is the sole System of Record.** All persistent state lives in SQLite
   behind `mana-hub`. Workers trust Hub, not their own local state.
5. **IDs between contexts are opaque references, not private joins.** No foreign
   keys across context boundaries.
6. **A screen may combine contexts, but that composition is a read model of
   Hub, not a new context.**
7. **No generic tables to avoid deciding the domain.** Every table has a clear
   owner context.
8. **NATS JetStream is the communication backbone.** Events flow through five
   topics: `evt_perception`, `evt_scene`, `evt_notif`, `evt_policy`, and
   `consumers`. No direct binary-to-binary RPC.

## How to Add a Feature

1. **Name the feature** and its business question.
2. **Pick the owning bounded context** (or create one only if a real boundary
   exists).
3. **Define what it owns** and what it explicitly does not.
4. **Implement invariants** in `ctx-*/src/domain`.
5. **Implement persistence** in `ctx-*/src/store`.
6. **Add use cases and cross-context coordination** in `mana-app`.
7. **Add HTTP contracts** in `mana-http` and, if applicable, `mana-sdk`.
8. **If it triggers side effects**, publish an event via `mana-nats` (topic
   `evt_*`) and subscribe in the appropriate worker binary.
9. **If a worker needs to act on it**, add a consumer in the worker's crate
   (`mana-engine-worker`, `mana-sentinel`, or `mana-vigilancia-worker`).
10. **Test** domain, persistence, use case, HTTP contract, and event flow.

## Where to Put Decisions

| Decision | Location |
| --- | --- |
| What a concept means | `docs/contextos/ctx-*.md` |
| How domain, use case, and data connect | `docs/funcional/modelo-dominio/` |
| Which tables each context owns | `docs/contextos/ownership.toml` |
| What gets built first | `docs/reference/architecture.md` |
| What a use case does today | `docs/funcional/casos-uso/` |
| Who serves a route | `mana-http` route registration |
| How events are transported | `mana-nats` topic definitions |
| Architecture decisions | `docs/funcional/north-star.md` |
