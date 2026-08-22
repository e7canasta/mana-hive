# Crate: `mana-http`

## Purpose

HTTP boundary of the Hub. Receives requests, identifies the route, converts bodies to commands, invokes `mana-app`, and transforms results into public responses.

## Dispatch by ID

Handlers are registered by `rutas.toml` ID, not hardcoded paths. The route table decides whether a request goes to Rust.

An entry marked `sirve = "rust"` without a registered handler prevents the Hub from starting.

## Handler Groups (13 groups, 111 endpoints)

| Group | Context | Endpoints |
|-------|---------|-----------|
| `identity_handlers` | ctx-identidad | 6 (login, me, logout, users CRUD) |
| `audit_handlers` | ctx-auditoria | 1 (list) |
| `residence_handlers` | ctx-residencia | 19 (facilities, wings, rooms, beds, planogram, privacy) |
| `poblacion_handlers` | ctx-poblacion | 8 (residents, assignments) |
| `cobertura_handlers` | ctx-cobertura | 9 (shifts, staff groups, coverage) |
| `cuidado_handlers` | ctx-cuidado | 9 (rounds, tasks, notes) |
| `historia_handlers` | ctx-historia | 5 (incidents, reviews) |
| `politica_handlers` | ctx-politica | 8 (alarm presets, profiles, autopilot) |
| `vigilancia_handlers` | ctx-vigilancia | 7 (alerts, deliveries) |
| `observation_handlers` | observacion | 14 (events, boards, summaries, peek) |
| `streams_handlers` | ctx-streams | 6 (streams, regions) |
| `internal_handlers` | ctx-evidence | 10 (evidence, timelines, clip windows) |
| `engine_handlers` | engine | 3 (perception, tick, state) |

## Error Contract

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "...",
    "fields": {}
  }
}
```

## Does Not

- Query Diesel directly.
- Enforce business rules.
- Make authorization decisions beyond delegating the actor to mana-app.
