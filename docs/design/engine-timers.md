# Design: Engine Timers

## Propósito

Definir cómo el engine gestiona timers para dwells, confirmaciones y otros eventos temporizados.

## Concepto

Los timers son contadores que miden cuánto tiempo permanece un residente en un estado. Cuando se supera el umbral, el timer dispara un scene event.

## Estructura

```rust
struct Timer {
    rule_id: String,
    bed_id: String,
    started_at: Instante,
    threshold_minutes: i32,
    state: TimerState,
}

enum TimerState {
    Active,     // corriendo
    Fired,      // superó el umbral
    Cancelled,  // cancelado por transición
}
```

## Tipos de Timers

### 1. Dwells (permanencia en estado)

Miden cuánto tiempo lleva el residente en un estado.

```rust
fn start_dwells(bed: &mut BedTwin, catalog: &AlarmCatalog) {
    let state = &bed.person.state;
    let dwell_rules = catalog.permanencias_candidatas(state);
    
    for rule in dwell_rules {
        bed.timers.push(Timer {
            rule_id: rule.id.clone(),
            bed_id: bed.bed_id.clone(),
            started_at: bed.person.state_since,
            threshold_minutes: rule.timer_minutes(),
            state: TimerState::Active,
        });
    }
}
```

### 2. Confirmaciones (transiciones pendientes)

Confirman que una transición realmente ocurrió (no fue un artefacto).

```rust
fn start_confirmation(bed: &mut BedTwin, from: PersonState, to: PersonState) {
    bed.timers.push(Timer {
        rule_id: format!("confirm_{:?}_to_{:?}", from, to),
        bed_id: bed.bed_id.clone(),
        started_at: Instante::now(),
        threshold_minutes: 1, // 1 minuto de confirmación
        state: TimerState::Active,
    });
}
```

### 3. Post-caída (refuerzo temporal)

Después de una caída, refuerza la vigilancia por un período.

```rust
fn start_post_fall(bed: &mut BedTwin) {
    bed.timers.push(Timer {
        rule_id: "post_fall_watch".to_string(),
        bed_id: bed.bed_id.clone(),
        started_at: Instante::now(),
        threshold_minutes: 30, // 30 minutos de vigilancia
        state: TimerState::Active,
    });
}
```

## Gestión de Timers

### Cuando llega un perception event

```rust
fn on_perception_event(bed: &mut BedTwin, event: &PerceptionEvent) {
    let old_state = bed.person.state.clone();
    let new_state = detect_transition(event);
    
    if old_state != new_state {
        // 1. Cancelar timers del estado anterior
        bed.cancel_timers_for_state(&old_state);
        
        // 2. Actualizar estado
        bed.person.state = new_state.clone();
        bed.person.state_since = event.timestamp;
        
        // 3. Iniciar timers del nuevo estado
        bed.start_timers_for_state(&new_state);
    }
}
```

### Cuando el tick() detecta un timer que superó el umbral

```rust
fn tick(bed: &mut BedTwin, now: Instante) -> Vec<SceneEvent> {
    let mut outputs = vec![];
    
    for timer in &mut bed.timers {
        if timer.state == TimerState::Active {
            let elapsed = now - timer.started_at;
            let threshold = Duration::from_secs(timer.threshold_minutes as u64 * 60);
            
            if elapsed >= threshold {
                timer.state = TimerState::Fired;
                outputs.push(SceneEvent::timer_triggered(bed, timer));
            }
        }
    }
    
    // Limpiar timers que ya dispararon
    bed.timers.retain(|t| t.state != TimerState::Fired);
    
    outputs
}
```

## Cancelación de Timers

### Cuando hay una transición

```rust
fn cancel_timers_for_state(bed: &mut BedTwin, state: &PersonState) {
    for timer in &mut bed.timers {
        if timer.belongs_to_state(state) && timer.state == TimerState::Active {
            timer.state = TimerState::Cancelled;
        }
    }
}
```

### Cuando se cumplió un dwell (ya no necesita cancelar)

```rust
fn on_timer_fired(bed: &mut BedTwin, timer: &Timer) {
    // El timer ya disparó, no necesita cancelación
    // Pero puede haber otros timers que sí necesiten cancelarse
    // (ej: si el dwell de "out_of_bed" disparó, cancelar el de "standing")
}
```

## Ejemplo Completo

### Timeline

```
02:00 - lying → state_since: 02:00
        Timers iniciados:
          - in_bed_dwell (threshold: 480min)
          - sleep_dwell (threshold: 150min)

02:05 - lying (mismo estado) → sin cambios
        Timers: siguen activos

02:10 - standing → state_since: 02:10
        Timers cancelados:
          - in_bed_dwell ❌
          - sleep_dwell ❌
        Timers iniciados:
          - out_of_bed_dwell (threshold: 10min)
          - standing_dwell (threshold: 5min)

02:15 - lying → state_since: 02:15
        Timers cancelados:
          - out_of_bed_dwell ❌
          - standing_dwell ❌
        Timers iniciados:
          - in_bed_dwell (threshold: 480min)
          - sleep_dwell (threshold: 150min)

02:22 - lying (mismo estado) → sin cambios
        Timers: in_bed_dwell sigue activo (22min de 480)
```

### Ejemplo con dwell que se cumple

```
02:10 - standing → state_since: 02:10
        Timer: out_of_bed_dwell (threshold: 10min)

02:20 - standing (mismo estado)
        Tick detecta: out_of_bed_dwell superado (10min)
        → SceneEvent emitido: dwell_completed
        → out_of_bed_dwell: Fired

02:22 - lying → state_since: 02:22
        Timer: out_of_bed_dwell ya está Fired, se limpia
        Timer: standing_dwell se cancela
```

## Reglas del Catálogo

```toml
[dwells.out_of_bed_dwell]
label = "Mucho tiempo fuera de la cama"
state = "out_of_bed"  # = NOT(in_bed)
dwell_minutes = { min = 0, max = 120, step = 5, default = 10 }
sensitivity = { default = "standard" }

[dwells.in_bed_dwell]
label = "Mucho tiempo en la cama"
state = "in_bed"
dwell_minutes = { min = 0, max = 480, step = 15, default = 300 }
sensitivity = { default = "standard" }

[dwells.standing_dwell]
label = "Mucho tiempo de pie"
state = "out_of_bed.standing"
dwell_minutes = { min = 0, max = 60, step = 5, default = 5 }
sensitivity = { default = "standard" }
```

## Sensitivity

El threshold se ajusta según la sensibilidad:

```rust
fn timer_minutes(rule: &ResolvedRule) -> i32 {
    let base = rule.dwell_minutes;
    let factor = match rule.sensitivity {
        Sensitivity::Low => 1.5,
        Sensitivity::Standard => 1.0,
        Sensitivity::High => 0.5,
    };
    let floor = match rule.sensitivity {
        Sensitivity::Low => 60,
        Sensitivity::Standard => 20,
        Sensitivity::High => 0,
    };
    
    ((base as f64 * factor) as i32).max(floor)
}
```

## Invariantes

1. Un timer solo puede estar en un estado a la vez
2. Un timer activo puede ser cancelado o disparado
3. Un timer disparado se limpia en el próximo tick
4. Un timer cancelado se limpia en el próximo tick
5. `state_since` del bed se usa como `started_at` del timer de dwell
6. Los timers no se persisten (se reconstruyen desde el estado)
