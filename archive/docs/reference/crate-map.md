# Crate Map

> Reverse-engineered from `Cargo.toml` and source. 32 workspace members total.

## Core Infrastructure (Layer 0)

| Crate | Lines | Role |
|-------|------:|------|
| `mana-kernel` | 419 | IDs, time, error types — zero internal deps |
| `mana-storage` | 87 | SQLite pool, PRAGMAs, diesel migrations |
| `mana-wire` | 635 | DTOs for the HTTP contract |

## Domain Contexts (Layer 1) — 11 crates

All depend on `mana-kernel` + `mana-storage`. All use `diesel` + `diesel_migrations`.

| Crate | Lines | Scope |
|-------|------:|-------|
| `ctx-identidad` | 1253 | Users, sessions, RBAC |
| `ctx-auditoria` | 381 | Append-only audit trail |
| `ctx-residencia` | 3120 | Facilities, wings, rooms, beds |
| `ctx-poblacion` | 2265 | Residents, assignments |
| `ctx-cobertura` | 1856 | Staff groups, shifts, coverage |
| `ctx-cuidado` | 1455 | Rounds, tasks, care notes |
| `ctx-historia` | 1075 | Incident detections, reviews |
| `ctx-politica` | 768 | Alarm profiles, catalog *(also depends on `mana-motores`)* |
| `ctx-vigilancia` | 1763 | Alerts, deliveries |
| `ctx-evidence` | 697 | Evidence, timelines, clip windows |
| `ctx-streams` | 700 | Streams, ROI regions |

## Engines (Layer 1–2)

| Crate | Lines | Role |
|-------|------:|------|
| `mana-engine-v2` | 1310 | DigitalTwin, FSM, SceneEvent *(depends on `mana-kernel` only)* |
| `mana-engine-worker` | 183 | NATS subscriber for engine |
| `mana-motores` | 3027 | Pure business rules: alarmas, autopilot, recomendación, catálogo |
| `mana-sentinel` | 1110 | Rule evaluation, incident management |

## Workers (Layer 2–3)

| Crate | Lines | Role |
|-------|------:|------|
| `mana-vigilancia-worker` | 101 | Notification processing |
| `mana-observation` | 1944 | Sensor event processing, state tracking |

## Application Layer (Layer 3)

| Crate | Lines | Role |
|-------|------:|------|
| `mana-app` | 8053 | Application services, cross-context coordination |
| `mana-http` | 6367 | HTTP boundary, route dispatch (Axum) |

## Clients

| Crate | Lines | Role |
|-------|------:|------|
| `mana-sdk` | 4283 | HTTP client SDK for CLI/scenes |
| `mana-hub-client` | 208 | Lightweight HTTP client for workers |
| `mana-nats` | 495 | NATS JetStream broker, publisher, subscriber |

## Binaries (Layer 4)

| Crate | Lines | Role |
|-------|------:|------|
| `mana-hub` | 277 | HTTP API server |
| `mana-engine` | 27 | DigitalTwin engine |
| `mana-sentinel` | 278 | Alarm evaluation worker |
| `mana-vigilancia` | 123 | Notification processing |

## Other

| Crate | Lines | Role |
|-------|------:|------|
| `mana-cli` | 3065 | CLI tool |
| `xtask` | 345 | Build/dev task runner |
| `mana-integration-tests` | — | Integration test crate |

## Dependency Graph

```
mana-kernel (0 deps)
├── mana-storage
├── mana-wire → mana-kernel
├── mana-motores → mana-kernel
├── mana-engine-v2 → mana-kernel
│
├─ All 11 ctx-* crates → mana-kernel, mana-storage
│   └── ctx-politica → mana-kernel, mana-storage, mana-motores
│
├── mana-sentinel → mana-engine-v2, mana-motores, ctx-politica
├── mana-observation → mana-engine-v2, mana-storage
├── mana-engine-worker → mana-engine-v2, mana-nats
│
├── mana-app → all ctx-*, mana-observation, mana-engine-v2,
│              mana-motores, mana-nats, mana-storage
├── mana-http → mana-app, mana-engine-v2, mana-wire, ctx-evidence
│
├── mana-nats → mana-engine-v2, mana-observation, ctx-evidence
│
├── mana-hub (bin) → mana-app, mana-http, mana-nats, mana-observation
├── mana-engine (bin) → mana-engine-v2, mana-engine-worker, mana-nats
├── mana-sentinel (bin) → mana-sentinel, mana-engine-v2,
│                          mana-hub-client, mana-nats, ctx-politica
├── mana-vigilancia (bin) → mana-hub-client, mana-nats
│
├── mana-sdk (standalone HTTP client)
├── mana-hub-client (standalone HTTP client)
├── mana-cli → mana-sdk
│
└── xtask (standalone)
```

*Lines are approximate source-line counts. Layer assignment reflects compile-time dependency depth, not runtime deployment.*
