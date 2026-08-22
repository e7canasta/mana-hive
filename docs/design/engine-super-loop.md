# Design: Engine Super Loop

## Propósito

Definir el ciclo principal del engine que procesa eventos y actualiza el digital twin.

## Concepto

El engine opera como un PLC con super loop: tiene dos fuentes de eventos:

1. **Perception events** (push): llegan del edge vía hub
2. **Tick** (pull): corre cada N segundos para revisar timers

Ambos producen **scene events** que el hub recibe y procesa.

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                    MANA ENGINE                                │
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │  Perception Event     │  │  Tick (super loop)    │        │
│  │  (push from hub)      │  │  (cada 5 segundos)    │        │
│  └──────────┬───────────┘  └──────────┬───────────┘        │
│             │                          │                     │
│             ▼                          ▼                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    DigitalTwin                        │    │
│  │  - beds: HashMap<String, BedTwin>                    │    │
│  │  - catalog: Arc<AlarmCatalog>                        │    │
│  └─────────────────────────────────────────────────────┘    │
│             │                                                  │
│             ▼                                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    SceneEvent                         │    │
│  │  - event_type: perception | dwell | transition       │    │
│  │  - estado completo de la escena                      │    │
│  │  - trigger: por qué se emitió                        │    │
│  └─────────────────────────────────────────────────────┘    │
│             │                                                  │
│             ▼                                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    EngineOutput                       │    │
│  │  - emit scene_event to hub                           │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## API Pública

```rust
pub struct Engine {
    twin: DigitalTwin,
    catalog: Arc<AlarmCatalog>,
    output: Box<dyn EngineOutput>,
}

impl Engine {
    // Crear engine
    pub fn new(
        catalog: Arc<AlarmCatalog>,
        output: Box<dyn EngineOutput>,
    ) -> Self;
    
    // Procesar perception event (push)
    pub fn on_perception_event(
        &mut self,
        event: PerceptionEvent,
    ) -> Vec<SceneEvent>;
    
    // Tick del super loop (pull)
    pub fn tick(&mut self) -> Vec<SceneEvent>;
    
    // Obtener estado actual de una cama
    pub fn get_bed_state(&self, bed_id: &str) -> Option<&BedTwin>;
    
    // Listar todas las camas activas
    pub fn list_beds(&self) -> Vec<&BedTwin>;
}

#[async_trait]
pub trait EngineOutput: Send + Sync {
    async fn emit(&self, event: SceneEvent) -> Result<(), EngineError>;
}
```

## Super Loop

```rust
pub async fn spawn_super_loop(
    mut engine: Engine,
    period: Duration,
) {
    let mut interval = tokio::time::interval(period);
    
    loop {
        interval.tick().await;
        
        let scene_events = engine.tick();
        
        for event in scene_events {
            if let Err(e) = engine.output.emit(event).await {
                tracing::error!("Failed to emit scene event: {}", e);
            }
        }
    }
}
```

## Flujo de Procesamiento

### Perception Event

```rust
fn on_perception_event(&mut self, event: PerceptionEvent) -> Vec<SceneEvent> {
    let bed = self.twin.beds
        .entry(event.bed_id.clone())
        .or_insert_with(|| BedTwin::new(event.bed_id.clone()));
    
    let mut outputs = vec![];
    
    // 1. Detectar transición
    let old_state = bed.person.state.clone();
    let new_state = detect_state(&event);
    
    if old_state != new_state {
        // 2. Cancelar timers del estado anterior
        bed.cancel_timers_for_state(&old_state);
        
        // 3. Actualizar FSM
        bed.person.state = new_state.clone();
        bed.person.state_since = event.timestamp;
        
        // 4. Iniciar timers del nuevo estado
        bed.start_timers_for_state(&new_state, &self.catalog);
        
        // 5. Emitir scene event de transición
        outputs.push(SceneEvent::new(
            SceneEventType::Transition,
            bed,
            Some(TriggerInfo::TransitionDetected {
                from_state: old_state,
                to_state: new_state,
            }),
        ));
    }
    
    // 6. Actualizar objetos
    bed.update_objects(&event);
    
    // 7. Actualizar room
    bed.update_room(&event);
    
    // 8. Emitir scene event de percepción
    outputs.push(SceneEvent::new(
        SceneEventType::Perception,
        bed,
        Some(TriggerInfo::Perception {
            perception_event_id: event.source_event_id,
            confidence: event.confidence,
        }),
    ));
    
    outputs
}
```

### Tick

```rust
fn tick(&mut self) -> Vec<SceneEvent> {
    let now = Instante::now();
    let mut outputs = vec![];
    
    for bed in self.twin.beds.values_mut() {
        // Revisar timers activos
        for timer in &mut bed.timers {
            if timer.state == TimerState::Active {
                let elapsed = now - timer.started_at;
                let threshold = Duration::from_secs(
                    timer.threshold_minutes as u64 * 60
                );
                
                if elapsed >= threshold {
                    timer.state = TimerState::Fired;
                    
                    // Emitir scene event de dwell
                    outputs.push(SceneEvent::new(
                        SceneEventType::Dwell,
                        bed,
                        Some(TriggerInfo::DwellCompleted {
                            rule_id: timer.rule_id.clone(),
                            duration_minutes: elapsed.as_secs() as i32 / 60,
                            threshold_minutes: timer.threshold_minutes,
                        }),
                    ));
                }
            }
        }
        
        // Limpiar timers que ya dispararon
        bed.timers.retain(|t| t.state == TimerState::Active);
    }
    
    outputs
}
```

## Periodo del Super Loop

```rust
// Configurable via env var
const DEFAULT_TICK_PERIOD: Duration = Duration::from_secs(5);

fn tick_period() -> Duration {
    std::env::var("MANA_ENGINE_TICK_PERIOD")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(DEFAULT_TICK_PERIOD)
}
```

## Manejo de Errores

```rust
fn tick(&mut self) -> Vec<SceneEvent> {
    let now = Instante::now();
    let mut outputs = vec![];
    
    for bed in self.twin.beds.values_mut() {
        match self.process_bed_timers(bed, now) {
            Ok(events) => outputs.extend(events),
            Err(e) => {
                tracing::error!(
                    bed_id = %bed.bed_id,
                    error = %e,
                    "Failed to process timers for bed"
                );
                // Continuar con las demás camas
            }
        }
    }
    
    outputs
}
```

## Métricas

```rust
// Métricas que el engine debería exponer
struct EngineMetrics {
    beds_active: Gauge,
    timers_active: Gauge,
    scene_events_emitted: Counter,
    perception_events_processed: Counter,
    ticks_completed: Counter,
    ticks_failed: Counter,
}
```

## Invariantes

1. El super loop nunca se detiene (aunque haya errores)
2. Los errores se loguean pero no matan el proceso
3. Cada tick processa todas las camas
4. Cada perception event procesa solo una cama
5. Los scene events se emiten de forma asíncrona
6. El engine es thread-safe (puede recibir múltiples perception events)
