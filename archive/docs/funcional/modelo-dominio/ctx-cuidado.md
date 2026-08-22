# Modelo de dominio: `ctx-cuidado`

## Pregunta del contexto

Que tareas de cuidado se planificaron, que rondas se completaron y que notas de
continuidad dejo el equipo?

## Objetos de dominio

```text
Round (agregado raiz)
  id: RoundId
  wing_id: String (opaco, sin FK)
  status: RoundStatus (in_progress | completed | cancelled)
  scheduled_for: Option<String>
  started_at: Instante
  completed_at: Option<Instante>
  started_by: Id<Actor>
  completed_by: Option<Id<Actor>>
  created_at: Instante
  updated_at: Instante

RoundTask (agregado hijo)
  id: TaskId
  round_id: RoundId
  resident_id: String (opaco, snapshot)
  bed_id: String (opaco, snapshot)
  status: TaskStatus (pending | completed)
  note: Option<String>
  completed_at: Option<Instante>
  completed_by: Option<Id<Actor>>
  created_at: Instante
  updated_at: Instante

CareNote (agregado)
  id: NoteId
  resident_id: String (opaco, sin FK)
  author_id: Id<Actor>
  kind: String (default "general")
  body: String
  duration_min: Option<i32>
  created_at: Instante
  updated_at: Instante
```

### Value objects

| Tipo | Significado |
|---|---|
| `RoundId` | Identificador opaco de ronda. |
| `TaskId` | Identificador opaco de tarea. |
| `NoteId` | Identificador opaco de nota. |
| `RoundStatus` | Enum `in_progress \| completed \| cancelled`. |
| `TaskStatus` | Enum `pending \| completed`. |

## Invariantes

| # | Invariante | Capa |
|---|---|---|
| 1 | Maximo 1 ronda in_progress por ala | Indice parcial `WHERE status = 'in_progress'` + repo |
| 2 | No crear ronda sin residentes asignados | `create_round_in_transaction`: check lista vacia |
| 3 | No completar ronda con tareas pendientes | `complete_round_in_transaction`: count pending |
| 4 | Ronda completada no recibe tareas ni reabre | `update_task_in_transaction`: check status |
| 5 | Completar tarea graba actor + timestamp | Dominio puro |
| 6 | Volver a pending limpia actor + timestamp | Dominio puro |
| 7 | Respuesta de tarea incluye campos de residente/ubicacion | Read model en mana-app |
| 8 | Nota requiere cuerpo no vacio, autor, residente | Dominio puro |
| 9 | Duracion nullable; ausente != cero | Documentacion + tipo `Option<i32>` |

## Tablas

### `rounds`

- `id` TEXT PK
- `wing_id` TEXT NOT NULL
- `status` TEXT NOT NULL (in_progress | completed | cancelled)
- `scheduled_for` TEXT NULL
- `started_at/completed_at` TEXT
- `started_by/completed_by` TEXT
- `created_at/updated_at` TEXT
- Indice unico parcial `(wing_id) WHERE status = 'in_progress'`

### `round_tasks`

- `id` TEXT PK
- `round_id` TEXT NOT NULL FK
- `resident_id/bed_id` TEXT NOT NULL (snapshot)
- `status` TEXT NOT NULL (pending | completed)
- `note` TEXT NULL
- `completed_at/completed_by` TEXT NULL
- `created_at/updated_at` TEXT

### `care_notes`

- `id` TEXT PK
- `resident_id` TEXT NOT NULL
- `author_id` TEXT NOT NULL
- `kind` TEXT NOT NULL (default 'general')
- `body` TEXT NOT NULL
- `duration_min` INTEGER NULL
- `created_at/updated_at` TEXT
