# Contexto `ctx-cuidado`

Clase: soporte.

## Pregunta

Que tareas de cuidado se planificaron, que rondas se completaron y que notas de
continuidad dejo el equipo?

## Lenguaje y ownership

Este contexto posee rondas operativas, tareas de ronda y notas de cuidado. No
posee identidad, asignaciones, incidentes clinicos ni eventos de sensores.

Una ronda toma una fotografia de las personas y camas asignadas al comenzar. No
se recalcula desde la ocupacion actual despues del hecho.

## Agregados

### `Round`

Raiz de una visita a un ala. Posee su conjunto de tareas y ciclo de vida.

### `CareNote`

Registro de continuidad escrito para un residente. Las notas solo son mutables
si el producto agrega explicitamente una politica de edicion; el primer diseno
trata el cuerpo como append-only y las correcciones como notas nuevas.

## Tablas

### `rounds`

```text
id             TEXT PRIMARY KEY
wing_id        TEXT NOT NULL
status         TEXT NOT NULL          -- in_progress | completed | cancelled
scheduled_for  TEXT NULL
started_at     TEXT NOT NULL
completed_at   TEXT NULL
started_by     TEXT NOT NULL
completed_by   TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

### `round_tasks`

```text
id             TEXT PRIMARY KEY
round_id       TEXT NOT NULL
resident_id    TEXT NOT NULL
bed_id         TEXT NOT NULL
status         TEXT NOT NULL          -- pending | completed
note           TEXT NULL
completed_at   TEXT NULL
completed_by   TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

Los IDs de residente y cama son una fotografia de la asignacion al crear la
ronda.

### `care_notes`

```text
id             TEXT PRIMARY KEY
resident_id    TEXT NOT NULL
author_id      TEXT NOT NULL
kind           TEXT NOT NULL DEFAULT 'general'
body           TEXT NOT NULL
duration_min   INTEGER NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

## Invariantes

1. Hay como maximo una ronda `in_progress` por ala.
2. No se puede crear una ronda sin al menos un residente asignado.
3. Una ronda no puede completarse mientras haya una tarea pendiente.
4. Una ronda completada no puede recibir tareas nuevas ni reabrirse mediante la
   API.
5. Completing a task records actor and timestamp together with status.
6. Returning a pending task clears completion actor and timestamp.
7. La respuesta de una tarea incluye los campos de residente y ubicacion del
   read model porque el cliente los necesita despues de un PATCH.
8. Una nota requiere cuerpo no vacio, autor e ID de residente.
9. La duracion de una nota es nullable; duracion ausente no significa cero.

## API

- `GET /api/v1/rounds/current?wing_id={wingId}`
- `GET /api/v1/rounds?wing_id={wingId}&limit={n}`
- `POST /api/v1/rounds` with `{ "wing_id": "..." }`
- `PATCH /api/v1/rounds/{roundId}` with `{ "status": "completed" }`
- `PATCH /api/v1/round-tasks/{taskId}` with status and optional note
- `GET /api/v1/residents/{residentId}/care?days={n}`
- `GET /api/v1/residents/{residentId}/notes?limit={n}`
- `POST /api/v1/residents/{residentId}/notes`

Los envelopes actuales del cliente se preservan donde la semantica es clara:
`{ round }`, `{ task }`, `{ notes }` and `{ note }`.

## Puertos entre contextos

- `WingLookup` and `ResidentAssignmentSnapshot` when a round starts;
- `ResidentLookup` for display data;
- `AuditPort` for round and note mutations.

Ninguna query de este contexto hace join directo a tablas de Residencia o
Poblacion.

## Tests

- create round, reject second active round;
- reject empty wing and complete-with-pending cases;
- complete tasks and then round;
- reject edits on completed round/task;
- pending transition clears actor/date;
- task response includes resident and room fields;
- care period derivation and nullable duration;
- note creation capability and audit entry.

## No posee

- assignment history;
- staff coverage;
- incidents or summaries;
- current monitoring state;
- resident clinical attributes.
