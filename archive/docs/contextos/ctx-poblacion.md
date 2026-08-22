# Contexto `ctx-poblacion`

Clase: soporte.

## Pregunta

Que residentes forman parte del hogar, cual es su estado de admision y que cama
ocuparon en cada intervalo?

## Lenguaje y ownership

Este contexto posee el padron de residentes, su ciclo de vida, atributos y
historia de asignaciones. No posee camas fisicas, estado de monitor, perfiles de
alarma ni observaciones clinicas.

El termino publico es `resident`. La relacion fisica es `bed assignment`, no
`bed ownership`: un residente puede estar activo sin una asignacion.

## Agregados

### `Resident`

Posee identidad, estado de admision y atributos del residente.

### `BedAssignment`

Agregado de asociacion con su propia frontera de consistencia. Es separado
porque la unicidad abarca todos los residentes y camas, no solo un agregado
`Resident`.

### `ResidentAttribute`

Afirmacion fechada como `fall_risk` o `wandering`, con provenance. No es un
diagnostico ni un array libre de traits.

## Tablas

### `residents`

```text
id             TEXT PRIMARY KEY
external_id    TEXT NULL UNIQUE
full_name      TEXT NOT NULL
birth_date     TEXT NULL                 -- YYYY-MM-DD
admission_date TEXT NULL                 -- YYYY-MM-DD
status         TEXT NOT NULL             -- active | discharged
discharged_at  TEXT NULL
discharged_by  TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

`status` es un hecho del ciclo clinico, no el flag generico de retiro.

### `resident_bed_assignments`

```text
id             TEXT PRIMARY KEY
resident_id    TEXT NOT NULL
bed_id         TEXT NOT NULL             -- opaque BedId from Residence
starts_at      TEXT NOT NULL
ends_at        TEXT NULL
created_at     TEXT NOT NULL
created_by     TEXT NULL
```

Indices unicos parciales imponen una asignacion abierta por residente y una por
cama. El solapamiento historico de intervalos se rechaza en el repositorio,
dentro de la transaccion de escritura.

Las foreign keys a `bed_id` y `created_by` no cruzan contextos a proposito.
`mana-app` las valida mediante puertos de Residencia e Identidad.

### `resident_attributes`

```text
id             TEXT PRIMARY KEY
resident_id    TEXT NOT NULL
code           TEXT NOT NULL
value          TEXT NOT NULL
source         TEXT NOT NULL
source_ref     TEXT NULL
recorded_by    TEXT NULL
recorded_at    TEXT NOT NULL
valid_from     TEXT NOT NULL
valid_to       TEXT NULL
```

El vocabulario permitido de `code` y valores se valida en el limite de
Politica/catalogo. La provenance es explicita: quien o que afirmo el atributo y
cuando.

## Invariantes

1. A resident has at most one open assignment.
2. A bed has at most one open assignment.
3. Creating an assignment closes the active assignment of the resident and the
   active assignment of the bed in the same transaction.
4. Assignment intervals are ordered and do not overlap for either side.
5. Releasing a bed does not discharge its resident.
6. Discharging a resident closes its open assignment but is a separate business
   action.
7. Discharge dates cannot precede admission dates.
8. An attribute has a source and a recorded timestamp.
9. An assignment change asks `mana-app` to clear projections for both affected
   beds.
10. A resident can remain active and temporarily have no assigned bed.

## API

### Residents

- `GET /api/v1/residents?q={query}` -> `{ residents: ResidentListItem[] }`
- `GET /api/v1/residents/{residentId}` -> `ResidentRecord`
- `POST /api/v1/residents` -> `201 ResidentRecord`
- `PATCH /api/v1/residents/{residentId}` -> `{ resident: ResidentRecord }`
- `POST /api/v1/residents/{residentId}/discharge` -> discharge result

### Assignments

- `GET /api/v1/residents/{residentId}/assignments`
- `POST /api/v1/residents/{residentId}/assignments`
- `DELETE /api/v1/beds/{bedId}/assignment`

El delete devuelve la asignacion terminada. Liberar una cama libre es un
`409 CONFLICT` deliberado, no un exito idempotente.

La lista puede incluir habitacion y ala actuales como read model. El agregado de
asignacion guarda solo sus IDs e intervalo.

## Servicios de aplicacion y cruces

`mana-app` posee la transaccion para:

```text
assign resident to bed
  -> validar residente en Poblacion
  -> validar cama en Residencia
  -> close assignment intervals
  -> clear Observation projection for old and new beds
  -> append audit entry
```

`ctx-poblacion` nunca importa Residencia u Observacion. El perfil de politica se
identifica por `ResidentId`, por lo que mudar un residente no mueve ni recrea la
politica.

## Tests

- create, move, release and discharge flow;
- one open assignment per resident and bed under concurrent writes;
- `409` when releasing a free bed;
- invalid date and interval overlap rejection;
- active resident without bed;
- attribute provenance and vocabulary validation;
- projection clearing on both sides of a move;
- exact resident and assignment client shapes.

## No posee

- bed existence or detector bindings;
- sensor events or current state;
- alarm configuration;
- incidents and daily summaries;
- staff coverage.
