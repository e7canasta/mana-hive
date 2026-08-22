# Casos de uso: `ctx-cuidado`

## Frontera funcional

`ctx-cuidado` es propietario de las rondas operativas, tareas de ronda y notas
de cuidado. Nunca importa `ctx-residencia`, `ctx-poblacion` ni
`ctx-observacion`; los IDs de ala, residente y cama son opacos y la validacion
de existencia vive en `mana-app`.

## Reglas del contexto

- Hay como maximo una ronda `in_progress` por ala.
- No se puede crear una ronda sin al menos un residente asignado.
- Una ronda no puede completarse mientras haya una tarea pendiente.
- Una ronda completada no puede recibir tareas nuevas ni reabrirse.
- Completar una tarea graba actor y timestamp.
- Volver una tarea a pending limpia actor y timestamp.
- Una nota requiere cuerpo no vacio, autor y residente.
- La duracion de una nota es nullable; ausente no significa cero.

## CUI-01 - Crear ronda

**Objetivo:** Iniciar una ronda de visita a un ala.

**Actor primario:** Staff con capability `rounds.manage`.

**Disparador:** HTTP POST `/api/v1/rounds`.

**Precondiciones:** Sesion autenticada; ala existe; hay residentes asignados a
camas del ala.

**Flujo principal:**

1. El staff envia `{ wing_id }`.
2. El sistema snapshot de asignaciones actuales como tareas pendientes.
3. Crea la ronda con `status = in_progress`.
4. Devuelve `201 CREATED` con el `Round`.

**Alternos y excepciones:**

- Ya existe una ronda `in_progress` para este ala -> 409 CONFLICT
- No hay residentes asignados a camas del ala -> 422 VALIDATION_ERROR

**Postcondiciones:** La ronda existe con tareas pendientes para cada residente
asignado a una cama del ala.

**Realizacion:** `mana-app::AppState::create_round` -> `CareStore::create_round`

## CUI-02 - Completar ronda con tareas pendientes

**Objetivo:** Verificar que no se puede completar una ronda con tareas
pendientes.

**Disparador:** HTTP PATCH `/api/v1/rounds/{roundId}` con `{ status: "completed" }`.

**Resultado esperado:** 409 CONFLICT con mensaje "No se puede completar una
ronda con tareas pendientes".

## CUI-03 - Completar tareas y ronda

**Objetivo:** Completar todas las tareas de una ronda y luego la ronda.

**Disparador:** HTTP PATCH `/api/v1/round-tasks/{taskId}` y luego PATCH
`/api/v1/rounds/{roundId}`.

**Flujo principal:**

1. El staff completa cada tarea con `{ status: "completed", note? }`.
2. El sistema graba actor y timestamp en cada tarea.
3. Cuando no quedan tareas pendientes, el staff completa la ronda.
4. El sistema cambia el status a `completed` y graba `completed_at/by`.

**Alternos y excepciones:**

- Tarea en ronda completada -> 409
- Ronda con tareas pendientes -> 409

## CUI-04 - Rechazar edicion de ronda completada

**Objetivo:** Verificar que una ronda completada no puede recibir cambios.

**Disparador:** PATCH sobre una ronda ya completada.

**Resultado esperado:** 409 CONFLICT.

## CUI-05 - Crear nota de cuidado

**Objetivo:** Registrar una nota de continuidad para un residente.

**Disparador:** HTTP POST `/api/v1/residents/{residentId}/notes`.

**Flujo principal:**

1. El staff envia `{ body, kind?, duration_min? }`.
2. El sistema crea la nota con el autor de la sesion.
3. Devuelve `201 CREATED` con la `CareNote`.

**Alternos y excepciones:**

- `body` vacio -> 422 VALIDATION_ERROR
- `kind` ausente -> default "general"
- `duration_min` ausente -> null (no es cero)

**Postcondiciones:** La nota existe como append-only.

## CUI-06 - Listar notas de un residente

**Objetivo:** Consultar el historial de notas de cuidado de un residente.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/notes?limit={n}`.

**Postcondiciones:** Devuelve las notas ordenadas por fecha descendente.
