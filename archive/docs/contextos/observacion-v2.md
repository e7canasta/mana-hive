# Subsistema de Observación (v2)

Tipo: ciclo de vida de datos, no bounded context de negocio.

## Pregunta

¿Qué informó el detector y cuál es el último estado proyectado de cada cama?

## Por qué no es un `ctx-*`

La observación no decide qué significa una alarma, no revisa incidentes y no administra residentes. Es evidencia externa y una proyección operacional. Su retención, volumen y transporte pueden cambiar sin cambiar el modelo de Registro.

## Arquitectura Actualizada

```
┌─────────────────────────────────────────────────────────────┐
│                    EDGE (IA Server)                          │
│  perception_event: state, location, sleeping, objects,      │
│                    extremities_out_of_bed, body_parts_out    │
└──────────────────────────┬──────────────────────────────────┘
                           │ POST /internal/v1/events
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    MANA HUB (Persistencia)                   │
│  • Recibe perception_event                                   │
│  • Valida, resuelve monitor_key → bed → resident             │
│  • Persiste en sensor_events (raw, inmutable)                │
│  • Forward a engine                                          │
│  • NO actualiza current_bed_states                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ perception_event
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    MANA ENGINE (Digital Twin)                 │
│  • Mantiene twin propio (FSM, objetos, timers)               │
│  • Produce scene_events (estado completo de la escena)       │
│  • Emite: perception, dwell, transition, change              │
└──────────────────────────┬──────────────────────────────────┘
                           │ scene_event
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    MANA HUB (Notificación)                   │
│  • Recibe scene_event                                        │
│  • UPDATE current_bed_states (system of record)              │
│  • Persiste en event_store (event sourcing)                  │
│  • Evalúa presets (day/night, risk_level)                    │
│  • Decide: off / notify / alarm                              │
│  • Notifica a UI                                             │
└─────────────────────────────────────────────────────────────┘
```

## Cambios Clave vs v1

| Aspecto | v1 (actual) | v2 (nueva) |
|---------|-------------|------------|
| current_bed_states | Hub actualiza en ingestión | Hub actualiza desde scene_event |
| Timers/dwells | Engine calcula umbral, no persiste | Engine mantiene timers activos |
| Digital twin | No existe (re-hidrata desde hub) | Engine mantiene twin propio |
| Scene events | No existe | Engine produce, hub consume |
| Presets | Engine evalúa | Hub evalúa |

## Tablas

### `sensor_events` (sin cambios)

```text
id                 TEXT PRIMARY KEY
source_event_id    TEXT NOT NULL UNIQUE
bed_id             TEXT NOT NULL
resident_id        TEXT NULL
monitor_key        TEXT NOT NULL
kind               TEXT NOT NULL
room_state         TEXT NULL
substate           TEXT NULL
zone               TEXT NULL
state              TEXT NULL
sleeping           INTEGER NULL
alert_level        TEXT NULL
occurred_at        TEXT NOT NULL
received_at        TEXT NOT NULL
payload_json       TEXT NOT NULL
```

### `current_bed_states` (ahora es output del engine)

```text
bed_id             TEXT PRIMARY KEY
resident_id        TEXT NULL
room_state         TEXT NULL
state              TEXT NOT NULL
substate           TEXT NULL
sleeping           INTEGER NOT NULL DEFAULT 0
alert_level        TEXT NOT NULL DEFAULT 'low'
state_since        TEXT NULL
updated_at         TEXT NOT NULL
source             TEXT NOT NULL
source_event_id    TEXT NULL
```

**Cambio**: `current_bed_states` se actualiza cuando el hub recibe un `scene_event`, no cuando ingesta un perception event.

### `scene_states` (nueva - twin del engine)

```text
bed_id             TEXT PRIMARY KEY
resident_id        TEXT NULL
scene_state        TEXT NOT NULL
person_state       TEXT NOT NULL
person_state_since TEXT NOT NULL
objects_json       TEXT NOT NULL
room_json          TEXT NOT NULL
timers_json        TEXT NOT NULL
updated_at         TEXT NOT NULL
```

### `event_store` (nueva - event sourcing)

```text
id                 TEXT PRIMARY KEY
bed_id             TEXT NOT NULL
event_type         TEXT NOT NULL
payload_json       TEXT NOT NULL
created_at         TEXT NOT NULL
```

## Contrato de Eventos

### Perception Event (edge → hub)

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
  "occurred_at": "2026-08-18T02:10:00Z",
  "confidence": 0.92
}
```

### Scene Event (engine → hub)

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
    "state": "standing",
    "state_since": "2026-08-18T02:10:00Z",
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

## Invariantes

1. `source_event_id` hace idempotente la ingesta
2. Un evento es inmutable después de aceptarse
3. `received_at` lo asigna el hub; `occurred_at` viene de la fuente
4. Unknown no equivale a false o cero
5. El estado actual es reemplazable y reconstruible
6. Cambiar el ocupante de una cama limpia su proyección
7. La frescura se deriva de `updated_at`
8. El detector informa observaciones; la política decide si crea alerta

## Read Models

Los read models se componen desde scene events, no desde perception events:

- `GET /api/v1/wings/{wingId}/board` — compone Residencia + Población + Observación
- `GET /api/v1/residents/{residentId}/current-state` — estado actual desde current_bed_states
- `GET /api/v1/residents/{residentId}/events` — últimos eventos desde sensor_events
- `GET /api/v1/residents/{residentId}/timeline` — línea de tiempo unificada
- `GET /api/v1/companion/rooms` — planograma con ocupantes

## Separación Futura

```
bridge → perception event → hub → engine
                                → scene_event → hub → projections
                                → scene_event → parquet
                                → scene_event → duckdb
```

La API pública permanece estable mientras almacenamiento y transporte se mueven detrás del contrato.
