# Design: Hub Scene Event Handler

## Propósito

Definir cómo el hub procesa los scene events que recibe del engine.

## Concepto

El hub recibe scene events del engine y:
1. Actualiza `current_bed_states` (system of record)
2. Persiste en `event_store` (event sourcing)
3. Evalúa presets para decidir alertas
4. Notifica a la UI si aplica

## Flujo

```
Scene Event (del engine)
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Hub Scene Event Handler                                     │
│                                                              │
│  1. VALIDAR scene event                                      │
│  2. UPDATE current_bed_states                                │
│  3. PERSIST event_store                                      │
│  4. EVALUATE presets                                         │
│  5. NOTIFY (si aplica)                                       │
└─────────────────────────────────────────────────────────────┘
```

## Estructura

```rust
pub struct SceneEventHandler {
    observation_store: ObservationStore,
    profile_store: ProfileStore,
    vigilancia_store: VigilanciaStore,
    notification_sender: NotificationSender,
}

impl SceneEventHandler {
    pub async fn handle(
        &self,
        event: SceneEvent,
    ) -> Result<(), HubError> {
        // 1. Validar
        self.validate(&event)?;
        
        // 2. Actualizar current_bed_states
        self.update_bed_state(&event).await?;
        
        // 3. Persistir en event_store
        self.persist_event(&event).await?;
        
        // 4. Evaluar presets
        let alert_level = self.evaluate_preset(&event).await?;
        
        // 5. Notificar si aplica
        if alert_level != AlertLevel::Off {
            self.notify(event, alert_level).await?;
        }
        
        Ok(())
    }
}
```

## Validación

```rust
fn validate(&self, event: &SceneEvent) -> Result<(), HubError> {
    // 1. bed_id es obligatorio
    if event.bed_id.is_empty() {
        return Err(HubError::InvalidSceneEvent("bed_id is required"));
    }
    
    // 2. timestamp es obligatorio
    if event.timestamp.is_none() {
        return Err(HubError::InvalidSceneEvent("timestamp is required"));
    }
    
    // 3. trigger es obligatorio
    if event.trigger.is_none() {
        return Err(HubError::InvalidSceneEvent("trigger is required"));
    }
    
    // 4. poi es obligatorio
    if event.poi.is_none() {
        return Err(HubError::InvalidSceneEvent("poi is required"));
    }
    
    Ok(())
}
```

## Update current_bed_states

```rust
async fn update_bed_state(&self, event: &SceneEvent) -> Result<(), HubError> {
    let poi = event.poi.as_ref().unwrap();
    
    let bed_state = CurrentBedState {
        bed_id: event.bed_id.clone(),
        resident_id: poi.resident_id.clone(),
        state: poi.state.clone(),
        state_since: poi.state_since.clone(),
        sleeping: poi.sleeping.unwrap_or(false),
        updated_at: event.timestamp.clone(),
        source: "engine".to_string(),
        source_event_id: event.trigger.as_ref().map(|t| t.event_id()),
    };
    
    self.observation_store.upsert_bed_state(&bed_state).await?;
    
    Ok(())
}
```

## Persist event_store

```rust
async fn persist_event(&self, event: &SceneEvent) -> Result<(), HubError> {
    let stored_event = StoredEvent {
        id: generate_id(),
        bed_id: event.bed_id.clone(),
        event_type: event.event_type.clone(),
        payload_json: serde_json::to_string(event)?,
        created_at: event.timestamp.clone(),
    };
    
    self.observation_store.insert_event(&stored_event).await?;
    
    Ok(())
}
```

## Evaluar Presets

```rust
async fn evaluate_preset(&self, event: &SceneEvent) -> Result<AlertLevel, HubError> {
    let poi = event.poi.as_ref().unwrap();
    
    // 1. Obtener perfil del residente
    let profile = self.profile_store
        .get_profile(&poi.resident_id)
        .await?
        .unwrap_or_default();
    
    // 2. Determinar shift (day/night)
    let shift = self.determine_shift(&event.timestamp);
    
    // 3. Buscar regla aplicable
    let rule = self.find_applicable_rule(event, &profile);
    
    // 4. Evaluar nivel de alerta
    match rule {
        Some(rule) => {
            let level = match shift {
                Shift::Day => rule.day_level,
                Shift::Night => rule.night_level,
            };
            Ok(level)
        }
        None => Ok(AlertLevel::Off),
    }
}
```

## Evaluar por Tipo de Evento

```rust
fn find_applicable_rule(
    &self,
    event: &SceneEvent,
    profile: &AlarmProfile,
) -> Option<ApplicableRule> {
    match event.event_type {
        SceneEventType::Perception => {
            // Evaluar reglas de percepción
            self.evaluate_perception_rule(event, profile)
        }
        SceneEventType::Dwell => {
            // Evaluar reglas de dwell
            self.evaluate_dwell_rule(event, profile)
        }
        SceneEventType::Transition => {
            // Evaluar reglas de transición
            self.evaluate_transition_rule(event, profile)
        }
        SceneEventType::Change => {
            // Los cambios son informativos, no alarma
            None
        }
    }
}
```

## Notificación

```rust
async fn notify(
    &self,
    event: SceneEvent,
    level: AlertLevel,
) -> Result<(), HubError> {
    match level {
        AlertLevel::Off => Ok(()),
        AlertLevel::Notify => {
            self.notification_sender.notify(event).await?;
            Ok(())
        }
        AlertLevel::Alarm => {
            self.notification_sender.alarm(event).await?;
            Ok(())
        }
    }
}
```

## Ejemplo de Procesamiento

### Perception Event

```json
{
  "event_type": "perception",
  "bed_id": "118-A",
  "resident_id": "res-001",
  "poi": {
    "state": "standing",
    "state_since": "2026-08-18T02:10:00Z"
  },
  "trigger": {
    "perception_event_id": "evt-001",
    "confidence": 0.92
  }
}
```

**Procesamiento:**
1. Validar: ✓
2. UPDATE current_bed_states: state = "standing"
3. PERSIST event_store: scene_event guardado
4. EVALUATE: perfil "medium" → "standing" de día = "notify"
5. NOTIFY: notificación enviada

### Dwell Timer

```json
{
  "event_type": "dwell",
  "bed_id": "118-A",
  "resident_id": "res-001",
  "poi": {
    "state": "standing",
    "state_since": "2026-08-18T02:10:00Z"
  },
  "trigger": {
    "rule_id": "out_of_bed_dwell",
    "duration_minutes": 12,
    "threshold_minutes": 10
  }
}
```

**Procesamiento:**
1. Validar: ✓
2. UPDATE current_bed_states: (sin cambios, state = "standing")
3. PERSIST event_store: scene_event guardado
4. EVALUATE: perfil "medium" → "out_of_bed_dwell" = "alarm"
5. NOTIFY: alarma enviada

## Invariantes

1. El handler siempre valida antes de procesar
2. current_bed_states se actualiza con cada scene event
3. event_store recibe todos los scene events
4. Presets se evalúan por tipo de evento
5. Notificación es asíncrona (no bloquea el handler)
6. Errores se loguean pero no detienen el procesamiento
