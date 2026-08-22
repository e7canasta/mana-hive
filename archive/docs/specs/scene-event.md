# Spec: Scene Event

## Propósito

Definir el contrato de datos entre **Engine** y **Hub**. El scene event es el estado completo de la escena en un momento dado, emetido por el engine cuando hay un cambio o se cumple una condición.

## Principios

1. **Autocontenido**: cada scene event contiene todo el estado de la escena
2. **Inmutable**: un scene event emitido no se modifica
3. **Temporal**: cada event tiene un timestamp preciso
4. **Enriquecido**: incluye trigger (por qué se emitió)

## Estructura del Scene Event

```rust
struct SceneEvent {
    // === Envelope ===
    event_type: SceneEventType,
    bed_id: String,
    resident_id: Option<String>,
    timestamp: DateTime<Utc>,
    trace_id: Option<String>,
    
    // === Trigger (por qué se emitió) ===
    trigger: TriggerInfo,
    
    // === Estado completo de la escena ===
    poi: PersonOfInterest,
    bed: BedState,
    chair: ChairState,
    wheelchair: WheelchairState,
    walker: WalkerState,
    room: RoomState,
    accompanied_by: Option<StaffRef>,
}
```

## Event Types (Envelope)

```rust
enum SceneEventType {
    // Vino de un perception event del edge
    Perception,
    
    // Se cumplió un dwell timer
    Dwell,
    
    // Hubo un cambio de estado (FSM transition)
    Transition,
    
    // Cambio de objeto o room state
    Change,
}
```

## Trigger Info

```rust
enum TriggerInfo {
    // Perception event del edge
    Perception {
        perception_event_id: String,
        confidence: f64,
    },
    
    // Dwell timer completado
    DwellCompleted {
        rule_id: String,
        duration_minutes: i32,
        threshold_minutes: i32,
    },
    
    // Transición detectada
    TransitionDetected {
        from_state: PersonState,
        to_state: PersonState,
    },
    
    // Cambio de objeto
    ObjectChange {
        object: String,
        from_state: String,
        to_state: String,
    },
}
```

## Person of Interest

```rust
struct PersonOfInterest {
    resident_id: String,
    state: PersonState,
    state_since: Instante,
    location: Location,
    sleeping: Option<bool>,
    confidence: f64,
}
```

## Person States (FSM)

```rust
enum PersonState {
    // En cama (in_bed)
    Lying,
    SittingInBed,
    BedEdge,
    
    // Fuera de cama (out_of_bed)
    Standing,
    InBathroom,
    InRoom,
    InHallway,
    Outdoor,
    
    // Muebles
    InChair,
    InWheelchair,
    
    Unknown,
}
```

### Agrupaciones para Reglas

```rust
// in_bed = {lying, sitting_in_bed, bed_edge}
fn is_in_bed(state: &PersonState) -> bool {
    matches!(state, 
        PersonState::Lying | 
        PersonState::SittingInBed | 
        PersonState::BedEdge
    )
}

// out_of_bed = NOT(in_bed)
fn is_out_of_bed(state: &PersonState) -> bool {
    !is_in_bed(state) && !matches!(state, PersonState::Unknown)
}
```

## Location

```rust
enum Location {
    Bed,
    Bathroom,
    Hallway,
    Room,
    Outdoor,
    Chair,
    Wheelchair,
    Unknown,
}
```

## Scene Objects

```rust
struct BedState {
    occupancy: BedOccupancy,  // occupied, empty, unknown
}

struct ChairState {
    occupancy: ObjectOccupancy,  // occupied, empty, unknown
}

struct WheelchairState {
    occupancy: ObjectOccupancy,  // occupied, empty, unknown
}

struct WalkerState {
    presence: ObjectPresence,  // present, absent, unknown
}

struct RoomState {
    occupancy: RoomOccupancy,
    resident_count: i32,
    staff_count: i32,
    visitor_count: i32,
}

struct StaffRef {
    staff_id: String,
    name: String,
    role: String,
}
```

## Enums de Objetos

```rust
enum BedOccupancy {
    Occupied,
    Empty,
    Unknown,
}

enum ObjectOccupancy {
    Occupied,
    Empty,
    Unknown,
}

enum ObjectPresence {
    Present,
    Absent,
    Unknown,
}

enum RoomOccupancy {
    Empty,
    Resident,
    Staff,
    ResidentAndStaff,
    ResidentAndVisitor,
}
```

## Ejemplos

### Perception Event

```json
{
  "event_type": "perception",
  "bed_id": "118-A",
  "resident_id": "res-001",
  "timestamp": "2026-08-18T02:10:00Z",
  
  "trigger": {
    "perception_event_id": "evt-001",
    "confidence": 0.92
  },
  
  "poi": {
    "resident_id": "res-001",
    "state": "sitting_in_bed",
    "state_since": "2026-08-18T02:08:00Z",
    "location": "bed",
    "sleeping": false,
    "confidence": 0.92
  },
  
  "bed": { "occupancy": "occupied" },
  "chair": { "occupancy": "empty" },
  "wheelchair": { "occupancy": "empty" },
  "walker": { "presence": "present" },
  
  "room": {
    "occupancy": "resident",
    "resident_count": 1,
    "staff_count": 0,
    "visitor_count": 0
  },
  
  "accompanied_by": null
}
```

### Dwell Timer

```json
{
  "event_type": "dwell",
  "bed_id": "118-A",
  "resident_id": "res-001",
  "timestamp": "2026-08-18T02:22:00Z",
  
  "trigger": {
    "rule_id": "out_of_bed_dwell",
    "duration_minutes": 12,
    "threshold_minutes": 10
  },
  
  "poi": {
    "resident_id": "res-001",
    "state": "standing",
    "state_since": "2026-08-18T02:10:00Z",
    "location": "bed",
    "sleeping": false,
    "confidence": 1.0
  },
  
  "bed": { "occupancy": "occupied" },
  "chair": { "occupancy": "empty" },
  "wheelchair": { "occupancy": "empty" },
  "walker": { "presence": "present" },
  
  "room": {
    "occupancy": "resident",
    "resident_count": 1,
    "staff_count": 0,
    "visitor_count": 0
  },
  
  "accompanied_by": null
}
```

## Flujo de Datos

```
Perception Event → Engine (DigitalTwin FSM)
                       │
                       ▼
                   Scene Event ──NATS evt_scene──→ Hub (persist)
                                                 → Sentinel (evaluate rules, create incidents/alerts)
                                                      │
                                                      ▼
                                                  NATS evt_notif → Hub (persist)
                                                                 → Vigilancia (create alerts via API)
```

## Invariantes

1. Cada scene event es autocontenido (no requiere eventos anteriores)
2. `state_since` solo cambia cuando el estado FSM cambia
3. `timestamp` es único y creciente por bed_id
4. `trigger` siempre está presente (nunca null)
5. Los objetos siempre están presentes (aunque con estado Unknown)
