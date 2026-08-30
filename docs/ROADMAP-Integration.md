# Roadmap: mana-hive ↔ mana-hub Integration

**Fecha:** 2026-08-30  
**Estado:** En progreso

## Visión General

Integrar mana-hive (motor de vigilancia) con mana-hub (system of record) usando un Shared Kernel via Contracts JAR.

```
mana-hive (owner)          mana-hub (consumer)
├── SceneEvent (14)        ──→ usa mismos tipos
├── SentinelSignal (7)     ──→ usa mismos tipos
├── NoticeEvent (8)        ──→ usa mismos tipos (reemplaza AlarmEvent)
├── RecordingCommand (3)   ──→ usa mismos tipos
├── EvidenceRecord (3)     ──→ usa mismos tipos
├── ResidentProfileDto     ──→ implementa ProfileEndpoints
└── Publicados como son    ← contracts JAR compartido
```

## Arquitectura Command/Event (ADR-006)

```
┌─────────────────────────────────────────────────────────┐
│                    mana-hive engines                      │
│                                                           │
│  SceneEngine    → SceneEvent                              │
│  SentinelEngine → SentinelSignal                          │
│  HarborEngine   → NoticeCommand → NoticeEvent             │
│  RecorderEngine → RecordingCommand → EvidenceRecord        │
│                                                           │
│  Each engine generates DOMAIN OBJECTS.                    │
│  The SERVICE converts domain objects to BUS EVENTS.       │
└─────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │   Event Bus     │  ← mana-hub escucha
                    │  (NATS JetStream)│    system of record
                    └─────────────────┘
                              ↑
┌─────────────────────────────────────────────────────────┐
│              External agents (subscribers)                │
│                                                           │
│  NVR Simulator  → escucha RecordingCommand                │
│                   emite ClipCreated                        │
│  Sender         → escucha NoticeEvent.Dispatch            │
│                   emite NoticeEvent.Sent                   │
│  App (staff)    → emite NoticeEvent.Seen/Confirmed        │
│  Scheduler      → emite NoticeEvent.Escalated/Expired     │
│                                                           │
│  Cada agente acciona sobre COMANDOS.                      │
│  Cada agente publica EVENTOS al mismo canal.              │
└─────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │   mana-hub      │  ← system of record
                    │  (escucha       │    registra TODOS los
                    │   TODOS los     │    eventos: hive +
                    │   eventos)      │    external agents
                    └─────────────────┘
```

## Sprints

### Sprint 1: Contracts JAR ✅

**Objetivo:** Publicar `mana-hive/contracts` como JAR que mana-hub pueda consumir.

**Tareas:**
- [x] Configurar Gradle publication en `platform/contracts/build.gradle.kts`
- [x] Definir group/artifact/version (`com.manahive:contracts:1.0.0`)
- [x] Publicar a Maven Local (`./gradlew publishToMavenLocal`)
- [x] Crear CHANGELOG.md en contracts

**Entregable:** JAR disponible en Maven Local, documentado.

---

### Sprint 2: Hub Model Expansion ✅ (parcial)

**Objetivo:** mana-hub soporta los tipos completos de hive.

**Tareas:**
- [x] **SceneEvent** → sealed interface con 14 subtypes
- [x] **EpisodeSeverity** → alinear a {INFO, WARNING, HIGH, CRITICAL}
- [x] **Episode** → agregar campos faltantes (ruleId, trigger, reversible, etc.)
- [x] **RecordingCommand** → crear modelo (Started, Stopped, ClipCreated)
- [x] **NoticeEvent** → modelar lifecycle completo (Dispatch, Sent, Delivered, Seen, Confirmed, Escalated, Expired, Resolved)
- [x] **EvidenceRecord** → alinear con tipos de hive
- [x] **Tests** → verificar serialización/deserialización
- [ ] **NotificationEvent** → agregar alertKey, channel, step, recipients (migrar a NoticeEvent)

**Entregable:** Hub usa contratos de hive, todos los tests pasan.

---

### Sprint 3: Bridge Routing ✅ (parcial)

**Objetivo:** Bridge deserializa y routea correctamente.

**Tareas:**
- [x] Agregar dependencia de `contracts` JAR
- [x] Crear `EventRouter` (subject → endpoint mapping)
- [x] Implementar deserialización de sealed interfaces
- [x] Crear translators (si es necesario) o forward directo
- [x] Subscribir a `recorder.command.v1.>` y `evidence.record.v1.>`
- [x] Test end-to-end con NatsEventRecorder
- [ ] Verificar `notice.event.v1.>` en bridge

**Entregable:** Bridge consume todos los eventos de hive correctamente.

---

### Sprint 4: Profile Integration (pendiente)

**Objetivo:** Cold boot y profile changes funcionan end-to-end.

**Tareas:**
- [ ] Implementar `ProfileEndpoints` en hub
- [ ] Crear `ResidentProfile` aggregate en hub
- [ ] Publicar `hub.policy.profile.v1` vía bridge
- [ ] Crear `hub.census.snapshot.v1` subject
- [ ] Test: cold boot → profile change → recalibration

**Entregable:** Flujo completo cold boot funciona con hub real.

---

### Sprint 5: Outbox + Effective Rules (pendiente)

**Objetivo:** Policy changes son duraderas y auditables.

**Tareas:**
- [ ] Crear migración `V11__hub_policy_outbox.sql`
- [ ] Implementar `HubPolicyOutboxRelay`
- [ ] Publicar `hub.policy.effective-rules.v1.<resident>`
- [ ] Test: policy change → outbox → NATS → hive recibe

**Entregable:** Policy changes persisten y se propagan correctamente.

---

### Sprint 6: End-to-End Validation (en progreso)

**Objetivo:** Todo funciona junto con hub real.

**Tareas:**
- [x] NVR Simulator: escucha RecordingCommand → emite ClipCreated
- [x] Flujo completo: Service → NVR → ClipCreated
- [ ] Sender externo: escucha NoticeEvent.Dispatch → emite NoticeEvent.Sent
- [ ] App externa: emite NoticeEvent.Seen/Confirmed
- [ ] Scheduler: emite NoticeEvent.Escalated/Expired
- [ ] Levantar: NATS + mana-hub + mana-hive
- [ ] Ejecutar `MainNatsScenarioE1` apuntando al hub real
- [ ] Verificar: observaciones → hub las registra
- [ ] Verificar: episodes → hub los crea correctamente
- [ ] Verificar: recordings → hub los registra
- [ ] Verificar: notices → hub los registra correctamente

**Entregable:** Sistema completo funcionando, documentado.

---

## Dependencias

```
Sprint 1 (Contracts JAR) ✅
    ↓
Sprint 2 (Hub Models) ✅  ← Sprint 3 (Bridge Routing) ✅
    ↓                           ↓
Sprint 4 (Profile) ←────────────┘
    ↓
Sprint 5 (Outbox)
    ↓
Sprint 6 (E2E Validation) 🔄
```

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Contracts JAR rompe compatibilidad | Alto | Semver estricto, backward compatibility |
| Hub model expansion tarda | Medio | Priorizar SceneEvent y Episode |
| Bridge routing complejo | Medio | Empezar con forward directo |
| Profile integration difficile | Alto | Usar contratos existentes de profile-api |
| Sender externo no llega a tiempo | Medio | Simular con NVR Simulator como proxy |

## Métricas de Éxito

- [x] 100% de eventos de hive publicados en bus
- [x] NVR Simulator funciona end-to-end
- [ ] 100% de eventos de hive consumidos por hub
- [ ] 0 traducciones en bridge (forward directo)
- [ ] Tests end-to-end pasando
- [ ] Cold boot funciona con hub real
- [ ] Policy changes persisten y se propagan
