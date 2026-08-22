# Domain Reference

Authoritative reference, reverse-engineered from `ctx-*` crates and `ownership.toml`.

## 11 Bounded Contexts

### ctx-identidad
**Purpose:** Who can log in and what capabilities the authenticated actor holds.
**Aggregates:** `User`, `Session`, `AuthenticatedActor`
**Tables:** `users`, `auth_sessions`
**Store methods:** `create_user`, `update_user`, `find_by_username`, `find_active_users`, `login`, `logout`, `find_session_by_token_hash`, `cleanup_expired_sessions`

### ctx-auditoria
**Purpose:** Immutable audit trail — what changed, who did it, on which record, when.
**Aggregates:** `AuditEntry`
**Tables:** `audit_log`
**Store methods:** `record` (append-only), `query_by_entity`, `query_by_actor`, `query_by_action`

### ctx-residencia
**Purpose:** Physical structure of the facility — wings, rooms, beds, cameras, detectors.
**Aggregates:** `Facility`, `Wing`, `Room`, `Bed`, `Planogram`, `RoomPrivacyConfig`
**Tables:** `facilities`, `wings`, `rooms`, `beds`, `planogram_placements`, `room_privacy_regions`
**Store methods:** CRUD for each aggregate, `find_active_*`, `list_by_parent`, `validate_facility`, `validate_bed`, `monitor_key_to_bed`

### ctx-poblacion
**Purpose:** Resident roster, admission lifecycle, and bed assignment history.
**Aggregates:** `Resident`, `BedAssignment`, `ResidentAttribute`
**Tables:** `residents`, `resident_bed_assignments`, `resident_attributes`
**Store methods:** `create_resident`, `discharge_resident`, `assign_bed`, `release_bed`, `list_active`, `current_assignment`, `attribute_history`

### ctx-cobertura
**Purpose:** Staff groups, membership history, shift grids, and wing coverage at an instant.
**Aggregates:** `StaffGroup`, `StaffGroupMembership`, `FacilityShift`, `WingCoverage`
**Tables:** `staff_groups`, `staff_group_members`, `facility_shifts`, `unit_shift_coverages`
**Store methods:** `create_group`, `replace_members`, `replace_grid`, `coverage_at`, `list_groups`

### ctx-cuidado
**Purpose:** Rounds, round tasks, and care notes for continuity.
**Aggregates:** `Round`, `RoundTask`, `CareNote`
**Tables:** `rounds`, `round_tasks`, `care_notes`
**Store methods:** `create_round`, `complete_round`, `complete_task`, `return_task`, `create_note`, `notes_for_resident`

### ctx-historia
**Purpose:** Clinical incident detections (immutable) and human reviews (append-only).
**Aggregates:** `IncidentDetection`, `IncidentReview`
**Tables:** `incident_detections`, `incident_reviews`
**Store methods:** `ingest_detection` (idempotent), `add_review`, `incident_with_reviews`, `list_for_resident`

### ctx-politica
**Purpose:** Resident alarm profiles, temporal versions, and the alarm rule catalog.
**Aggregates:** `AlarmProfileVersion`, `AlarmCatalog`
**Tables:** `alarm_profile_versions`
**Store methods:** `apply_profile`, `current_profile`, `profile_at`, `profile_history`, `load_catalog`

### ctx-vigilancia
**Purpose:** Alert lifecycle, transitions, notification delivery, and escalation history.
**Aggregates:** `Alert`, `AlertTransition`, `NotificationDelivery`, `NotificationDeliveryEvent`, `AlertEscalation`
**Tables:** `alerts`, `alert_transitions`, `notification_deliveries`, `notification_delivery_events`, `alert_escalations`
**Store methods:** `create_alert`, `transition_status`, `add_delivery`, `record_delivery_event`, `escalate`, `list_open`, `deliveries_for_alert`

### ctx-evidence
**Purpose:** Highlighted evidence events, temporal timelines, and clip windows for pattern detection.
**Aggregates:** `Evidence`, `Timeline`, `ClipWindow`
**Tables:** `evidence`, `timelines`, `clip_windows`
**Store methods:** `create_evidence`, `list_evidence`, `create_timeline`, `close_timeline`, `create_clip_window`, `close_clip_window`, `list_open_clip_windows`

### ctx-streams
**Purpose:** Camera streams and their polygonal regions of interest (ROI).
**Aggregates:** `Stream`, `StreamRegion`
**Tables:** `streams`, `stream_regions`
**Store methods:** `create_stream`, `list_streams`, `get_stream`, `list_regions`, `replace_regions`, `update_region`

### observacion (mana-observation)
**Purpose:** Sensor event ingestion, bed state projection, daily summaries, scene events, notification events.
**Aggregates:** `SensorEvent`, `BedState`, `SleepSummary`, `MobilitySummary`, `BathroomSummary`, `SceneEvent`, `NotificationEvent`
**Tables:** `sensor_events`, `current_bed_states`, `sleep_summaries`, `mobility_summaries`, `bathroom_summaries`, `scene_events`, `notification_events`
**Store methods:** `ingest` (idempotent), `current_state`, `bed_states`, `events_for_bed`, `ingest_sleep_summary`, `ingest_mobility_summary`, `ingest_bathroom_summary`, `resident_sleep`, `resident_mobility`, `resident_bathroom`, `unresolved_count`, `clear_projection_in_transaction`, `persist_scene_event`, `persist_notification_event`

## Cross-Context References

All inter-context references use **opaque IDs**, never direct SQL joins. `mana-app` resolves references through lookup ports at the application layer.

| Source | Reference | Target | Resolution |
|--------|-----------|--------|------------|
| ctx-poblacion | `bed_id` in assignments | ctx-residencia | `BedLookup` port |
| ctx-poblacion | `created_by` | ctx-identidad | `UserLookup` port |
| ctx-cobertura | `facility_id` | ctx-residencia | `FacilityLookup` port |
| ctx-cobertura | `wing_id` | ctx-residencia | `WingLookup` port |
| ctx-cobertura | `user_id` | ctx-identidad | `UserLookup` port |
| ctx-cuidado | `wing_id` | ctx-residencia | `WingLookup` port |
| ctx-cuidado | `resident_id`, `bed_id` | ctx-poblacion, ctx-residencia | Snapshot at round start |
| ctx-cuidado | `author_id` | ctx-identidad | `UserLookup` port |
| ctx-historia | `resident_id`, `bed_id` | ctx-poblacion, ctx-residencia | Lookup ports |
| ctx-historia | `source_alert_id` | ctx-vigilancia | Alert lookup port |
| ctx-historia | `actor_id` in reviews | ctx-identidad | `UserLookup` port |
| ctx-politica | `resident_id` | ctx-poblacion | Existence check |
| ctx-vigilancia | `resident_id`, `bed_id` | ctx-poblacion, ctx-residencia | Lookup ports |
| ctx-vigilancia | `status_actor_id`, `escalated_to` | ctx-identidad | `UserLookup` port |
| ctx-vigilancia | effective policy | ctx-politica | Read model from `mana-app` |
| ctx-vigilancia | staff coverage | ctx-cobertura | Read model from `mana-app` |
| ctx-evidence | `scene_event_id` | observacion | Scene event reference |
| ctx-streams | `room_id` | ctx-residencia | Room reference |
| observacion | `monitor_key` → `bed_id` | ctx-residencia | `MonitorBindingLookup` port |
| observacion | `resident_id` | ctx-poblacion | Resolved at ingestion time |

**Rule:** No `ctx-*` crate has a Cargo dependency on another `ctx-*` crate. All wiring happens in `mana-app`.
