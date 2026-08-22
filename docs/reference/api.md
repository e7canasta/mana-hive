# API Reference

> Reverse-engineered from `rutas.toml`. All 96 endpoints are served by Rust
> handlers via a compile-time route table. Node has been completely removed.

| Prefix | Audience |
|--------|----------|
| `/api/v1/*` | Client applications |
| `/internal/v1/*` | Machine-to-machine |

---

## 1. Platform (1)

| Method | Path | Route ID |
|--------|------|----------|
| OPTIONS | `*` | `cors.preflight.options` |

## 2. Identidad (6)

| Method | Path | Route ID |
|--------|------|----------|
| POST | `/api/v1/auth/login` | `auth.login.post` |
| GET | `/api/v1/auth/me` | `auth.me.get` |
| POST | `/api/v1/auth/logout` | `auth.logout.post` |
| GET | `/api/v1/users` | `users.list.get` |
| POST | `/api/v1/users` | `users.create.post` |
| PATCH | `/api/v1/users/:userId` | `users.update.patch` |

## 3. Auditoria (1)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/audit-log` | `audit-log.list.get` |

## 4. Residencia (18)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/facilities` | `facilities.list.get` |
| POST | `/api/v1/facilities` | `facilities.create.post` |
| GET | `/api/v1/facilities/:facilityId` | `facilities.detail.get` |
| PATCH | `/api/v1/facilities/:facilityId` | `facilities.update.patch` |
| GET | `/api/v1/facilities/:facilityId/tree` | `facilities.tree.get` |
| GET | `/api/v1/wings` | `wings.list.get` |
| POST | `/api/v1/facilities/:facilityId/wings` | `facilities.wings.create.post` |
| PATCH | `/api/v1/wings/:wingId` | `wings.update.patch` |
| GET | `/api/v1/wings/:wingId/rooms` | `wings.rooms.get` |
| POST | `/api/v1/wings/:wingId/rooms` | `wings.rooms.create.post` |
| GET | `/api/v1/wings/:wingId/planogram` | `wings.planogram.get` |
| PUT | `/api/v1/wings/:wingId/planogram` | `wings.planogram.put` |
| GET | `/api/v1/rooms/:roomId/beds` | `rooms.beds.get` |
| POST | `/api/v1/rooms/:roomId/beds` | `rooms.beds.create.post` |
| PATCH | `/api/v1/rooms/:roomId` | `rooms.update.patch` |
| GET | `/api/v1/beds` | `beds.list.get` |
| PATCH | `/api/v1/beds/:bedId` | `beds.update.patch` |
| GET | `/api/v1/rooms/:roomId/privacy-regions` | `rooms.privacy-regions.get` |
| PUT | `/api/v1/rooms/:roomId/privacy-regions` | `rooms.privacy-regions.put` |

## 5. Poblacion (8)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/residents` | `residents.list.get` |
| POST | `/api/v1/residents` | `residents.create.post` |
| GET | `/api/v1/residents/:residentId` | `residents.detail.get` |
| PATCH | `/api/v1/residents/:residentId` | `residents.update.patch` |
| POST | `/api/v1/residents/:residentId/discharge` | `residents.discharge.post` |
| GET | `/api/v1/residents/:residentId/assignments` | `residents.assignments.get` |
| POST | `/api/v1/residents/:residentId/assignments` | `residents.assignments.create.post` |
| DELETE | `/api/v1/beds/:bedId/assignment` | `beds.assignment.delete` |

## 6. Cobertura (8)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/facilities/:facilityId/shifts` | `facilities.shifts.get` |
| PUT | `/api/v1/facilities/:facilityId/shifts` | `facilities.shifts.put` |
| GET | `/api/v1/wings/:wingId/coverage` | `wings.coverage.get` |
| PUT | `/api/v1/wings/:wingId/coverage` | `wings.coverage.put` |
| GET | `/api/v1/staff-groups` | `staff-groups.list.get` |
| GET | `/api/v1/staff-groups/:groupId` | `staff-groups.detail.get` |
| POST | `/api/v1/staff-groups` | `staff-groups.create.post` |
| PATCH | `/api/v1/staff-groups/:groupId` | `staff-groups.update.patch` |
| PUT | `/api/v1/staff-groups/:groupId/members` | `staff-groups.members.put` |

## 7. Cuidado (9)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/rounds/current` | `rounds.current.get` |
| GET | `/api/v1/rounds` | `rounds.list.get` |
| POST | `/api/v1/rounds` | `rounds.create.post` |
| GET | `/api/v1/rounds/:roundId` | `rounds.detail.get` |
| PATCH | `/api/v1/rounds/:roundId` | `rounds.update.patch` |
| PATCH | `/api/v1/round-tasks/:taskId` | `round-tasks.update.patch` |
| GET | `/api/v1/residents/:residentId/care` | `residents.care.get` |
| GET | `/api/v1/residents/:residentId/notes` | `residents.notes.get` |
| POST | `/api/v1/residents/:residentId/notes` | `residents.notes.create.post` |

## 8. Historia (5)

| Method | Path | Route ID |
|--------|------|----------|
| POST | `/internal/v1/clinical/incidents` | `clinical.incidents.ingest.post` |
| GET | `/api/v1/residents/:residentId/incidents` | `residents.incidents.get` |
| GET | `/api/v1/incidents/:incidentId/sequence` | `incidents.sequence.get` |
| PATCH | `/api/v1/incidents/:incidentId` | `incidents.review.patch` |
| POST | `/api/v1/incidents/:incidentId/reviews` | `incidents.update.patch` |

## 9. Politica (8)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/alarm-presets/catalog` | `alarm-presets.catalog.get` |
| GET | `/api/v1/alarm-presets` | `alarm-presets.list.get` |
| GET | `/api/v1/alarm-presets/:residentId` | `alarm-presets.resident.get` |
| PATCH | `/api/v1/alarm-presets/:residentId` | `alarm-presets.resident.patch` |
| POST | `/api/v1/alarm-presets/apply-recommendations` | `alarm-presets.recommendations.post` |
| POST | `/api/v1/alarm-presets/autopilot` | `alarm-presets.autopilot.post` |
| GET | `/api/v1/alarm-presets/:residentId/history` | `alarm-presets.history.get` |
| POST | `/api/v1/alarm-presets/:residentId/apply-recommendation` | `alarm-presets.recommendation.post` |

## 10. Vigilancia (7)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/alerts` | `alerts.list.get` |
| POST | `/api/v1/alerts` | `alerts.create.post` |
| POST | `/api/v1/alerts/:alertId/view` | `alerts.view.post` |
| PATCH | `/api/v1/alerts/:alertId` | `alerts.update.patch` |
| GET | `/api/v1/alerts/:alertId/deliveries` | `alerts.deliveries.get` |
| POST | `/api/v1/alerts/:alertId/deliveries` | `alerts.deliveries.create.post` |
| POST | `/api/v1/deliveries/:deliveryId/events` | `alerts.delivery-events.create.post` |

## 11. Observacion (14)

| Method | Path | Route ID |
|--------|------|----------|
| POST | `/internal/v1/events` | `events.internal.post` |
| POST | `/internal/v1/clinical/sleep-summaries` | `clinical.sleep-summaries.ingest.post` |
| POST | `/internal/v1/clinical/mobility-summaries` | `clinical.mobility-summaries.ingest.post` |
| POST | `/internal/v1/clinical/bathroom-summaries` | `clinical.bathroom-summaries.ingest.post` |
| GET | `/api/v1/wings/:wingId/board` | `wings.board.get` |
| GET | `/api/v1/companion/rooms` | `companion.rooms.get` |
| POST | `/api/v1/rooms/:roomId/peek` | `rooms.peek.post` |
| GET | `/api/v1/reports/summary` | `reports.summary.get` |
| GET | `/api/v1/residents/:residentId/sleep` | `residents.sleep.get` |
| GET | `/api/v1/residents/:residentId/mobility` | `residents.mobility.get` |
| GET | `/api/v1/residents/:residentId/bathroom` | `residents.bathroom.get` |
| GET | `/api/v1/residents/:residentId/current-state` | `residents.current-state.get` |
| GET | `/api/v1/residents/:residentId/timeline` | `residents.timeline.get` |
| GET | `/api/v1/residents/:residentId/events` | `residents.events.get` |

## 12. Streams (6)

| Method | Path | Route ID |
|--------|------|----------|
| GET | `/api/v1/rooms/:roomId/streams` | `rooms.streams.list.get` |
| POST | `/api/v1/rooms/:roomId/streams` | `rooms.streams.create.post` |
| GET | `/api/v1/streams/:streamId` | `streams.detail.get` |
| GET | `/api/v1/streams/:streamId/regions` | `streams.regions.list.get` |
| PUT | `/api/v1/streams/:streamId/regions` | `streams.regions.replace.put` |
| PATCH | `/api/v1/streams/:streamId/regions/:regionId` | `streams.regions.update.patch` |

## 13. Evidence / Internal (10)

| Method | Path | Route ID |
|--------|------|----------|
| POST | `/internal/v1/evidence` | `internal.evidence.post` |
| GET | `/internal/v1/evidence/:evidenceId` | `internal.evidence.get` |
| GET | `/internal/v1/evidence` | `internal.evidence.list` |
| POST | `/internal/v1/timelines` | `internal.timelines.post` |
| GET | `/internal/v1/timelines/:timelineId` | `internal.timelines.get` |
| POST | `/internal/v1/timelines/:timelineId/close` | `internal.timelines.close` |
| POST | `/internal/v1/clip-windows` | `internal.clip-windows.post` |
| GET | `/internal/v1/clip-windows/:windowId` | `internal.clip-windows.get` |
| POST | `/internal/v1/clip-windows/:windowId/close` | `internal.clip-windows.close` |
| GET | `/internal/v1/clip-windows/:bedId/open` | `internal.clip-windows.list-open` |

## 14. Engine (3)

| Method | Path | Route ID |
|--------|------|----------|
| POST | `/internal/v1/engine/perception` | `engine.perception.post` |
| POST | `/internal/v1/engine/tick` | `engine.tick.post` |
| GET | `/internal/v1/engine/state/:bedId` | `engine.state.get` |

---

**Total: 96 endpoints** (all `sirve = "rust"`)
