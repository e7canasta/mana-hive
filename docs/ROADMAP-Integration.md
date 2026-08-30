# Roadmap: mana-hive ↔ mana-hub Integration

**Fecha:** 2026-08-30  
**Estado:** Planificado

## Visión General

Integrar mana-hive (motor de vigilancia) con mana-hub (system of record) usando un Shared Kernel via Contracts JAR.

```
mana-hive (owner)          mana-hub (consumer)
├── SceneEvent (14)        ──→ usa mismos tipos
├── SentinelSignal (7)     ──→ usa mismos tipos
├── AlarmEvent (8)         ──→ usa mismos tipos
├── RecordingCommand (3)   ──→ usa mismos tipos
├── EvidenceRecord (3)     ──→ usa mismos tipos
├── ResidentProfileDto     ──→ implementa ProfileEndpoints
└── Publicados como son    ← contracts JAR compartido
```

## Sprints

### Sprint 1: Contracts JAR (1-2 días)

**Objetivo:** Publicar `mana-hive/contracts` como JAR que mana-hub pueda consumir.

**Tareas:**
- [ ] Configurar Gradle publication en `platform/contracts/build.gradle.kts`
- [ ] Definir group/artifact/version (`com.manahive:contracts:1.0.0`)
- [ ] Publicar a Maven Local (`./gradlew publishToMavenLocal`)
- [ ] Verificar que mana-hub puede agregar la dependencia
- [ ] Documentar versioning strategy (semver estricto)
- [ ] Crear CHANGELOG.md en contracts

**Entregable:** JAR disponible en Maven Local, documentado.

---

### Sprint 2: Hub Model Expansion (3-5 días)

**Objetivo:** mana-hub soporta los tipos completos de hive.

**Tareas:**
- [ ] **SceneEvent** → sealed interface con 14 subtypes
- [ ] **EpisodeSeverity** → alinear a {INFO, WARNING, HIGH, CRITICAL}
- [ ] **Episode** → agregar campos faltantes (ruleId, trigger, reversible, etc.)
- [ ] **RecordingCommand** → crear modelo (Started, Stopped, ClipCreated)
- [ ] **NotificationEvent** → agregar alertKey, channel, step, recipients
- [ ] **EvidenceRecord** → alinear con tipos de hive
- [ ] **Tests** → verificar serialización/deserialización

**Entregable:** Hub usa contratos de hive, todos los tests pasan.

---

### Sprint 3: Bridge Routing (2-3 días)

**Objetivo:** Bridge deserializa y routea correctamente.

**Tareas:**
- [ ] Agregar dependencia de `contracts` JAR
- [ ] Crear `EventRouter` (subject → endpoint mapping)
- [ ] Implementar deserialización de sealed interfaces
- [ ] Crear translators (si es necesario) o forward directo
- [ ] Subscribir a `recorder.command.v1.>` y `evidence.record.v1.>`
- [ ] Test end-to-end con NatsEventRecorder

**Entregable:** Bridge consume todos los eventos de hive correctamente.

---

### Sprint 4: Profile Integration (2-3 días)

**Objetivo:** Cold boot y profile changes funcionan end-to-end.

**Tareas:**
- [ ] Implementar `ProfileEndpoints` en hub
- [ ] Crear `ResidentProfile` aggregate en hub
- [ ] Publicar `hub.policy.profile.v1` vía bridge
- [ ] Crear `hub.census.snapshot.v1` subject
- [ ] Test: cold boot → profile change → recalibration

**Entregable:** Flujo completo cold boot funciona con hub real.

---

### Sprint 5: Outbox + Effective Rules (2-3 días)

**Objetivo:** Policy changes son duraderas y auditables.

**Tareas:**
- [ ] Crear migración `V11__hub_policy_outbox.sql`
- [ ] Implementar `HubPolicyOutboxRelay`
- [ ] Publicar `hub.policy.effective-rules.v1.<resident>`
- [ ] Test: policy change → outbox → NATS → hive recibe

**Entregable:** Policy changes persisten y se propagan correctamente.

---

### Sprint 6: End-to-End Validation (2-3 días)

**Objetivo:** Todo funciona junto con hub real.

**Tareas:**
- [ ] Levantar: NATS + mana-hub + mana-hive
- [ ] Ejecutar `MainNatsScenarioE1` apuntando al hub real
- [ ] Verificar: observaciones → hub las registra
- [ ] Verificar: policy changes → hive las recibe
- [ ] Verificar: episodes → hub los crea correctamente
- [ ] Verificar: recordings → hub los registra
- [ ] Documentar Gaps restantes

**Entregable:** Sistema completo funcionando, documentado.

---

## Dependencias

```
Sprint 1 (Contracts JAR)
    ↓
Sprint 2 (Hub Models) ← Sprint 3 (Bridge Routing)
    ↓                       ↓
Sprint 4 (Profile) ←────────┘
    ↓
Sprint 5 (Outbox)
    ↓
Sprint 6 (E2E Validation)
```

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Contracts JAR rompe compatibilidad | Alto | Semver estricto, backward compatibility |
| Hub model expansion tarda | Medio | Priorizar SceneEvent y Episode |
| Bridge routing complejo | Medio | Empezar con forward directo |
| Profile integration difficile | Alto | Usar contratos existentes de profile-api |

## Métricas de Éxito

- [ ] 100% de eventos de hive consumidos por hub
- [ ] 0 traducciones en bridge (forward directo)
- [ ] Tests end-to-end pasando
- [ ] Cold boot funciona con hub real
- [ ] Policy changes persisten y se propagan
