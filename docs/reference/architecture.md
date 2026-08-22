# Architecture Reference

Virtual Rounds is a distributed physical security system built on NATS JetStream with four production binaries, each owning a single responsibility. The Hub is the sole System of Record; all state mutations flow through its HTTP API. Workers are stateless orchestrators that persist exclusively via Hub, while NATS JetStream provides the asynchronous communication backbone between all components.

## System Diagram

```
                    ┌─────────────────────────────────────────────┐
                    │            NATS JetStream (5 streams)       │
                    │  evt_perception  evt_scene  evt_notif       │
                    │  evt_policy      consumers                  │
                    └──┬──────────┬──────────┬──────────┬────────┘
                       │          │          │          │
            ┌──────────┘   ┌─────┘     ┌────┘    ┌─────┘
            ▼              ▼           ▼         ▼
     ┌─────────────┐ ┌──────────┐ ┌─────────┐ ┌──────────────┐
     │ mana-engine  │ │mana-hub  │ │mana-    │ │mana-         │
     │ (FSM/Digital │ │(HTTP API │ │sentinel │ │vigilancia    │
     │  Twin engine)│ │+ SQLite  │ │(Rules/  │ │(Notification │
     │              │ │+ NATS)   │ │Incidents│ │ processing)  │
     └──────────────┘ └────┬─────┘ └────┬────┘ └──────────────┘
                           │            │
                    Persists via    Creates via
                    Hub HTTP API    Hub HTTP API
```

## Binary Responsibilities

| Binary | Path | Role | Transport | State |
|---|---|---|---|---|
| `mana-hub` | `bins/mana-hub/` | HTTP API server, SQLite persistence, NATS subscriber | HTTP (port 8780) + NATS | SQLite DB (System of Record) |
| `mana-engine` | `bins/mana-engine/` | DigitalTwin + FSM evaluation engine | Pure NATS | Stateless |
| `mana-sentinel` | `bins/mana-sentinel/` | Rule evaluation, incident management, alert creation | Pure NATS + Hub HTTP | Stateless |
| `mana-vigilancia` | `bins/mana-vigilancia/` | Notification processing and delivery | Pure NATS + Hub HTTP | Stateless |

## NATS JetStream Topics

| Topic | Producer | Consumer(s) | Persistence |
|---|---|---|---|
| `evt_perception` | Edge devices | `mana-hub` (persists) → `mana-engine` (subscribes) | JetStream |
| `evt_scene` | `mana-engine` | `mana-hub` (persists) + `mana-sentinel` (subscribes) | JetStream |
| `evt_notif` | `mana-sentinel` | `mana-hub` (persists) + `mana-vigilancia` (subscribes) | JetStream |
| `evt_policy` | `mana-hub` | `mana-engine` + `mana-sentinel` (both subscribe) | JetStream |
| `consumers` | Various | Consumer group tracking | JetStream |

**Flow:** `evt_perception` → Engine processes → `evt_scene` → Sentinel evaluates rules → `evt_notif` → Vigilancia delivers. Policy updates broadcast via `evt_policy` to both Engine and Sentinel.

## Dependency Layers

```
Layer 0 (leaves)     mana-kernel, mana-storage, mana-sdk,
                     mana-hub-client, xtask

Layer 1              mana-wire, mana-engine-v2, mana-motores,
                     all ctx-* crates

Layer 2              mana-observation, mana-nats

Layer 3              mana-engine-worker, mana-vigilancia-worker,
                     mana-app, mana-http

Layer 4 (binaries)   mana-hub, mana-engine, mana-sentinel,
                     mana-vigilancia
```

Dependencies flow strictly downward: Layer 4 depends on Layer 3, which depends on Layer 2, and so on. No upward or lateral cross-references between crates at the same layer.

## Architecture Rules

1. **Workers are stateless.** They have no database. All persistence goes through the Hub HTTP API.
2. **Hub is the sole System of Record and Event Store.** SQLite lives only in `mana-hub`. No other binary writes directly to a database.
3. **NATS JetStream is the communication backbone.** All inter-binary messaging flows through JetStream streams. No direct HTTP calls between binaries except workers → Hub.
4. **A context does not import another context.** Each bounded context is an isolated crate boundary. Cross-context access uses opaque IDs, not private joins.
5. **A context does not write tables it does not own.** Each context owns its domain, its store, and its migrations. No shared tables.
6. **Cross-context coordination lives in `mana-app`.** Orchestration logic that touches multiple contexts belongs in the application layer, not in individual contexts.
7. **IDs between contexts are opaque references.** One context's primary key is another context's foreign key reference only. No implicit relational joins across context boundaries.

## Design Rules (memoria-diseno)

These four rules are **build-breaking** — violating them causes compilation failure, not just a warning:

1. **No direct domain imports across contexts.** A context crate must never depend on another context's domain types. Use wire types or opaque IDs only.
2. **No shared database tables.** Each context's migrations own their tables exclusively. No table may be written by more than one context.
3. **No direct SQLite access outside Hub.** Workers and other binaries must persist through the Hub HTTP API. Direct DB connections are forbidden.
4. **No upward dependency flow.** Lower layers must never import from higher layers. The dependency graph is a strict DAG with leaves at Layer 0.
