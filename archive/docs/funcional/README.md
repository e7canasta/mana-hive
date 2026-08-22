# Functional Documentation

This folder explains what the Registry can do today, how each use case is executed, and what remains in migration. It is the functional guide for product, frontend, QA, and new team members.

It does not replace design documents:

- [`../contextos/`](../contextos/) defines ownership, aggregates, tables, and invariants.
- [`../../rutas.toml`](../../rutas.toml) defines who serves each route.
- [`../contrato/`](../contrato/) defines the documented HTTP contract.

## Sprint Status

| Slice | Status | Notes |
|-------|--------|-------|
| Identidad | Rust public | Login, sessions, users, capabilities |
| Auditoria | Rust public | Query and append-only writes |
| Residencia | Rust public | Facilities, wings, rooms, beds, planogram, privacy |
| Poblacion | Rust public | Residents, assignments, discharge, audit |
| Cobertura | Rust public | Shifts, groups, memberships, wing coverage |
| Cuidado | Rust public | Rounds, tasks, care notes |
| Historia | Rust public | Incident detections, reviews, sequences |
| Politica | Rust public | Catalog, presets, profiles, versions, autopilot |
| Vigilancia | Rust public | Alerts, transitions, deliveries, escalation |

## Documentation Map

### Use Cases

- [`casos-uso/`](casos-uso/): use cases with business logic by bounded context.
- [`modelo-dominio/`](modelo-dominio/): entities, invariants, use cases, and table mapping by context.
- [`north-star.md`](north-star.md): separation by functionality, domain, and bounded context.

### Domain Contexts

- [`contextos/ctx-identidad.md`](../contextos/ctx-identidad.md): access, sessions, user management.
- [`contextos/ctx-auditoria.md`](../contextos/ctx-auditoria.md): change trail and forensic queries.
- [`contextos/ctx-residencia.md`](../contextos/ctx-residencia.md): physical structure of the residence.
- [`contextos/ctx-poblacion.md`](../contextos/ctx-poblacion.md): resident roster, assignments, clinical cycle.
- [`contextos/ctx-cobertura.md`](../contextos/ctx-cobertura.md): staff groups, shifts, wing coverage.
- [`contextos/ctx-cuidado.md`](../contextos/ctx-cuidado.md): rounds, tasks, care notes.
- [`contextos/ctx-historia.md`](../contextos/ctx-historia.md): incident detections and reviews.
- [`contextos/ctx-politica.md`](../contextos/ctx-politica.md): alarm profiles and catalog.
- [`contextos/ctx-vigilancia.md`](../contextos/ctx-vigilancia.md): alerts, deliveries, escalation.

### Platform Modules

- [`modulos/README.md`](modulos/README.md): which modules are technical and where use cases live.
- [`modulos/mana-kernel.md`](modulos/mana-kernel.md): cross-cutting types and error vocabulary.
- [`modulos/mana-storage.md`](modulos/mana-storage.md): SQLite, pool, and migrations.
- [`modulos/mana-app.md`](modulos/mana-app.md): use cases, authorization, and transactions.
- [`modulos/mana-http.md`](modulos/mana-http.md): transport and route dispatch.
- [`modulos/mana-wire.md`](modulos/mana-wire.md): DTOs and HTTP envelope.
- [`modulos/mana-sdk.md`](modulos/mana-sdk.md): Rust client, CLI, and scenes.
- [`modulos/mana-hub.md`](modulos/mana-hub.md): composition and process operation.

## Common Flow

```text
client
  -> mana-http
  -> mana-app
  -> ctx-* and store
  -> SQLite (hub)
```

Authenticated reads verify the token and capability before querying. Mutations verify the actor, write the business context, and register audit within the same SQLite transaction.

## Capabilities

| Capability | Allows |
|------------|--------|
| `master.structure.read` | Read users and physical structure |
| `master.structure.write` | Create or update users and physical structure |
| `audit.read` | Query audit trail |
| `residents.read` | Read resident data |
| `residents.write` | Create/update residents |
| `monitoring.board.read` | Read wing boards |
| `monitoring.live.read` | Read live state |
| `incidents.read` | Read incidents |
| `incidents.manage` | Manage incident reviews |
| `analytics.read` | Read analytics and reports |
| `sleep.read` | Read sleep summaries |
| `mobility.read` | Read mobility summaries |
| `bathroom.read` | Read bathroom summaries |

## Verification

From `hub/`:

```bash
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```
