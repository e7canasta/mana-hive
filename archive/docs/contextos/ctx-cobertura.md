# Contexto `ctx-cobertura`

Clase: soporte.

## Pregunta

Quien trabaja en el hogar y que grupo cubria una unidad funcional en un
instante determinado?

## Lenguaje y ownership

Este contexto posee grupos de staff, sus miembros, la grilla laboral de la
residencia y la cobertura de cada ala. Un turno laboral no es el eje `day` o
`night` de la politica de alarmas. Una casa puede definir `morning`, `afternoon`
y `night` sin cambiar la semantica de las alarmas del residente.

Referencia usuarios, residencias y alas mediante IDs opacos. No los posee.

## Agregados

- `StaffGroup`: named group associated with one facility.
- `ShiftGrid`: ordered work shifts for one facility.
- `WingCoverage`: temporal assignment of a staff group to a wing and a work
  shift.

Membership is modeled as a temporal association rather than a permanent array,
because answering who covered a past alert requires membership history too.

## Tablas

### `staff_groups`

```text
id             TEXT PRIMARY KEY
facility_id    TEXT NOT NULL
name           TEXT NOT NULL
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

### `staff_group_members`

```text
id             TEXT PRIMARY KEY
staff_group_id TEXT NOT NULL
user_id        TEXT NOT NULL
valid_from     TEXT NOT NULL
valid_to       TEXT NULL
created_at     TEXT NOT NULL
```

Hay como maximo una membresia valida para un usuario y grupo en un instante.

### `facility_shifts`

```text
id             TEXT PRIMARY KEY
facility_id    TEXT NOT NULL
key            TEXT NOT NULL
label          TEXT NOT NULL
start_minute   INTEGER NOT NULL       -- 0..1439, local facility time
sort_order     INTEGER NOT NULL
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

`start_minute` es mas preciso que una hora y mantiene una grilla local simple.
El ultimo turno cruza medianoche hasta el primero.

### `unit_shift_coverages`

```text
id             TEXT PRIMARY KEY
wing_id        TEXT NOT NULL
staff_group_id TEXT NULL
shift_key      TEXT NOT NULL
valid_from     TEXT NOT NULL
valid_to       TEXT NULL
created_at     TEXT NOT NULL
created_by     TEXT NULL
```

Una cobertura abierta es la asignacion actual. Limpiarla cierra el intervalo en
vez de borrar la historia.

## Invariantes

1. A facility has at least one valid shift before a coverage can be assigned.
2. Shift keys are unique within a facility.
3. Two shifts cannot start at the same local minute.
4. Shift keys are validated against the facility grid, not a global enum.
5. A wing has at most one coverage per shift at an instant.
6. A staff group belongs to the same facility as the wing it covers.
7. A member is an active user at the time a new membership starts.
8. Replacing a grid closes coverage using removed shift keys and reports how
   many assignments became uncovered.
9. Historical queries use `valid_from <= at` and `(valid_to IS NULL OR at <
   valid_to)`.
10. Coverage data never changes alarm policy day/night semantics.

## API

### Shift grid

- `GET /api/v1/facilities/{facilityId}/shifts`
- `PUT /api/v1/facilities/{facilityId}/shifts`

La respuesta del reemplazo es:

```json
{
  "facility_id": "facility-1",
  "shifts": [],
  "coverages_cleared": 0
}
```

### Groups and membership

- `GET /api/v1/staff-groups?facility_id={facilityId}`
- `GET /api/v1/staff-groups/{groupId}`
- `POST /api/v1/staff-groups`
- `PATCH /api/v1/staff-groups/{groupId}`
- `PUT /api/v1/staff-groups/{groupId}/members`

El comando de reemplazo crea y cierra intervalos de membresia cuando hace falta;
no borra miembros historicos.

### Wing coverage

- `GET /api/v1/wings/{wingId}/coverage?at={ISO instant}`
- `PUT /api/v1/wings/{wingId}/coverage`

El parametro opcional `at` es parte del significado del endpoint, no solo un
filtro. La respuesta incluye el turno laboral resuelto y los miembros del grupo
validos en ese instante.

## Puertos entre contextos

- `UserLookup` for active users and display names;
- `FacilityLookup` for timezone and facility membership;
- `WingLookup` for facility identity.

Notification selection in Vigilance asks `mana-app` for a coverage read model;
this context does not send notifications.

## Tests

- default grid and custom three-shift grid;
- duplicate key and duplicate start conflict;
- coverage at now and at a historical instant;
- membership replacement preserves history;
- removed shift closes dependent coverage;
- cross-facility group/wing rejection;
- exact staffing client shapes.

## No posee

- users or permissions;
- facilities and wings;
- alarm `day`/`night` policy;
- notification transport;
- audit storage.
