# Hub Documentation

Source of truth for the System of Record in Rust. Describes the system as built, not a mechanical translation of any previous implementation.

## Authority

| Artifact | Authority over |
|----------|---------------|
| `reference/architecture.md` | System architecture, 4 binaries, NATS pipeline |
| `reference/crate-map.md` | All 32 workspace members, dependencies |
| `reference/domain.md` | 11 bounded contexts, aggregates, tables |
| `reference/api.md` | All HTTP endpoints (96 routes) |
| `reference/nats-topics.md` | NATS topics, wire format, producers/consumers |
| `reference/engine.md` | DigitalTwin, FSM, SceneEvent engine |
| `reference/data-model.md` | 34 tables, complete DDL, ER diagram |
| `contrato/openapi.yaml` | Versioned HTTP contract |
| `contextos/*.md` | Ownership, model, invariants per context |
| `arquitectura/memoria-diseno.md` | Architecture rules and code boundaries |
| `../rutas.toml` | Runtime route migration status |

## Structure

```
docs/
├── reference/          ← Core reference (reverse-engineered from code)
│   ├── architecture.md
│   ├── crate-map.md
│   ├── domain.md
│   ├── api.md
│   ├── nats-topics.md
│   └── engine.md
├── arquitectura/       ← Design rules and architecture
│   └── memoria-diseno.md
├── contextos/          ← Domain context ownership (crown jewel)
│   ├── ctx-*.md (11 files)
│   ├── ownership.toml
│   ├── observacion.md
│   ├── observacion-v2.md
│   └── plataforma.md
├── contrato/           ← HTTP contract (OpenAPI)
│   ├── openapi.yaml
│   └── modulos/*.yaml
├── especificacion/     ← Engine specifications
│   ├── motor-de-alarmas.md
│   └── motores/*.md
├── specs/              ← Data contracts
│   ├── scene-event.md
│   ├── person-state.md
│   └── edge-contract.md
├── design/             ← Implementation design
│   ├── engine-digital-twin.md
│   ├── engine-fsm.md
│   ├── engine-timers.md
│   ├── engine-super-loop.md
│   ├── engine-ports.md
│   ├── hub-scene-event-handler.md
│   └── end-to-end-flow.md
└── funcional/          ← Functional documentation
    ├── north-star.md
    ├── README.md
    ├── modulos/*.md
    ├── casos-uso/*.md
    ├── modelo-dominio/*.md
    └── catalogo-alarmas-arquitectura.md
```

## Contract Rule

The contract is written in OpenAPI and maintained independently of Rust and TypeScript. No client types are generated from Rust and the server does not import any TypeScript package. `mana-wire` contains hand-written DTOs tested against the contract.

## Quick Navigation

- **New to the codebase?** Start with `reference/crate-map.md` and `reference/architecture.md`
- **Working on a context?** Read `contextos/ctx-*.md` and `funcional/casos-uso/ctx-*.md`
- **Adding an endpoint?** Check `reference/api.md` and `rutas.toml`
- **Working on the engine?** Read `reference/engine.md` and `design/engine-*.md`
- **Understanding NATS flow?** Read `reference/nats-topics.md` and `design/end-to-end-flow.md`
