# Workspace Modules

Complete list of all workspace crates organized by responsibility.

## Core infrastructure

| Crate | Purpose |
| --- | --- |
| `mana-kernel` | IDs, time and cross-cutting error types |
| `mana-storage` | SQLite connection pool, PRAGMAs and migrations |
| `mana-wire` | DTOs and shared types for the HTTP contract |

## Domain contexts (`ctx-*`)

| Crate | Purpose |
| --- | --- |
| `ctx-identidad` | Identity management |
| `ctx-auditoria` | Audit trail |
| `ctx-residencia` | Residence / room management |
| `ctx-poblacion` | Population / census |
| `ctx-cobertura` | Coverage and shifts |
| `ctx-cuidado` | Care plans and interventions |
| `ctx-historia` | Clinical history |
| `ctx-politica` | Policy and rules |
| `ctx-vigilancia` | Surveillance workflows |
| `ctx-evidence` | Evidence attachment |
| `ctx-streams` | Real-time event streams |

## Engines

| Crate | Purpose |
| --- | --- |
| `mana-engine-v2` | DigitalTwin FSM and SceneEvent processing |
| `mana-engine-worker` | NATS subscriber that drives the engine |
| `mana-motores` | Pure business rules (alarmas, autopilot, recomendacion, catalogo) |
| `mana-sentinel` | Rule evaluation and incident management |

## Workers

| Crate | Purpose |
| --- | --- |
| `mana-vigilancia-worker` | Notification processing for surveillance |

## Application layer

| Crate | Purpose |
| --- | --- |
| `mana-app` | Application services and cross-context coordination |
| `mana-http` | HTTP boundary and route dispatch |
| `mana-observation` | Sensor event processing and state tracking |

## Clients

| Crate | Purpose |
| --- | --- |
| `mana-sdk` | HTTP client SDK for CLI and scene actors |
| `mana-hub-client` | HTTP client for workers (sentinel, vigilancia) |
| `mana-nats` | NATS JetStream broker, publisher and subscriber |

## Binaries (`bins/`)

| Crate | Purpose |
| --- | --- |
| `mana-hub` | HTTP API server and SQLite persistence |
| `mana-engine` | DigitalTwin engine (pure NATS) |
| `mana-sentinel` | Alarm evaluation worker |
| `mana-vigilancia` | Notification processing worker |

## Other

| Crate | Purpose |
| --- | --- |
| `mana-cli` | Command-line interface tool |
| `xtask` | Build and dev task runner |
| `mana-integration-tests` | Integration test crate |

## Rules of thumb

- Domain rules (e.g. "a room cannot repeat number within a wing") go in the
  owning `ctx-*` crate, not in `mana-http` or `mana-storage`.
- When an operation spans multiple contexts, the use case belongs to the domain
  that owns the objective and `mana-app` coordinates the cross.
