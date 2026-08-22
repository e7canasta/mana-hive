# NATS Topics Reference

Authoritative reference reverse-engineered from `mana-nats/src/topics.rs`, `publisher.rs`, and consumer code.

## Topic Overview

| # | Topic | Producer | Consumers |
|---|-------|----------|-----------|
| 1 | `evt_perception` | IA-Edge (external) | Engine, Hub |
| 2 | `evt_scene` | Engine | Sentinel, Hub |
| 3 | `evt_notif` | Sentinel | Vigilancia, Hub |
| 4 | `evt_policy` | Hub (API) | Engine, Sentinel |

## Topic Details
### 1. `evt_perception` — Perception Events

- **Subject:** `evt_perception`
- **Producer:** IA-Edge (external perception service)
- **Consumers:**
  - Engine — consumer group `engine-processor`
  - Hub — consumer group `hub-persistence`
- **Trigger:** IA-Edge detects a bed-state change (person in/out, body parts, objects).

#### Wire Format — PerceptionEvent

```json
{
  "event_id": "string (UUID)",
  "monitor_key": "string",
  "bed_id": "string",
  "resident_id": "string | null",
  "resolution": { "Resolved": { "bed_id": "string", "resident_id": "string" } } | "Unresolved",
  "kind": "string",
  "state": "string | null",
  "sleeping": "bool | null",
  "zone": "string | null",
  "substate": "string | null",
  "extremities_out_of_bed": "bool",
  "body_parts_out": ["string"],
  "objects": { "walker": "string", "wheelchair": "string" },
  "confidence": "f64",
  "occurred_at": "DateTime<Utc>",
  "received_at": "DateTime<Utc>",
  "trace_id": "string | null"
}
```

### 2. `evt_scene` — Scene Events

- **Subject:** `evt_scene`
- **Producer:** Engine (via `publish_scene`)
- **Consumers:**
  - Sentinel — consumer group `sentinel-evaluator`
  - Hub — consumer group `hub-scene`
- **Trigger:** Engine processes a `PerceptionEvent` through the `DigitalTwin` FSM and produces scene transitions.

#### Wire Format — SceneEvent

See [docs/specs/scene-event.md](../specs/scene-event.md) for the full struct definition.

### 3. `evt_notif` — Notification Events

- **Subject:** `evt_notif`
- **Producer:** Sentinel (via `publish_notif` in `NatsBroker`)
- **Consumers:**
  - Vigilancia — consumer group `vigilancia-handler`
  - Hub — consumer group `hub-notif`
- **Trigger:** Sentinel evaluates a scene event against alarm rules and determines a notification is needed.

#### Wire Format — NotificationEvent

```json
{
  "category": "off | notify | alarm | mark",
  "bed_id": "string",
  "resident_id": "string | null",
  "event_type": "perception | dwell | transition | change",
  "timestamp": "string (RFC 3339)",
  "rule_id": "string | null",
  "risk_level": "high | medium | null",
  "trace_id": "string | null"
}
```

### 4. `evt_policy` — Policy Events

- **Subject:** `evt_policy`
- **Producer:** Hub (API endpoint, publishes `PolicyEvent`)
- **Consumers:**
  - Engine — consumer group `engine-policy`
  - Sentinel — consumer group `sentinel-policy`
- **Trigger:** Admin changes a resident's care policy (e.g. alarm rules, dwell thresholds) via Hub API.

#### Wire Format — PolicyEvent

```json
{
  "event_type": "string",
  "resident_id": "string",
  "policy_type": "string",
  "effective_at": "string (RFC 3339)",
  "payload": "object (arbitrary JSON)"
}
```

## Consumer Groups

All durable consumer names defined in `mana-nats/src/topics.rs:consumers`:

| Consumer Constant | Consumer Group Name | Subscribes To |
|---|---|---|
| `ENGINE_PERCEPTION` | `engine-processor` | `evt_perception` |
| `ENGINE_POLICY` | `engine-policy` | `evt_policy` |
| `SENTINEL_SCENE` | `sentinel-evaluator` | `evt_scene` |
| `SENTINEL_POLICY` | `sentinel-policy` | `evt_policy` |
| `VIGILANCIA_NOTIF` | `vigilancia-handler` | `evt_notif` |
| `HUB_PERCEPTION` | `hub-persistence` | `evt_perception` |
| `HUB_SCENE` | `hub-scene` | `evt_scene` |
| `HUB_NOTIF` | `hub-notif` | `evt_notif` |
