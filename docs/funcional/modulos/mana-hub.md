# Binary: `mana-hub`

## Purpose

The Hub is the sole System of Record and Event Store. It owns SQLite, serves the HTTP API, and orchestrates all NATS communication.

## Startup

1. Load environment configuration.
2. Build `AppState` with SQLite pool.
3. Execute all context migrations (identidad, auditoria, residencia, poblacion, cobertura, cuidado, historia, politica, vigilancia, evidence, streams, observation).
4. Optionally seed demo users.
5. Register all 13 Rust handler groups (111 endpoints).
6. Subscribe to NATS topics: `evt_scene`, `evt_notif`, `evt_policy`.
7. Start HTTP server on `:8780`.

## Ports

| Service | Default | Purpose |
|---------|---------|---------|
| Hub | `8780` | Public HTTP API |

## Key Environment Variables

- `MANA_HUB_DATABASE_URL`: SQLite path, default `hub.sqlite`.
- `MANA_HUB_SEED_DEMO`: enable demo users.
- `MANA_HUB_SEED_RESIDENCE`: seed physical structure.
- `MANA_NATS_URL`: NATS connection, default `nats://127.0.0.1:4222`.
- `API_ENABLED_CAPABILITIES`: comma-separated active capabilities.

## Health Checks

```bash
curl http://localhost:8780/health
curl http://localhost:8780/__hub/ready
curl http://localhost:8780/__hub/rutas
```

## Responsibilities

- Serve all 111 HTTP endpoints (13 handler groups).
- Persist all events to SQLite (sensor_events, scene_events, notification_events, etc.).
- Publish to NATS: `evt_perception` (after ingest), `evt_policy` (on profile changes).
- Subscribe to NATS: `evt_scene` (persist scene events), `evt_notif` (persist notifications), `evt_policy` (forward to Engine/Sentinel).
- Resolve monitor_key → bed_id → resident_id for incoming perception events.
