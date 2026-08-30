Plan de Implementación: bridge + hub → compatible con hive
Mapa completo: lo que hive publica vs lo que hub tiene
Evento hive	Subtypes	Hub tiene
Observation	1	IngestEventRequest
SceneEvent	14	flat SceneEvent
SentinelSignal	7	Episode
AlarmEvent	8	NotificationEvent
RecordingCommand	3	NADA
EvidenceRecord	3	Evidence (genérico)
ResidentProfileDto	1	AlarmProfileVersion
Fase 1 — Bridge: deserializar y routear (sin cambiar hub)
Objetivo: El bridge deserializa los sealed interfaces de hive y mapea a los DTOs que hub ya tiene.
Archivos a crear/modificar:
event-bridge/src/main/kotlin/com/hub/bridge/ingest/
├── HiveEventDeserializer.kt     ← NUEVO: deserializa payloadJson a tipos hive
├── EventRouter.kt               ← NUEVO: routea por subject al endpoint correcto
├── NatsIngestService.kt         ← MODIFICAR: usar router en vez de forwardToHub genérico
└── translators/
    ├── SceneEventTranslator.kt  ← NUEVO: SceneEvent → IngestSceneEventRequest
    ├── SentinelTranslator.kt    ← NUEVO: SentinelSignal → CreateEpisodeRequest/Patch
    ├── AlarmTranslator.kt       ← NUEVO: AlarmEvent → IngestNotificationRequest
    └── RecordingTranslator.kt   ← NUEVO: RecordingCommand → (nuevo DTO)
Cambios en NatsIngestService:
// ANTES: todo al mismo endpoint
forwardToHub(subject, payloadJson)

// DESPUÉS: routear por subject
when {
    subject.startsWith("perception.") -> forwardToHub("/internal/v1/events", payload)
    subject.startsWith("scene.") -> forwardToHub("/internal/v1/scene-events", translateScene(payload))
    subject.startsWith("sentinel.") -> forwardToEpisodes(payload)
    subject.startsWith("alarm.") -> forwardToHub("/internal/v1/notifications", translateAlarm(payload))
    subject.startsWith("recorder.") -> forwardToHub("/internal/v1/recordings", translateRecording(payload))
    subject.startsWith("evidence.") -> forwardToHub("/internal/v1/evidence", translateEvidence(payload))
}
Fase 2 — Hub: expandir SceneEvent (CRÍTICO)
Objetivo: SceneEvent soporta los 14 subtypes de hive.
Archivo: observation/src/main/kotlin/com/hub/observation/domain/model/SceneEvent.kt
// ANTES: flat data class
data class SceneEvent(
    val eventType: String,
    val fromState: String?,
    val toState: String?,
    ...
)

// DESPUÉS: sealed interface
sealed interface SceneEvent {
    val id: Identifier
    val bedId: BedId
    val nightId: String?
    val at: Instant
    val payloadJson: String

    data class TransitionDetected(...) : SceneEvent
    data class DwellWarning(...) : SceneEvent
    data class DwellExceeded(...) : SceneEvent
    data class ComeBackWarning(...) : SceneEvent
    data class ComeBackExceeded(...) : SceneEvent
    data class SignalLost(...) : SceneEvent
    data class SignalRecovered(...) : SceneEvent
    data class NightOpened(...) : SceneEvent
    data class NightClosed(...) : SceneEvent
    data class StaffPresenceDetected(...) : SceneEvent
    data class StaffLeftDetected(...) : SceneEvent
    data class SceneStateChanged(...) : SceneEvent
    data class SceneDwellWarning(...) : SceneEvent
    data class SceneDwellExceeded(...) : SceneEvent
}
DTO: IngestSceneEventRequest se mantiene como entry point, pero el translator del bridge mapea los campos.
Fase 3 — Hub: expandir Episode + Severity (CRÍTICO)
Archivo: surveillance/src/main/kotlin/com/hub/surveillance/domain/model/EpisodeSeverity.kt
// ANTES
enum class EpisodeSeverity { INFO, WARNING, CRITICAL, EMERGENCY }

// DESPUÉS (alineado a hive)
enum class EpisodeSeverity { INFO, WARNING, HIGH, CRITICAL }
Archivo: surveillance/src/main/kotlin/com/hub/surveillance/domain/model/Episode.kt
Agregar campos:
class Episode(
    ...
    val ruleId: String?,              // ← NUEVO
    val rulesFingerprint: String?,    // ← NUEVO
    val trigger: String?,             // ← NUEVO (StateKind)
    val triggerField: String?,        // ← NUEVO
    val reversible: Boolean?,         // ← NUEVO
    val requiresNvr: Boolean?,        // ← NUEVO
    val confirmationWindowMs: Long?,  // ← NUEVO
    val closureCause: String?,        // ← NUEVO
    val gapDurationMs: Long?,         // ← NUEVO
    val sourceEventId: String?,       // ← NUEVO (idempotency)
)
Fase 4 — Hub: crear RecordingCommand model (NUEVO)
Objetivo: Hub puede registrar comandos de grabación.
Archivos nuevos:
surveillance/src/main/kotlin/com/hub/surveillance/domain/model/
├── RecordingCommand.kt         ← NUEVO: sealed interface
├── RecordingTarget.kt          ← NUEVO: bed + monitor
├── RecordingContext.kt         ← NUEVO: standalone / tied-to-episode
└── RecordingQuality.kt         ← NUEVO: SD/HD/FULL

surveillance/src/main/kotlin/com/hub/surveillance/application/dto/
└── RecordingDtos.kt            ← NUEVO: IngestRecordingRequest
sealed interface RecordingCommand {
    val target: RecordingTarget
    val at: Instant
    val context: RecordingContext

    data class Started(...) : RecordingCommand
    data class Stopped(...) : RecordingCommand
    data class ClipCreated(...) : RecordingCommand
}
Fase 5 — Hub: expandir NotificationEvent (ALTO)
Archivo: observation/src/main/kotlin/com/hub/observation/domain/model/NotificationEvent.kt
Agregar campos:
data class NotificationEvent(
    ...
    val alertKeyBedId: String?,       // ← NUEVO
    val alertKeyRuleId: String?,      // ← NUEVO
    val alertKeyEpisodeId: String?,   // ← NUEVO
    val channel: String?,             // ← NUEVO
    val step: Int?,                   // ← NUEVO
    val recipients: String?,          // ← NUEVO (JSON list)
    val escalationCause: String?,     // ← NUEVO
)
Fase 6 — Hub: implementar ProfileEndpoints (ALTO)
Objetivo: Hub expone la API que hive espera para perfiles.
Archivos nuevos:
policy/src/main/kotlin/com/hub/policy/api/
├── ResidentProfileController.kt   ← NUEVO: implementa ProfileEndpoints
├── ResidentProfileDtos.kt         ← NUEVO: DTOs del perfil completo
└── ResidentProfileService.kt      ← NUEVO: lógica de negocio
Subject NATS: hub.policy.profile.v1 → el bridge publica cuando cambia un perfil.
Fase 7 — Hub: outbox + effective-rules (MODERADO)
Archivos:
- V11__hub_policy_outbox.sql ← migración
- HubPolicyOutboxRelay.kt ← relay de outbox a NATS
- Publicar hub.policy.effective-rules.v1.<resident> después de resolver
Fase 8 — Hub: census snapshot (BAJO)
Subject: hub.census.snapshot.v1
Payload: List<CensusEntry> (bed → resident mapping)
Cuándo: Al arrancar y cuando cambia el censo
Orden de implementación
Fase 1 (bridge routing)     → funciona sin cambiar hub
Fase 2 (SceneEvent)         → CRÍTICO, desbloquea todo
Fase 3 (Episode + Severity) → CRÍTICO, alinea tipos
Fase 4 (RecordingCommand)   → NUEVO, registra grabaciones
Fase 5 (NotificationEvent)  → ALTO, lifecycle completo
Fase 6 (ProfileEndpoints)   → ALTO, cold boot funciona
Fase 7 (outbox + rules)     → MODERADO, efectividad
Fase 8 (census)             → BAJO, último
Validación
Cada fase se valida con un test tipo MainNatsScenarioE1 pero apuntando al hub:
1. Publicar observación vía NATS
2. Verificar que hub la registró (GET /api/v1/beds/{bedId}/state)
3. Publicar policy change vía hub API
4. Verificar que hive lo recibió (log [TIME])
5. Publicar scene event vía NATS
6. Verificar que hub lo guardó (GET /api/v1/timeline/{bedId})