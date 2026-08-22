# Data Model Reference

Authoritative reference for the Virtual Rounds data model. Reverse-engineered from all 12 Diesel `schema.rs` files and 17 migration SQL files.

**Database:** SQLite (WAL mode)
**Tables:** 34 across 12 contexts

---

## Overview

| Context | Tables | Description |
|---------|--------|-------------|
| ctx-identidad | 2 | Users and authentication sessions |
| ctx-auditoria | 1 | Immutable audit trail |
| ctx-residencia | 6 | Facility hierarchy and bed layout |
| ctx-poblacion | 3 | Residents and bed assignments |
| ctx-cobertura | 4 | Staff groups, shifts, and coverage |
| ctx-cuidado | 3 | Rounds, tasks, and care notes |
| ctx-historia | 2 | Incident detections and reviews |
| ctx-politica | 1 | Alarm profile versioning |
| ctx-vigilancia | 5 | Alerts, transitions, notifications, escalations |
| ctx-evidence | 3 | Evidence, timelines, and clip windows |
| ctx-streams | 2 | Live streams and spatial regions |
| mana-observation | 7 | Sensor events, bed states, scene events, summaries |

---

## ER Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-identidad                               │
│                                                                         │
│  ┌──────────────────────┐       ┌──────────────────────────┐           │
│  │       users           │◄──────│      auth_sessions        │           │
│  │──────────────────────│       │──────────────────────────│           │
│  │ PK id                │       │ PK token_hash            │           │
│  │    username  UQ      │       │ FK→users.user_id         │           │
│  │    display_name      │       │    expires_at            │           │
│  │    role (CHECK)      │       │    last_seen_at          │           │
│  │    job_title         │       └──────────────────────────┘           │
│  │    password_hash     │                                               │
│  │    retired_at/by     │                                               │
│  │    created_at        │                                               │
│  │    updated_at        │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-auditoria                               │
│                                                                         │
│  ┌──────────────────────┐                                               │
│  │      audit_log        │                                               │
│  │──────────────────────│                                               │
│  │ PK id                │                                               │
│  │    actor_id          │ ← opaque ref to users.id                     │
│  │    action            │                                               │
│  │    entity_type/id    │                                               │
│  │    metadata_json     │                                               │
│  │    created_at        │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-residencia                              │
│                                                                         │
│  ┌──────────────────────┐                                               │
│  │      facilities       │                                               │
│  │──────────────────────│                                               │
│  │ PK id                │                                               │
│  │    name, timezone    │                                               │
│  │    retired_at/by     │                                               │
│  │    created_at        │                                               │
│  │    updated_at        │                                               │
│  └──────────┬───────────┘                                               │
│             │ 1:N                                                       │
│  ┌──────────▼───────────┐     ┌──────────────────────────┐             │
│  │        wings          │     │   planogram_placements    │             │
│  │──────────────────────│◄────│──────────────────────────│             │
│  │ PK id                │     │ FK→wings, FK→rooms        │             │
│  │ FK→facilities        │     │    x, y, sort_order       │             │
│  │    name, floor       │     │    active                 │             │
│  │    sort_order        │     └──────────────────────────┘             │
│  │    retired_at/by     │                                               │
│  └──────────┬───────────┘     ┌──────────────────────────────┐         │
│             │ 1:N             │    room_privacy_regions       │         │
│  ┌──────────▼───────────┐     │──────────────────────────────│         │
│  │        rooms          │◄────│ FK→rooms, x/y/w/h, active   │         │
│  │──────────────────────│     └──────────────────────────────┘         │
│  │ PK id                │                                               │
│  │ FK→wings             │                                               │
│  │    number, room_type │                                               │
│  │    stream_key        │                                               │
│  │    retired_at/by     │                                               │
│  └──────────┬───────────┘                                               │
│             │ 1:N                                                       │
│  ┌──────────▼───────────┐                                               │
│  │        beds           │                                               │
│  │──────────────────────│                                               │
│  │ PK id                │                                               │
│  │ FK→rooms             │                                               │
│  │    label             │                                               │
│  │    monitor_key       │                                               │
│  │    retired_at/by     │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-poblacion                               │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────────┐         │
│  │      residents        │◄────│  resident_bed_assignments     │         │
│  │──────────────────────│     │──────────────────────────────│         │
│  │ PK id                │     │ PK id                        │         │
│  │    external_id  UQ   │     │ FK→residents, FK→beds        │         │
│  │    full_name         │     │    starts_at, ends_at        │         │
│  │    birth_date        │     │    created_by                │         │
│  │    admission_date    │     └──────────────────────────────┘         │
│  │    status (CHECK)    │                                               │
│  │    discharged_at/by  │     ┌──────────────────────────┐             │
│  └──────────────────────┘◄────│  resident_attributes      │             │
│                               │──────────────────────────│             │
│                               │ FK→residents             │             │
│                               │    code, value, source   │             │
│                               │    valid_from, valid_to  │             │
│                               └──────────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-cobertura                               │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────────┐         │
│  │     staff_groups      │◄────│    staff_group_members        │         │
│  │──────────────────────│     │──────────────────────────────│         │
│  │ PK id                │     │ FK→staff_groups              │         │
│  │ FK→facilities (oppq) │     │ FK→users (opaque)            │         │
│  │    name              │     │    valid_from, valid_to      │         │
│  │    retired_at/by     │     └──────────────────────────────┘         │
│  └──────────────────────┘                                               │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────────┐         │
│  │   facility_shifts     │     │   unit_shift_coverages       │         │
│  │──────────────────────│     │──────────────────────────────│         │
│  │ PK id                │     │ FK→staff_groups, wings       │         │
│  │ FK→facilities (oppq) │     │    shift_key                 │         │
│  │    key, label        │     │    valid_from, valid_to      │         │
│  │    start_minute      │     └──────────────────────────────┘         │
│  │    retired_at/by     │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                              ctx-cuidado                                │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────┐             │
│  │        rounds         │◄────│      round_tasks          │             │
│  │──────────────────────│     │──────────────────────────│             │
│  │ PK id                │     │ FK→rounds                 │             │
│  │ FK→wings (oppq)      │     │ FK→residents, beds (oppq) │             │
│  │    status (CHECK)    │     │    status (CHECK)         │             │
│  │    scheduled_for     │     │    note, completed_at/by  │             │
│  │    started_at        │     └──────────────────────────┘             │
│  │    completed_at      │                                               │
│  │    started_by        │     ┌──────────────────────────┐             │
│  │    completed_by      │     │      care_notes           │             │
│  └──────────────────────┘     │──────────────────────────│             │
│                               │ PK id                    │             │
│                               │ FK→residents, users(oppq)│             │
│                               │    kind, body            │             │
│                               │    duration_min          │             │
│                               └──────────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-historia                                │
│                                                                         │
│  ┌─────────────────────────────┐   ┌──────────────────────────┐       │
│  │    incident_detections       │◄──│    incident_reviews       │       │
│  │─────────────────────────────│   │──────────────────────────│       │
│  │ PK id                       │   │ FK→incidents             │       │
│  │    source_record_id   UQ    │   │    status                │       │
│  │ FK→residents, beds (oppq)   │   │    detection_verdict     │       │
│  │    kind, severity           │   │    review_note           │       │
│  │    occurred_at              │   │    resolved_at          │       │
│  │    narrative                │   │ FK→users (opaque)       │       │
│  │    interventions_json       │   └──────────────────────────┘       │
│  │    confidence               │                                       │
│  └─────────────────────────────┘                                       │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-politica                                │
│                                                                         │
│  ┌─────────────────────────────┐                                       │
│  │   alarm_profile_versions     │                                       │
│  │─────────────────────────────│                                       │
│  │ PK id                       │                                       │
│  │ FK→residents (opaque)       │                                       │
│  │    valid_from, valid_to     │                                       │
│  │    mobility_aid, autopilot  │                                       │
│  │    mode, template_id        │                                       │
│  │    overrides_json           │                                       │
│  │    risk_level               │                                       │
│  └─────────────────────────────┘                                       │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-vigilancia                              │
│                                                                         │
│  ┌──────────────────────┐                                               │
│  │        alerts         │                                               │
│  │──────────────────────│                                               │
│  │ PK id                │                                               │
│  │ FK→residents, beds   │                                               │
│  │    level, status     │                                               │
│  │    title, detail     │                                               │
│  │    escalation_level  │                                               │
│  └──────────┬───────────┘                                               │
│             │ 1:N  (three child tables)                                │
│  ┌──────────┼──────────────────────────────────┐                       │
│  │          │                  │                │                       │
│  ▼          ▼                  ▼                ▼                       │
│  ┌──────────────────┐ ┌───────────────────┐ ┌──────────────────┐      │
│  │ alert_transitions │ │notification_deliver│ │alert_escalations │      │
│  │──────────────────│ │───────────────────│ │──────────────────│      │
│  │ FK→alerts        │ │ FK→alerts         │ │ FK→alerts        │      │
│  │  from/to_status  │ │  recipient_kind/id│ │  level           │      │
│  │  actor_id        │ │  channel          │ │  target_id       │      │
│  │  sequence        │ └────────┬──────────┘ └──────────────────┘      │
│  └──────────────────┘          │ 1:N                                  │
│                                ▼                                      │
│                    ┌──────────────────────────┐                       │
│                    │notification_delivery_events│                       │
│                    │──────────────────────────│                       │
│                    │ FK→deliveries.delivery_id │                       │
│                    │  kind, reason            │                       │
│                    └──────────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-evidence                                │
│                                                                         │
│  ┌──────────────────────┐ ┌──────────────────────────┐ ┌────────────┐ │
│  │       evidence        │ │       timelines           │ │clip_windows │ │
│  │──────────────────────│ │──────────────────────────│ │────────────│ │
│  │ PK id                │ │ PK id                    │ │PK window_id│ │
│  │ FK→beds, residents   │ │ FK→beds, residents       │ │FK→beds     │ │
│  │    evidence_type     │ │    anchor_event_*        │ │FK→residents│ │
│  │    category          │ │    before/after_events   │ │  state     │ │
│  │    risk_level        │ │    window_start/end      │ │  events_   │ │
│  │    timestamp         │ │    closed_at             │ │    json    │ │
│  └──────────────────────┘ └──────────────────────────┘ └────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                             ctx-streams                                 │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────┐             │
│  │       streams         │◄────│     stream_regions         │             │
│  │──────────────────────│     │──────────────────────────│             │
│  │ PK id                │     │ FK→streams                │             │
│  │ FK→rooms             │     │    region_type (CHECK)    │             │
│  │    stream_key        │     │    points (JSON)          │             │
│  │    name, active      │     │    label, is_static       │             │
│  └──────────────────────┘     └──────────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                           mana-observation                              │
│                                                                         │
│  ┌──────────────────────┐     ┌──────────────────────────┐             │
│  │    sensor_events      │────►│   current_bed_states      │             │
│  │──────────────────────│     │──────────────────────────│             │
│  │ PK id                │     │ PK bed_id                │             │
│  │    monitor_key       │     │ FK→beds, residents (oppq)│             │
│  │ FK→beds, residents   │     │    room_state, state     │             │
│  │    kind, state       │     │    sleeping              │             │
│  │    sleeping          │     │    state_since           │             │
│  │    occurred_at       │     └──────────────────────────┘             │
│  └──────────────────────┘                                               │
│                                                                         │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌────────────────┐ │
│  │   scene_events        │ │ notification_events   │ │ sleep_summaries│ │
│  │──────────────────────│ │──────────────────────│ │────────────────│ │
│  │ PK id                │ │ PK id                │ │ PK id          │ │
│  │    event_id   UQ     │ │    category          │ │ source_record  │ │
│  │ FK→beds, residents   │ │ FK→beds, residents   │ │ FK→residents   │ │
│  │    event_type        │ │    event_type        │ │ observed_on    │ │
│  │    from/to_state     │ │    risk_level        │ │ calm/restless  │ │
│  └──────────────────────┘ └──────────────────────┘ └────────────────┘ │
│                                                                         │
│  ┌──────────────────────┐ ┌──────────────────────────┐                 │
│  │mobility_summaries     │ │   bathroom_summaries      │                 │
│  │──────────────────────│ │──────────────────────────│                 │
│  │ PK id                │ │ PK id                    │                 │
│  │    source_record UQ  │ │    source_record_id  UQ  │                 │
│  │ FK→residents         │ │ FK→residents             │                 │
│  │ observed_on          │ │    observed_on           │                 │
│  │ in/out_bed_minutes   │ │    visit_count           │                 │
│  │ walking, distance    │ │    night_visit_count     │                 │
│  │ transfer_count       │ │    assisted_count        │                 │
│  └──────────────────────┘ └──────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Complete DDL

### ctx-identidad

```sql
CREATE TABLE users (
    id            TEXT PRIMARY KEY,
    username      TEXT UNIQUE,
    display_name  TEXT,
    role          TEXT CHECK (role IN ('owner', 'supervisor', 'staff')),
    job_title     TEXT,
    password_hash TEXT,
    retired_at    TEXT,
    retired_by    TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE auth_sessions (
    token_hash   BLOB PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id),
    expires_at   TEXT NOT NULL,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    last_seen_at TEXT
);

CREATE INDEX auth_sessions_user_expiry_idx
    ON auth_sessions (user_id, expires_at);
```

### ctx-auditoria

```sql
CREATE TABLE audit_log (
    id            TEXT PRIMARY KEY,
    actor_id      TEXT,
    action        TEXT NOT NULL,
    entity_type   TEXT NOT NULL,
    entity_id     TEXT NOT NULL,
    metadata_json TEXT DEFAULT '{}',
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX audit_log_entity_time_idx
    ON audit_log (entity_type, entity_id, created_at DESC);

CREATE INDEX audit_log_actor_time_idx
    ON audit_log (actor_id, created_at DESC);

CREATE INDEX audit_log_action_time_idx
    ON audit_log (action, created_at DESC);
```

### ctx-residencia

```sql
CREATE TABLE facilities (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    timezone   TEXT NOT NULL DEFAULT 'UTC',
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE wings (
    id          TEXT PRIMARY KEY,
    facility_id TEXT NOT NULL REFERENCES facilities(id),
    name        TEXT NOT NULL,
    floor       TEXT,
    sort_order  INTEGER DEFAULT 0,
    retired_at  TEXT,
    retired_by  TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE rooms (
    id         TEXT PRIMARY KEY,
    wing_id    TEXT NOT NULL REFERENCES wings(id),
    number     TEXT NOT NULL,
    room_type  TEXT,
    stream_key TEXT,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX rooms_active_number_idx
    ON rooms (wing_id, number)
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX rooms_active_stream_idx
    ON rooms (stream_key)
    WHERE stream_key IS NOT NULL AND retired_at IS NULL;

CREATE TABLE beds (
    id          TEXT PRIMARY KEY,
    room_id     TEXT NOT NULL REFERENCES rooms(id),
    label       TEXT NOT NULL,
    monitor_key TEXT,
    retired_at  TEXT,
    retired_by  TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX beds_active_monitor_idx
    ON beds (monitor_key)
    WHERE monitor_key IS NOT NULL AND retired_at IS NULL;

CREATE TABLE planogram_placements (
    id         TEXT PRIMARY KEY,
    wing_id    TEXT NOT NULL REFERENCES wings(id),
    room_id    TEXT NOT NULL REFERENCES rooms(id),
    x          REAL NOT NULL,
    y          REAL NOT NULL,
    sort_order INTEGER DEFAULT 0,
    active     INTEGER DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX planogram_active_room_idx
    ON planogram_placements (room_id)
    WHERE active = 1;

CREATE TABLE room_privacy_regions (
    id         TEXT PRIMARY KEY,
    room_id    TEXT NOT NULL REFERENCES rooms(id),
    x          REAL NOT NULL,
    y          REAL NOT NULL,
    w          REAL NOT NULL,
    h          REAL NOT NULL,
    active     INTEGER DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### ctx-poblacion

```sql
CREATE TABLE residents (
    id             TEXT PRIMARY KEY,
    external_id    TEXT UNIQUE,
    full_name      TEXT NOT NULL,
    birth_date     TEXT,
    admission_date TEXT NOT NULL,
    status         TEXT DEFAULT 'active' CHECK (status IN ('active', 'discharged')),
    discharged_at  TEXT,
    discharged_by  TEXT,
    created_at     TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE resident_bed_assignments (
    rowid       INTEGER PRIMARY KEY,
    id          TEXT NOT NULL UNIQUE,
    resident_id TEXT NOT NULL REFERENCES residents(id),
    bed_id      TEXT NOT NULL,
    starts_at   TEXT NOT NULL,
    ends_at     TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    created_by  TEXT
);

CREATE UNIQUE INDEX residents_open_assignment_idx
    ON resident_bed_assignments (resident_id)
    WHERE ends_at IS NULL;

CREATE UNIQUE INDEX beds_open_assignment_idx
    ON resident_bed_assignments (bed_id)
    WHERE ends_at IS NULL;

CREATE TABLE resident_attributes (
    id          TEXT PRIMARY KEY,
    resident_id TEXT NOT NULL REFERENCES residents(id),
    code        TEXT NOT NULL,
    value       TEXT,
    source      TEXT,
    source_ref  TEXT,
    recorded_by TEXT,
    recorded_at TEXT NOT NULL DEFAULT (datetime('now')),
    valid_from  TEXT NOT NULL DEFAULT (datetime('now')),
    valid_to    TEXT
);
```

### ctx-cobertura

```sql
CREATE TABLE staff_groups (
    id          TEXT PRIMARY KEY,
    facility_id TEXT NOT NULL,
    name        TEXT NOT NULL,
    retired_at  TEXT,
    retired_by  TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX staff_groups_facility_name_idx
    ON staff_groups (facility_id, name)
    WHERE retired_at IS NULL;

CREATE TABLE staff_group_members (
    id             TEXT PRIMARY KEY,
    staff_group_id TEXT NOT NULL REFERENCES staff_groups(id),
    user_id        TEXT NOT NULL,
    valid_from     TEXT NOT NULL DEFAULT (datetime('now')),
    valid_to       TEXT,
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX staff_group_members_user_group_valid_idx
    ON staff_group_members (user_id, staff_group_id)
    WHERE valid_to IS NULL;

CREATE TABLE facility_shifts (
    id           TEXT PRIMARY KEY,
    facility_id  TEXT NOT NULL,
    key          TEXT NOT NULL,
    label        TEXT NOT NULL,
    start_minute INTEGER NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    sort_order   INTEGER DEFAULT 0,
    retired_at   TEXT,
    retired_by   TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX facility_shifts_facility_key_idx
    ON facility_shifts (facility_id, key)
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX facility_shifts_facility_minute_idx
    ON facility_shifts (facility_id, start_minute)
    WHERE retired_at IS NULL;

CREATE TABLE unit_shift_coverages (
    id             TEXT PRIMARY KEY,
    wing_id        TEXT NOT NULL,
    staff_group_id TEXT NOT NULL,
    shift_key      TEXT NOT NULL,
    valid_from     TEXT NOT NULL DEFAULT (datetime('now')),
    valid_to       TEXT,
    created_at     TEXT NOT NULL DEFAULT (datetime('now')),
    created_by     TEXT
);

CREATE UNIQUE INDEX coverage_wing_shift_valid_idx
    ON unit_shift_coverages (wing_id, shift_key)
    WHERE valid_to IS NULL;
```

### ctx-cuidado

```sql
CREATE TABLE rounds (
    id            TEXT PRIMARY KEY,
    wing_id       TEXT NOT NULL,
    status        TEXT DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed', 'cancelled')),
    scheduled_for TEXT,
    started_at    TEXT,
    completed_at  TEXT,
    started_by    TEXT,
    completed_by  TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX rounds_wing_in_progress_idx
    ON rounds (wing_id)
    WHERE status = 'in_progress';

CREATE TABLE round_tasks (
    id           TEXT PRIMARY KEY,
    round_id     TEXT NOT NULL REFERENCES rounds(id),
    resident_id  TEXT NOT NULL,
    bed_id       TEXT,
    status       TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'completed')),
    note         TEXT,
    completed_at TEXT,
    completed_by TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE care_notes (
    id           TEXT PRIMARY KEY,
    resident_id  TEXT NOT NULL,
    author_id    TEXT NOT NULL,
    kind         TEXT DEFAULT 'general',
    body         TEXT NOT NULL,
    duration_min INTEGER,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### ctx-historia

```sql
CREATE TABLE incident_detections (
    id                 TEXT PRIMARY KEY,
    source_record_id   TEXT UNIQUE NOT NULL,
    resident_id        TEXT NOT NULL,
    bed_id             TEXT,
    source_alert_id    TEXT,
    kind               TEXT NOT NULL,
    severity           TEXT NOT NULL,
    occurred_at        TEXT NOT NULL,
    location           TEXT,
    activity           TEXT,
    injury_status      TEXT,
    self_recovery      INTEGER DEFAULT 0,
    response_seconds   INTEGER,
    narrative          TEXT,
    interventions_json TEXT DEFAULT '[]',
    source             TEXT NOT NULL,
    model_version      TEXT,
    confidence         REAL,
    provenance_json    TEXT DEFAULT '{}',
    created_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE incident_reviews (
    rowid             INTEGER PRIMARY KEY,
    id                TEXT NOT NULL UNIQUE,
    incident_id       TEXT NOT NULL,
    status            TEXT NOT NULL,
    detection_verdict TEXT,
    review_note       TEXT,
    resolved_at       TEXT,
    actor_id          TEXT NOT NULL,
    created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### ctx-politica

```sql
CREATE TABLE alarm_profile_versions (
    id              TEXT PRIMARY KEY,
    resident_id     TEXT NOT NULL,
    valid_from      TEXT NOT NULL DEFAULT (datetime('now')),
    valid_to        TEXT,
    mobility_aid    TEXT,
    autopilot       INTEGER DEFAULT 0,
    mode            TEXT,
    template_id     TEXT,
    overrides_json  TEXT DEFAULT '{}',
    catalog_version TEXT,
    updated_by      TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    risk_level      TEXT DEFAULT 'medium'
);

CREATE UNIQUE INDEX idx_alarm_profiles_one_current
    ON alarm_profile_versions (resident_id)
    WHERE valid_to IS NULL;
```

### ctx-vigilancia

```sql
CREATE TABLE alerts (
    id               TEXT PRIMARY KEY,
    resident_id      TEXT NOT NULL,
    bed_id           TEXT,
    evidence_kind    TEXT,
    evidence_ref     TEXT,
    rule_id          TEXT,
    level            TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'pending',
    status_actor_id  TEXT,
    status_at        TEXT,
    title            TEXT,
    detail           TEXT,
    occurred_at      TEXT NOT NULL,
    escalation_level INTEGER DEFAULT 0,
    escalated_at     TEXT,
    escalated_to     TEXT,
    created_at       TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE alert_transitions (
    id          TEXT PRIMARY KEY,
    alert_id    TEXT NOT NULL REFERENCES alerts(id),
    from_status TEXT,
    to_status   TEXT NOT NULL,
    actor_id    TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    sequence    INTEGER NOT NULL
);

CREATE TABLE notification_deliveries (
    id               TEXT PRIMARY KEY,
    alert_id         TEXT NOT NULL REFERENCES alerts(id),
    recipient_kind   TEXT NOT NULL,
    recipient_id     TEXT NOT NULL,
    channel          TEXT NOT NULL,
    escalation_level INTEGER DEFAULT 0,
    created_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE notification_delivery_events (
    id          TEXT PRIMARY KEY,
    delivery_id TEXT NOT NULL REFERENCES notification_deliveries(id),
    kind        TEXT NOT NULL,
    reason      TEXT,
    occurred_at TEXT NOT NULL
);

CREATE TABLE alert_escalations (
    id          TEXT PRIMARY KEY,
    alert_id    TEXT NOT NULL REFERENCES alerts(id),
    level       INTEGER NOT NULL,
    target_id   TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### ctx-evidence

```sql
CREATE TABLE evidence (
    id               TEXT PRIMARY KEY,
    bed_id           TEXT NOT NULL,
    resident_id      TEXT NOT NULL,
    evidence_type    TEXT NOT NULL,
    category         TEXT,
    scene_event_id   TEXT,
    scene_event_json TEXT,
    rule_id          TEXT,
    shift            TEXT,
    risk_level       TEXT,
    timestamp        TEXT NOT NULL,
    created_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE timelines (
    id                 TEXT PRIMARY KEY,
    bed_id             TEXT NOT NULL,
    resident_id        TEXT NOT NULL,
    anchor_event_id    TEXT,
    anchor_event_json  TEXT,
    before_events_json TEXT DEFAULT '[]',
    after_events_json  TEXT DEFAULT '[]',
    window_start       TEXT NOT NULL,
    window_end         TEXT,
    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
    closed_at          TEXT
);

CREATE TABLE clip_windows (
    window_id            TEXT PRIMARY KEY,
    bed_id               TEXT NOT NULL,
    resident_id          TEXT NOT NULL,
    started_at           TEXT NOT NULL,
    ended_at             TEXT,
    timeout_minutes      INTEGER DEFAULT 5,
    events_json          TEXT DEFAULT '[]',
    state                TEXT DEFAULT 'open',
    close_condition_json TEXT,
    created_at           TEXT NOT NULL DEFAULT (datetime('now')),
    closed_at            TEXT
);
```

### ctx-streams

```sql
CREATE TABLE streams (
    id         TEXT PRIMARY KEY,
    room_id    TEXT NOT NULL,
    stream_key TEXT NOT NULL,
    name       TEXT,
    active     INTEGER DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX streams_active_room_key_idx
    ON streams (room_id, stream_key)
    WHERE active = 1;

CREATE TABLE stream_regions (
    id          TEXT PRIMARY KEY,
    stream_id   TEXT NOT NULL REFERENCES streams(id),
    region_type TEXT NOT NULL CHECK (region_type IN (
        'bathroom', 'hallway', 'exit', 'bed', 'furniture', 'person', 'object'
    )),
    points      TEXT NOT NULL,
    label       TEXT,
    is_static   INTEGER DEFAULT 1,
    updated_by  TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### mana-observation

```sql
CREATE TABLE sensor_events (
    id              TEXT PRIMARY KEY,
    source_event_id TEXT UNIQUE NOT NULL,
    monitor_key     TEXT NOT NULL,
    bed_id          TEXT,
    resident_id     TEXT,
    kind            TEXT NOT NULL,
    room_state      TEXT,
    substate        TEXT,
    zone            TEXT,
    state           TEXT,
    sleeping        INTEGER,
    occurred_at     TEXT NOT NULL,
    received_at     TEXT NOT NULL DEFAULT (datetime('now')),
    payload_json    TEXT DEFAULT '{}'
);

CREATE INDEX idx_sensor_events_unresolved
    ON sensor_events (monitor_key)
    WHERE bed_id IS NULL;

CREATE TABLE current_bed_states (
    bed_id          TEXT PRIMARY KEY,
    resident_id     TEXT,
    room_state      TEXT,
    state           TEXT,
    substate        TEXT,
    sleeping        INTEGER,
    state_since     TEXT NOT NULL,
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    source          TEXT,
    source_event_id TEXT
);

CREATE TABLE scene_events (
    id           TEXT PRIMARY KEY,
    event_id     TEXT UNIQUE NOT NULL,
    bed_id       TEXT NOT NULL,
    resident_id  TEXT,
    event_type   TEXT NOT NULL,
    from_state   TEXT,
    to_state     TEXT,
    trigger_type TEXT,
    timestamp    TEXT NOT NULL,
    payload_json TEXT DEFAULT '{}',
    received_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE notification_events (
    id           TEXT PRIMARY KEY,
    category     TEXT NOT NULL,
    bed_id       TEXT,
    resident_id  TEXT,
    event_type   TEXT NOT NULL,
    timestamp    TEXT NOT NULL,
    rule_id      TEXT,
    risk_level   TEXT,
    payload_json TEXT DEFAULT '{}',
    received_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE sleep_summaries (
    id                 TEXT PRIMARY KEY,
    source_record_id   TEXT UNIQUE NOT NULL,
    resident_id        TEXT NOT NULL,
    observed_on        TEXT NOT NULL,
    calm_minutes       INTEGER DEFAULT 0,
    restless_minutes   INTEGER DEFAULT 0,
    awake_minutes      INTEGER DEFAULT 0,
    out_of_bed_minutes INTEGER DEFAULT 0,
    bed_exit_count     INTEGER DEFAULT 0,
    wake_count         INTEGER DEFAULT 0,
    source             TEXT,
    model_version      TEXT,
    confidence         REAL,
    provenance_json    TEXT DEFAULT '{}',
    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at         TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX idx_sleep_summaries_resident_day
    ON sleep_summaries (resident_id, observed_on);

CREATE TABLE mobility_summaries (
    id                   TEXT PRIMARY KEY,
    source_record_id     TEXT UNIQUE NOT NULL,
    resident_id          TEXT NOT NULL,
    observed_on          TEXT NOT NULL,
    in_bed_minutes       INTEGER DEFAULT 0,
    out_of_bed_minutes   INTEGER DEFAULT 0,
    out_of_sight_minutes INTEGER DEFAULT 0,
    walking_minutes      INTEGER DEFAULT 0,
    distance_meters      REAL DEFAULT 0,
    transfer_count       INTEGER DEFAULT 0,
    source               TEXT,
    model_version        TEXT,
    confidence           REAL,
    provenance_json      TEXT DEFAULT '{}',
    created_at           TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at           TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX idx_mobility_summaries_resident_day
    ON mobility_summaries (resident_id, observed_on);

CREATE TABLE bathroom_summaries (
    id                    TEXT PRIMARY KEY,
    source_record_id      TEXT UNIQUE NOT NULL,
    resident_id           TEXT NOT NULL,
    observed_on           TEXT NOT NULL,
    visit_count           INTEGER DEFAULT 0,
    night_visit_count     INTEGER DEFAULT 0,
    assisted_count        INTEGER DEFAULT 0,
    total_minutes         INTEGER DEFAULT 0,
    longest_visit_minutes INTEGER DEFAULT 0,
    source                TEXT,
    model_version         TEXT,
    confidence            REAL,
    provenance_json       TEXT DEFAULT '{}',
    created_at            TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at            TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX idx_bathroom_summaries_resident_day
    ON bathroom_summaries (resident_id, observed_on);
```

---

## Indexes Summary

| Index Name | Table | Columns | Partial Condition |
|------------|-------|---------|-------------------|
| `auth_sessions_user_expiry_idx` | auth_sessions | user_id, expires_at | — |
| `audit_log_entity_time_idx` | audit_log | entity_type, entity_id, created_at DESC | — |
| `audit_log_actor_time_idx` | audit_log | actor_id, created_at DESC | — |
| `audit_log_action_time_idx` | audit_log | action, created_at DESC | — |
| `rooms_active_number_idx` | rooms | wing_id, number | WHERE retired_at IS NULL |
| `rooms_active_stream_idx` | rooms | stream_key | WHERE stream_key IS NOT NULL AND retired_at IS NULL |
| `beds_active_monitor_idx` | beds | monitor_key | WHERE monitor_key IS NOT NULL AND retired_at IS NULL |
| `planogram_active_room_idx` | planogram_placements | room_id | WHERE active = 1 |
| `residents_open_assignment_idx` | resident_bed_assignments | resident_id | WHERE ends_at IS NULL |
| `beds_open_assignment_idx` | resident_bed_assignments | bed_id | WHERE ends_at IS NULL |
| `staff_groups_facility_name_idx` | staff_groups | facility_id, name | WHERE retired_at IS NULL |
| `staff_group_members_user_group_valid_idx` | staff_group_members | user_id, staff_group_id | WHERE valid_to IS NULL |
| `facility_shifts_facility_key_idx` | facility_shifts | facility_id, key | WHERE retired_at IS NULL |
| `facility_shifts_facility_minute_idx` | facility_shifts | facility_id, start_minute | WHERE retired_at IS NULL |
| `coverage_wing_shift_valid_idx` | unit_shift_coverages | wing_id, shift_key | WHERE valid_to IS NULL |
| `rounds_wing_in_progress_idx` | rounds | wing_id | WHERE status = 'in_progress' |
| `idx_alarm_profiles_one_current` | alarm_profile_versions | resident_id | WHERE valid_to IS NULL |
| `idx_sensor_events_unresolved` | sensor_events | monitor_key | WHERE bed_id IS NULL |
| `streams_active_room_key_idx` | streams | room_id, stream_key | WHERE active = 1 |
| `idx_sleep_summaries_resident_day` | sleep_summaries | resident_id, observed_on | — |
| `idx_mobility_summaries_resident_day` | mobility_summaries | resident_id, observed_on | — |
| `idx_bathroom_summaries_resident_day` | bathroom_summaries | resident_id, observed_on | — |

---

## Cross-Context References

Tables in different contexts reference each other via opaque TEXT IDs. There are no formal `FOREIGN KEY` constraints across context boundaries. The application layer enforces referential integrity.

| Source Context | Source Table | Column | Target Context | Target Table |
|---------------|--------------|--------|---------------|--------------|
| ctx-auditoria | audit_log | actor_id | ctx-identidad | users.id |
| ctx-cobertura | staff_group_members | user_id | ctx-identidad | users.id |
| ctx-cobertura | staff_groups | facility_id | ctx-residencia | facilities.id |
| ctx-cobertura | facility_shifts | facility_id | ctx-residencia | facilities.id |
| ctx-cobertura | unit_shift_coverages | wing_id | ctx-residencia | wings.id |
| ctx-cobertura | unit_shift_coverages | staff_group_id | ctx-cobertura | staff_groups.id |
| ctx-cuidado | rounds | wing_id | ctx-residencia | wings.id |
| ctx-cuidado | round_tasks | round_id | ctx-cuidado | rounds.id |
| ctx-cuidado | round_tasks | resident_id | ctx-poblacion | residents.id |
| ctx-cuidado | round_tasks | bed_id | ctx-residencia | beds.id |
| ctx-cuidado | care_notes | resident_id | ctx-poblacion | residents.id |
| ctx-cuidado | care_notes | author_id | ctx-identidad | users.id |
| ctx-historia | incident_detections | resident_id | ctx-poblacion | residents.id |
| ctx-historia | incident_detections | bed_id | ctx-residencia | beds.id |
| ctx-historia | incident_detections | source_alert_id | ctx-vigilancia | alerts.id |
| ctx-historia | incident_reviews | incident_id | ctx-historia | incident_detections.id |
| ctx-historia | incident_reviews | actor_id | ctx-identidad | users.id |
| ctx-politica | alarm_profile_versions | resident_id | ctx-poblacion | residents.id |
| ctx-politica | alarm_profile_versions | updated_by | ctx-identidad | users.id |
| ctx-vigilancia | alerts | resident_id | ctx-poblacion | residents.id |
| ctx-vigilancia | alerts | bed_id | ctx-residencia | beds.id |
| ctx-vigilancia | alert_transitions | alert_id | ctx-vigilancia | alerts.id |
| ctx-vigilancia | alert_transitions | actor_id | ctx-identidad | users.id |
| ctx-vigilancia | notification_deliveries | alert_id | ctx-vigilancia | alerts.id |
| ctx-vigilancia | notification_deliveries | recipient_id | ctx-identidad | users.id |
| ctx-vigilancia | alert_escalations | alert_id | ctx-vigilancia | alerts.id |
| ctx-vigilancia | alert_escalations | target_id | ctx-identidad | users.id |
| ctx-evidence | evidence | bed_id | ctx-residencia | beds.id |
| ctx-evidence | evidence | resident_id | ctx-poblacion | residents.id |
| ctx-evidence | timelines | bed_id | ctx-residencia | beds.id |
| ctx-evidence | timelines | resident_id | ctx-poblacion | residents.id |
| ctx-evidence | clip_windows | bed_id | ctx-residencia | beds.id |
| ctx-evidence | clip_windows | resident_id | ctx-poblacion | residents.id |
| ctx-streams | streams | room_id | ctx-residencia | rooms.id |
| ctx-streams | stream_regions | stream_id | ctx-streams | streams.id |
| mana-observation | sensor_events | bed_id | ctx-residencia | beds.id |
| mana-observation | sensor_events | resident_id | ctx-poblacion | residents.id |
| mana-observation | current_bed_states | bed_id | ctx-residencia | beds.id |
| mana-observation | current_bed_states | resident_id | ctx-poblacion | residents.id |
| mana-observation | scene_events | bed_id | ctx-residencia | beds.id |
| mana-observation | scene_events | resident_id | ctx-poblacion | residents.id |
| mana-observation | notification_events | bed_id | ctx-residencia | beds.id |
| mana-observation | notification_events | resident_id | ctx-poblacion | residents.id |
| mana-observation | sleep_summaries | resident_id | ctx-poblacion | residents.id |
| mana-observation | mobility_summaries | resident_id | ctx-poblacion | residents.id |
| mana-observation | bathroom_summaries | resident_id | ctx-poblacion | residents.id |

---

## Design Patterns

### Soft Delete Pattern

Used for entities that can be logically removed without losing historical data. Columns `retired_at` (timestamp of deactivation) and `retired_by` (user ID who retired the entity) are set together; when both are NULL the entity is active.

**Tables using this pattern:**

| Table | retired_at | retired_by |
|-------|-----------|-----------|
| users | ✓ | ✓ |
| facilities | ✓ | ✓ |
| wings | ✓ | ✓ |
| rooms | ✓ | ✓ |
| beds | ✓ | ✓ |
| staff_groups | ✓ | ✓ |
| facility_shifts | ✓ | ✓ |

**Partial unique indexes enforce single-active constraints:**
- `rooms_active_number_idx` — one active room number per wing
- `rooms_active_stream_idx` — one active room per stream key
- `beds_active_monitor_idx` — one active bed per monitor key
- `staff_groups_facility_name_idx` — one active group name per facility
- `facility_shifts_facility_key_idx` — one active shift key per facility
- `facility_shifts_facility_minute_idx` — one active shift per start minute per facility

---

### Temporal Pattern (Bi-temporal Versioning)

Used for data that has a business-valid time range distinct from row-creation time. Columns `valid_from` and `valid_to` define the business validity window; `valid_to = NULL` marks the current version.

**Tables using this pattern:**

| Table | valid_from | valid_to | Single-Active Index |
|-------|-----------|----------|---------------------|
| resident_attributes | ✓ | ✓ | — |
| staff_group_members | ✓ | ✓ | `staff_group_members_user_group_valid_idx` WHERE valid_to IS NULL |
| unit_shift_coverages | ✓ | ✓ | `coverage_wing_shift_valid_idx` WHERE valid_to IS NULL |
| alarm_profile_versions | ✓ | ✓ | `idx_alarm_profiles_one_current` WHERE valid_to IS NULL |
| resident_bed_assignments | starts_at / ends_at | | `residents_open_assignment_idx`, `beds_open_assignment_idx` WHERE ends_at IS NULL |

**Key constraint:** Only one row per entity may have `valid_to IS NULL` (enforced by partial unique indexes). This prevents multiple "current" versions from coexisting.

---

### Append-Only Pattern

Immutable tables that grow monotonically. Rows are never updated or deleted; new rows represent new events or versions.

**Tables using this pattern:**

| Table | Key Column | Description |
|-------|-----------|-------------|
| audit_log | id | Every state-change event is recorded once |
| care_notes | id | Clinical notes are never edited, only appended |
| evidence | id | Sensor-derived evidence records |
| sensor_events | id | Raw sensor readings |
| scene_events | id | State transition events |
| notification_events | id | Notification dispatch records |
| sleep_summaries | id | Daily sleep summaries (one per resident per day) |
| mobility_summaries | id | Daily mobility summaries |
| bathroom_summaries | id | Daily bathroom summaries |
| incident_detections | id | AI-detected incidents |
| incident_reviews | id | Human reviews of detections |

**Characteristics:**
- `created_at` is set on insert and never modified
- No `updated_at` column (the row never changes)
- `source_record_id` UNIQUE ensures idempotent ingestion
- Daily summaries use composite unique indexes `(resident_id, observed_on)` for natural deduplication

---

### Check Constraint Summary

| Table | Column | Constraint |
|-------|--------|-----------|
| users | role | IN ('owner', 'supervisor', 'staff') |
| residents | status | IN ('active', 'discharged') |
| facility_shifts | start_minute | BETWEEN 0 AND 1439 |
| rounds | status | IN ('in_progress', 'completed', 'cancelled') |
| round_tasks | status | IN ('pending', 'completed') |
| stream_regions | region_type | IN ('bathroom', 'hallway', 'exit', 'bed', 'furniture', 'person', 'object') |

---

### DEFAULT Values

| Pattern | Default | Used In |
|---------|---------|---------|
| Timestamps | `datetime('now')` | All `created_at` and `updated_at` columns |
| JSON objects | `'{}'` | metadata_json, overrides_json, provenance_json, payload_json |
| JSON arrays | `'[]'` | interventions_json, before_events_json, after_events_json, events_json |
| Status enums | Context-specific | alerts.status, rounds.status, round_tasks.status, residents.status |
| Booleans (SQLite) | `0` or `1` | autopilot, active, sleeping, self_recovery, is_static |
| Risk levels | `'medium'` | alarm_profile_versions.risk_level |
| Timezone | `'UTC'` | facilities.timezone |
| Clip timeout | `5` (minutes) | clip_windows.timeout_minutes |

---

## Table Count by Context

```
ctx-identidad      ██ (2)
ctx-auditoria      █ (1)
ctx-residencia     ██████ (6)
ctx-poblacion      ███ (3)
ctx-cobertura      ████ (4)
ctx-cuidado        ███ (3)
ctx-historia       ██ (2)
ctx-politica       █ (1)
ctx-vigilancia     █████ (5)
ctx-evidence       ███ (3)
ctx-streams        ██ (2)
mana-observation   ███████ (7)
                   ─────────
                   34 total
```
