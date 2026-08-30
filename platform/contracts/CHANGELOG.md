# Changelog

All notable changes to `com.manahive:contracts` will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

## [1.0.0] - 2026-08-30

### Added
- `Observation` — persona detectada por cámara (8 tipos: IN_BED, SITTING_IN_BED, STANDING, WALKING, etc.)
- `SceneEvent` — 14 subtypes: TransitionDetected, DwellWarning, DwellExceeded, ComeBackWarning, ComeBackExceeded, SignalLost, SignalRecovered, NightOpened, NightClosed, StaffPresenceDetected, StaffLeftDetected, SceneStateChanged, SceneDwellWarning, SceneDwellExceeded
- `SentinelSignal` — 7 subtypes: EpisodeOpened, EpisodeClosed, ComeBackPreWarning, UmbrellaEvent, PolicyChangeDetected, StaffContactRequested, NightSummary
- `AlarmEvent` — 8 subtypes: AlertRaised, NoticeDelivered, EscalationTriggered, StaffNotified, ReminderSent, SilenceTriggered, NightClosed
- `RecordingCommand` — 3 subtypes: RecordingStarted, RecordingStopped, ClipCreated
- `EvidenceRecord` — 3 subtypes: EvidenceRecordingStarted, EvidenceRecordingStopped, EvidenceClipCreated
- `ResidentProfileDto` — perfil completo del residente (windows, subjects, aspects, transitions)
- `AlarmProfile` — perfil de alarma (riskLevel, mobilityAid, policyMode, templates, overrides)
- `PolicyLayers` — capas de política (thresholds, templates, manualAdjustments, timeWindows)
- `PolicyProjection` — extensión `toAlarmProfile()` para proyectar PolicyLayers a AlarmProfile
- Value types: BedId, ResidentId, EpisodeId, NightId, MonitorId, etc.
- Enums: WatchLevel, Severity, StateKind, PersonState, SceneKind, PolicyMode, MobilityAid

### Architecture
- Shared Kernel pattern — same types used by mana-hive and mana-hub
- Published as JAR via Gradle maven-publish
- Depends on `com.manahive:domain-kernel` for value types

### Breaking Changes
- None — first release

### Known Limitations
- Jackson serialization not included — consumers must add `jackson-module-kotlin` and `jackson-datatype-jsr310`
- Kotlin serialization not included — not used in current architecture
