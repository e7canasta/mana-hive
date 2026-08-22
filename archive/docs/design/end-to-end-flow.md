# Design: End-to-End Flow

## Purpose

Document the complete data flow from edge detection to notification across the 4-binary architecture connected via NATS.

## Architecture Overview

```
┌──────────────┐
│   EDGE (IA)  │
│  Perceptions │
└──────┬───────┘
       │ POST /internal/v1/events
       ▼
┌──────────────────────────────────────────────────────────────────┐
│                         NATS Message Bus                         │
│                                                                  │
│  Subjects: evt_perception · evt_scene · evt_notif · evt_policy  │
└──┬───────────┬───────────┬───────────┬───────────┬──────────────┘
   │           │           │           │           │
   ▼           ▼           ▼           ▼           ▼
┌──────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌───────────┐
│ HUB  │  │ ENGINE │  │ HUB*   │  │SENTINEL│  │VIGILANCIA │
│ Ingest│  │Digital │  │Persist │  │ Rules  │  │  Alerts   │
│      │  │  Twin  │  │  Handler│  │Engine  │  │           │
└──────┘  └────────┘  └────────┘  └────────┘  └───────────┘

* Hub has multiple subscription roles (ingest, scene handler, notif handler)
```

## Step-by-Step Flow

### 1. Edge → Hub (Ingest)

- Edge POSTs perception event to `POST /internal/v1/events`
- Hub validates envelope + payload_json
- Resolves `monitor_key → bed_id → resident_id` (Residency + Population services)
- Checks idempotency (`source_event_id` duplicate?)
- Persists raw event to `sensor_events`
- Publishes resolved perception to **NATS `evt_perception`**
- Returns `201 { id, resolved: true, duplicate: false }`

### 2. Engine (DigitalTwin FSM)

- Subscribes to **NATS `evt_perception`**
- Finds or creates `BedTwin` for `bed_id`
- Runs FSM transition: detects state change (e.g. lying → standing)
- Cancels timers for previous state, starts timers for new state
- Updates objects (walker, wheelchair, bed_rail)
- Generates `SceneEvent` (transition, dwell, or tick)
- Publishes to **NATS `evt_scene`**

### 3. Hub (Scene Handler)

- Subscribes to **NATS `evt_scene`**
- Persists `scene_event` to database
- Updates `current_bed_states` projection (state, state_since, sleeping, updated_at)

### 4. Sentinel (Rule Engine)

- Subscribes to **NATS `evt_scene`**
- Evaluates alarm rules via `RuleEngine`
- Resolves resident profile + shift (day: 07-19, night: 19-07)
- Creates incidents/alerts via Hub HTTP API (`POST /internal/v1/incidents`)
- Publishes notification to **NATS `evt_notif`**

### 5. Hub (Notification Handler)

- Subscribes to **NATS `evt_notif`**
- Persists `notification_event` to database

### 6. Vigilancia (Alert Delivery)

- Subscribes to **NATS `evt_notif`**
- Creates alert via Hub HTTP API (`POST /internal/v1/alerts`)
- Delivers to UI / push / external systems

### 7. Policy Changes (evt_policy)

- Hub publishes policy changes to **NATS `evt_policy`**
- Engine + Sentinel subscribe to update resident profiles and rule configurations

## Timer / Dwell Flow

```
02:10  Engine: perception → transition (lying → standing)
       Engine: start out_of_bed_dwell timer (10min)
       Engine: publish evt_scene (transition)

02:15  Engine: tick event, 5min elapsed
       Engine: publish evt_scene (tick)
       Sentinel: evaluates → no alarm (below threshold)

02:20  Engine: tick event, 10min elapsed
       Engine: publish evt_scene (dwell triggered)
       │
       ├→ Hub: persist scene_event
       └→ Sentinel: evaluates out_of_bed_dwell
           → night shift → alarm
           → creates incident via Hub API
           → publishes evt_notif
              ├→ Hub: persist notification_event
              └→ Vigilancia: create alert → UI
```

## Timer Cancellation Flow

```
02:10  Engine: transition lying → standing
       Engine: start out_of_bed_dwell (10min)

02:15  Engine: perception → transition standing → lying
       Engine: cancel out_of_bed_dwell
       Engine: start in_bed_dwell, sleep_dwell
       Engine: publish evt_scene (transition)
       Sentinel: evaluates → no alarm
```

## Responsibility Summary

| Binary | Responsibility |
|--------|---------------|
| **Hub** | HTTP ingest, resolution, persistence (raw + scene + notif), projections, policy publish |
| **Engine** | DigitalTwin FSM, state transitions, timer management, scene event generation |
| **Sentinel** | Alarm rule evaluation, incident creation, notification publishing |
| **Vigilancia** | Alert delivery to external systems and UI |

## Failure Modes

| Failure | Impact | Recovery |
|---------|--------|----------|
| Edge offline | No perception events | Edge reconnects, buffers locally |
| Hub down | No ingest, no persistence | Restart; NATS retains undelivered |
| Engine down | No scene events generated | Restart; reprocess from last offset |
| Sentinel down | No alarm evaluation | Restart; reprocess evt_scene |
| Vigilancia down | No alert delivery | Restart; reprocess evt_notif |
| NATS down | All message flow halts | NATS cluster failover; clients reconnect |
| DB down | No persistence possible | Hub rejects ingest; Sentinel retries |

## Metrics

| Metric | Description |
|--------|-------------|
| `perception_events_received` | Total perception events received by Hub |
| `perception_events_resolved` | Successfully resolved monitor_key → bed_id → resident_id |
| `scene_events_emitted` | Scene events published to evt_scene |
| `scene_events_persisted` | Scene events persisted by Hub |
| `alarms_evaluated` | Alarm rules evaluated by Sentinel |
| `incidents_created` | Incidents created via Hub API |
| `notifications_sent` | Notifications published to evt_notif |
| `alerts_delivered` | Alerts delivered by Vigilancia |
| `dwell_timers_triggered` | Dwell timers that completed |
| `dwell_timers_cancelled` | Dwell timers cancelled by state change |
| `processing_latency_ms` | Edge → notification latency |
| `nats_publish_errors` | Failed NATS publish attempts |
