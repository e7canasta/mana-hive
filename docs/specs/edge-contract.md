# Spec: Edge Contract (Perception Event)

## Propósito

Definir el contrato de datos que el edge (IA server / cámara) envía al hub.

## Concepto

El edge es el oído del sistema — detecta lo que pasa en la escena y lo reporta como un perception event. No interpreta, no decide, solo reporta.

## Estructura del Perception Event

```json
{
  "source_event_id": "evt-001",
  "monitor_key": "mana-camera-118",
  "kind": "scene_observation",
  
  "state": "standing",
  "sleeping": false,
  "zone": "bed_area",
  
  "extremities_out_of_bed": true,
  "body_parts_out": ["head", "left_arm"],
  
  "objects": {
    "walker": "present",
    "wheelchair": "absent",
    "bed_rail": "up"
  },
  
  "room": {
    "occupancy": "resident",
    "person_count": 1
  },
  
  "confidence": 0.92,
  "occurred_at": "2026-08-18T02:10:00Z"
}
```

## Campos

### Envelope (obligatorio)

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `source_event_id` | string | ✓ | ID único del evento (idempotencia) |
| `monitor_key` | string | ✓ | Identificador de la cámara/sensor |
| `kind` | string | ✓ | Tipo de evento (siempre "scene_observation") |

### Persona de Interés

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `state` | string | ✓ | FSM state del residente |
| `sleeping` | bool | ✗ | Durmiendo (null = no informado) |
| `zone` | string | ✗ | Zona de la habitación |

### Detección de Cuerpo

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `extremities_out_of_bed` | bool | ✗ | Tiene extremidades fuera de la cama |
| `body_parts_out` | string[] | ✗ | Qué partes del cuerpo están fuera |

### Objetos

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `objects` | map | ✗ | Estado de objetos detectados |

### Habitación

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `room` | object | ✗ | Estado de la habitación |

### Metadata

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `confidence` | float | ✓ | Confianza de la detección (0.0 - 1.0) |
| `occurred_at` | string | ✓ | Timestamp ISO 8601 |

## FSM States (valores de `state`)

```rust
enum PerceptionState {
    // En cama
    Lying,              // "lying"
    SittingInBed,       // "sitting_in_bed"
    BedEdge,            // "bed_edge"
    
    // Fuera de cama
    Standing,           // "standing"
    InBathroom,         // "in_bathroom"
    InRoom,             // "in_room"
    InHallway,          // "in_hallway"
    Outdoor,            // "outdoor"
    
    // Muebles
    InChair,            // "in_chair"
    InWheelchair,       // "in_wheelchair"
    
    // Desconocido
    Unknown,            // "unknown"
}
```

## Body Parts (valores de `body_parts_out`)

```rust
enum BodyPart {
    Head,               // "head"
    LeftArm,            // "left_arm"
    RightArm,           // "right_arm"
    LeftHand,           // "left_hand"
    RightHand,          // "right_hand"
    LeftLeg,            // "left_leg"
    RightLeg,           // "right_leg"
    LeftFoot,           // "left_foot"
    RightFoot,          // "right_foot"
    Torso,              // "torso"
}
```

## Objects (valores de `objects`)

```rust
enum ObjectState {
    Present,            // "present"
    Absent,             // "absent"
    Up,                 // "up" (bed_rail)
    Down,               // "down" (bed_rail)
    Unknown,            // "unknown"
}
```

## Room Occupancy (valores de `room.occupancy`)

```rust
enum RoomOccupancy {
    Empty,              // "empty"
    Resident,           // "resident"
    Staff,              // "staff"
    Visitor,            // "visitor"
}
```

## Ejemplos

### Residente acostado durmiendo

```json
{
  "source_event_id": "evt-002",
  "monitor_key": "mana-camera-118",
  "kind": "scene_observation",
  "state": "lying",
  "sleeping": true,
  "zone": "bed_area",
  "confidence": 0.95,
  "occurred_at": "2026-08-18T03:00:00Z"
}
```

### Residente sentado en borde con andador

```json
{
  "source_event_id": "evt-003",
  "monitor_key": "mana-camera-118",
  "kind": "scene_observation",
  "state": "bed_edge",
  "sleeping": false,
  "zone": "bed_area",
  "extremities_out_of_bed": false,
  "objects": {
    "walker": "present"
  },
  "confidence": 0.88,
  "occurred_at": "2026-08-18T02:05:00Z"
}
```

### Residente con extremidades fuera de cama

```json
{
  "source_event_id": "evt-004",
  "monitor_key": "mana-camera-118",
  "kind": "scene_observation",
  "state": "lying",
  "sleeping": false,
  "zone": "bed_area",
  "extremities_out_of_bed": true,
  "body_parts_out": ["head", "left_arm", "left_hand"],
  "confidence": 0.85,
  "occurred_at": "2026-08-18T02:08:00Z"
}
```

### Residente en baño

```json
{
  "source_event_id": "evt-005",
  "monitor_key": "mana-camera-119",
  "kind": "scene_observation",
  "state": "in_bathroom",
  "sleeping": false,
  "zone": "bathroom",
  "confidence": 0.90,
  "occurred_at": "2026-08-18T02:15:00Z"
}
```

## Invariantes

1. `source_event_id` es único (idempotencia)
2. `monitor_key` es obligatorio
3. `state` es obligatorio
4. `confidence` está en rango [0.0, 1.0]
5. `occurred_at` es ISO 8601
6. El edge NO interpreta — solo reporta
7. El edge NO gestiona timers
8. El edge NO decide alertas
9. Los campos opcionales pueden ser null/ausentes
10. `sleeping` null significa "no informado", no "false"
