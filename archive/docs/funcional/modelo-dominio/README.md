# Domain Model by Context

This directory connects three levels without mixing them:

```text
domain
  -> use cases
  -> data model
```

- **Domain:** concepts, relationships, and rules that must be true.
- **Use case:** how an actor or the application changes or queries the domain.
- **Data:** how the domain is persisted in tables, columns, indices, and migrations.

## All Implemented Contexts

| Context | Domain Model | Use Cases | Tables |
|---------|-------------|-----------|--------|
| `ctx-identidad` | users, sessions, authenticated actor | [cases](../casos-uso/ctx-identidad.md) | `users`, `auth_sessions` |
| `ctx-auditoria` | append-only audit trail | [cases](../casos-uso/ctx-auditoria.md) | `audit_log` |
| `ctx-residencia` | facility, wing, room, bed | [cases](../casos-uso/ctx-residencia.md) | `facilities`, `wings`, `rooms`, `beds`, `wing_planograms`, `room_privacy_configs` |
| `ctx-poblacion` | resident, bed assignment, attributes | [cases](../casos-uso/ctx-poblacion.md) | `residents`, `resident_bed_assignments`, `resident_attributes` |
| `ctx-cobertura` | staff groups, shifts, wing coverage | [cases](../casos-uso/ctx-cobertura.md) | `staff_groups`, `staff_group_members`, `facility_shifts`, `unit_shift_coverages` |
| `ctx-cuidado` | rounds, tasks, care notes | [cases](../casos-uso/ctx-cuidado.md) | `rounds`, `round_tasks`, `care_notes` |
| `ctx-historia` | incident detections, reviews | [cases](../casos-uso/ctx-historia.md) | `incident_detections`, `incident_reviews` |
| `ctx-politica` | alarm profiles, catalog, autopilot | [cases](../casos-uso/ctx-politica.md) | `alarm_profile_versions` |
| `ctx-vigilancia` | alerts, transitions, deliveries | [cases](../casos-uso/ctx-vigilancia.md) | `alerts`, `alert_transitions`, `notification_deliveries`, `notification_delivery_events`, `alert_escalations` |
| `ctx-evidence` | evidence, timelines, clip windows | [cases](../casos-uso/observacion.md) | `evidence`, `timelines`, `clip_windows` |
| `ctx-streams` | streams, ROI regions | — | `streams`, `stream_regions` |
| observacion | sensor events, bed states, summaries | [cases](../casos-uso/observacion.md) | `sensor_events`, `current_bed_states`, `sleep_summaries`, `mobility_summaries`, `bathroom_summaries`, `scene_events`, `notification_events` |

## Reading Rules

1. A table does not define an aggregate by itself.
2. A use case does not belong to the HTTP handler: the handler only invokes it.
3. `mana-app` can coordinate contexts but does not become the owner of their entities.
4. A field from another table does not create ownership: the owner is in `ownership.toml`.
5. If the domain changes, review in this order: use case, invariants, contract, data.

## Realization Layers

```text
actor / client
  -> mana-http + mana-wire
  -> mana-app: command, authorization, transaction
  -> ctx-*/domain: rule
  -> ctx-*/store: mapping
  -> mana-storage: SQLite
```

The domain model does not know Diesel, Axum, JSON, or Tokio.
