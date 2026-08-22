# Spec: Person State (FSM)

## Propósito

Definir los estados de la máquina de estados finitos (FSM) que modela la posición y ubicación de un residente en la escena.

## Principios

1. **Cada residente tiene un FSM independiente** por cada cama que ocupa
2. **Los estados son mutuamente excluyentes** — un residente solo puede estar en un estado a la vez
3. **Las transiciones son deterministas** — dado un estado actual y un evento, el siguiente estado es único
4. **Los estados se agrupan** para evaluar reglas del catálogo de alarmas

## FSM States

```rust
enum PersonState {
    // === IN_BED: residente en la cama ===
    Lying,              // acostado en cama
    SittingInBed,       // incorporado en cama (sentado)
    BedEdge,            // sentado en el borde de la cama
    
    // === OUT_OF_BED: residente fuera de la cama ===
    Standing,           // de pie
    InBathroom,         // dentro del baño
    InRoom,             // en la habitación (fuera de cama, no en baño/pasillo)
    InHallway,          // en el pasillo
    Outdoor,            // en el exterior
    
    // === FURNITURE: residente en muebles ===
    InChair,            // sentado en silla
    InWheelchair,       // en silla de ruedas
    
    // === UNKNOWN: no se puede determinar ===
    Unknown,
}
```

## Agrupaciones para Reglas

El catálogo de alarmas usa agrupaciones de estados para definir reglas:

### `in_bed`

```rust
fn is_in_bed(state: &PersonState) -> bool {
    matches!(state, 
        PersonState::Lying | 
        PersonState::SittingInBed | 
        PersonState::BedEdge
    )
}

// Equivalente a: in_bed = {lying, sitting_in_bed, bed_edge}
```

### `out_of_bed`

```rust
fn is_out_of_bed(state: &PersonState) -> bool {
    !is_in_bed(state) && !matches!(state, PersonState::Unknown)
}

// Equivalente a: out_of_bed = NOT(in_bed)
```

### `in_furniture`

```rust
fn is_in_furniture(state: &PersonState) -> bool {
    matches!(state, 
        PersonState::InChair | 
        PersonState::InWheelchair
    )
}
```

## Transiciones Válidas

### Dentro de in_bed

```
lying ↔ sitting_in_bed
lying ↔ bed_edge
sitting_in_bed ↔ bed_edge
```

### in_bed ↔ out_of_bed

```
lying ↔ standing
sitting_in_bed ↔ standing
bed_edge ↔ standing
```

### out_of_bed ↔ furniture

```
standing ↔ in_chair
standing ↔ in_wheelchair
```

### Transiciones directas (sin pasar por standing)

```
lying ↔ in_chair
sitting_in_bed ↔ in_chair
bed_edge ↔ in_chair
lying ↔ in_wheelchair
sitting_in_bed ↔ in_wheelchair
bed_edge ↔ in_wheelchair
```

### Dentro de out_of_bed

```
standing ↔ in_bathroom
standing ↔ in_hallway
standing ↔ outdoor
in_bathroom ↔ in_hallway (posible)
in_hallway ↔ outdoor (posible)
```

### bed ↔ bathroom (directo)

```
lying ↔ in_bathroom
sitting_in_bed ↔ in_bathroom
```

## Matriz de Transiciones

| Desde \ Hacia | lying | sitting | bed_edge | standing | bathroom | room | hallway | outdoor | chair | wheelchair |
|---------------|-------|---------|----------|----------|----------|------|---------|---------|-------|------------|
| **lying** | - | ✓ | ✓ | ✓ | ✓ | ✓ | - | - | ✓ | ✓ |
| **sitting_in_bed** | ✓ | - | ✓ | ✓ | ✓ | ✓ | - | - | ✓ | ✓ |
| **bed_edge** | ✓ | ✓ | - | ✓ | ✓ | ✓ | - | - | ✓ | ✓ |
| **standing** | ✓ | ✓ | ✓ | - | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **bathroom** | ✓ | ✓ | ✓ | ✓ | - | ✓ | ✓ | - | - | - |
| **room** | ✓ | ✓ | ✓ | ✓ | ✓ | - | ✓ | ✓ | ✓ | ✓ |
| **hallway** | - | - | - | ✓ | ✓ | ✓ | - | ✓ | - | - |
| **outdoor** | - | - | - | ✓ | - | ✓ | ✓ | - | - | - |
| **chair** | ✓ | ✓ | ✓ | ✓ | - | ✓ | - | - | - | - |
| **wheelchair** | ✓ | ✓ | ✓ | ✓ | - | ✓ | - | - | - | - |

## Detección de Extremidades

La IA server puede reportar que extremidades del residente están fuera del borde de la cama. Estos campos viven en `PerceptionEvent` (input) y se reflejan en `PersonTwin.extremities` (estado en el twin).

```rust
// En PerceptionEvent (input desde edge)
struct PerceptionEvent {
    // ...existing fields...
    extremities_out_of_bed: bool,
    body_parts_out: Vec<BodyPart>,
}

// En PersonTwin (estado en el digital twin)
struct Extremities {
    out_of_bed: bool,
    body_parts: Vec<BodyPart>,
}

enum BodyPart {
    Head,
    LeftArm,
    RightArm,
    LeftHand,
    RightHand,
    LeftLeg,
    RightLeg,
    LeftFoot,
    RightFoot,
    Torso,
}
```

### Reglas Relacionadas

```toml
[fall_prevention.limbs_out_while_lying]
label = "Extremidades fuera de la cama (acostado)"
class = "posture"
state = "in_bed.lying"
requires_extremities_out = true
delay_minutes = { min = 0, max = 5, step = 1, default = 1 }

[fall_prevention.limbs_out_while_sitting]
label = "Extremidades fuera de la cama (incorporado)"
class = "posture"
state = "in_bed.sitting_in_bed"
requires_extremities_out = true
delay_minutes = { min = 0, max = 3, step = 1, default = 1 }
```

## Mapeo Perception Event → Person State

| Perception Event State | Person State |
|------------------------|--------------|
| `lying` | `Lying` |
| `sitting` | `SittingInBed` |
| `bed_edge` | `BedEdge` |
| `standing` | `Standing` |
| `in_bathroom` | `InBathroom` |
| `in_room` | `InRoom` |
| `in_hallway` | `InHallway` |
| `outdoor` | `Outdoor` |
| `in_chair` | `InChair` |
| `in_wheelchair` | `InWheelchair` |
| `unknown` | `Unknown` |

## Invariantes

1. Un residente solo puede estar en un estado a la vez
2. `state_since` se actualiza solo cuando el estado cambia
3. Un evento que repite el estado no mueve `state_since`
4. `Unknown` nunca se infiere — solo se asigna cuando la IA no puede determinar
5. La transición `Unknown → CualquierEstado` es siempre válida
6. La transición `CualquierEstado → Unknown` es siempre válida
