# Handoff: mana-hive ↔ mana-hub Integration

**Fecha:** 2026-08-30  
**Estado:** Sprint 1-3 completados, Sprint 6 en progreso

## Estado Actual

### Lo que funciona
- ✅ Arquitectura hexagonal completa (interfaces → core → adapters)
- ✅ 6 mains de test funcionando
- ✅ 8 blueprints compilando
- ✅ Time control vía NATS
- ✅ Profile change vía NATS
- ✅ Event recording vía NATS
- ✅ Contracts JAR publicado (`com.manahive:contracts:1.0.0`)
- ✅ NoticeEvent modelado (Dispatch, Sent, Delivered, Seen, Confirmed, Escalated, Expired, Resolved)
- ✅ Stream NOTICE en NatsTopology
- ✅ NVR Simulator funciona end-to-end
- ✅ Flujo completo: Service → NVR → ClipCreated

### Lo que falta
- 🔴 Sender externo (NoticeEvent.Sent)
- 🔴 App externa (NoticeEvent.Seen/Confirmed)
- 🔴 Scheduler (NoticeEvent.Escalated/Expired)
- 🔴 Profile endpoints no implementados
- 🔴 Outbox pattern no implementado
- 🔴 Bridge no consume notice.event.v1.>
- 🔴 E2E con hub real

## Event Taxonomy

| Event Type | Source Engine | Subject | Content |
|------------|---------------|---------|---------|
| `SceneEvent` | SceneEngine | `scene.fact.v1.<bed>` | Observations, transitions, signal lost |
| `SentinelSignal` | SentinelEngine | `sentinel.signal.v1.<bed>` | Episodes opened/closed, comeBack alerts |
| `NoticeEvent` | HarborEngine (via service) | `notice.event.v1.<notice>` | Dispatch, Sent, Confirmed, Escalated, Resolved |
| `EvidenceRecord` | RecorderEngine | `evidence.record.v1.<bed>` | Recording started/stopped, clip created |

### Command Taxonomy (internal, not on bus)

| Command | Source | Purpose |
|---------|--------|---------|
| `NoticeCommand` | HarborEngine | Internal to harbor domain |
| `RecordingCommand` | RecorderEngine | Instructions to NVR adapter |

## Próximos Pasos (Sprint 6)

### 1. Sender Externo

**Archivo:** `examples/jose-e1/src/main/kotlin/jose301/MainSender.kt`

```kotlin
// Escucha NoticeEvent.Dispatch
// Emite NoticeEvent.Sent al mismo canal
// Simula envío de push notification
```

### 2. App Externa (staff)

**Archivo:** `examples/jose-e1/src/main/kotlin/jose301/MainApp.kt`

```kotlin
// Escucha NoticeEvent.Sent
// Emite NoticeEvent.Seen cuando staff abre la notificación
// Emite NoticeEvent.Confirm cuando staff confirma presencia
```

### 3. Scheduler

**Archivo:** `examples/jose-e1/src/main/kotlin/jose301/MainScheduler.kt`

```kotlin
// Escucha NoticeEvent.Confirm
// Si no hay respuesta en 15 minutos → emite NoticeEvent.Escalated
// Si episode se resuelve → emite NoticeEvent.Expired
```

## Archivos Clave

```
contracts:    platform/contracts/src/main/kotlin/com/manahive/contracts/
core:         engines/night-watch-runtime/src/main/kotlin/com/manahive/runtime/
tests:        examples/jose-e1/src/main/kotlin/jose301/
blueprints:   blueprints/level-thresholds/
ADRs:         docs/adr/
```

## Comandos Útiles

```bash
# Todos los mains
for main in MainKt MainFromJsonKt MainPipelineKt MainResidentRuntimeKt MainNightWatchRuntimeKt MainNightWatchServiceCoreKt; do
  ./gradlew :examples:jose-e1:run -Pmain=jose301.$main
done

# NVR Simulator
./gradlew :examples:jose-e1:run -Pmain=jose301.MainNvrSimulatorKt

# Blueprints
./gradlew :blueprints:level-thresholds:run

# NATS integration
# Terminal 1: ./gradlew :engines:night-watch-runtime:bootRun
# Terminal 2: ./gradlew :examples:jose-e1:run -Pmain=jose301.MainColdBootProfileKt
```

## Decisiones Pendientes

1. **Engine-Owned Publishing (ADR-006)** — refactor para que cada engine publique directo via publisher interface
2. **Sender implementation** — ¿real o simulado?
3. **Escalation policy** — ¿tiempo fijo o configurable por residente?
4. **Profile endpoints** — ¿usar contratos existentes o crear nuevos?
