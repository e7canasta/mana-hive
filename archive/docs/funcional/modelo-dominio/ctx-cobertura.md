# Modelo de dominio: `ctx-cobertura`

## Pregunta del contexto

Quien trabaja en el hogar y que grupo cubria una unidad funcional en un
instante determinado?

## Objetos de dominio

```text
StaffGroup (agregado raiz)
  id: StaffGroupId
  facility_id: String (opaco, sin FK)
  name: String
  retired_at: Option<Instante>
  retired_by: Option<Id<Actor>>
  created_at: Instante
  updated_at: Instante

StaffGroupMembership (agregado temporal)
  id: MembershipId
  staff_group_id: StaffGroupId
  user_id: String (opaco, sin FK)
  valid_from: Instante
  valid_to: Option<Instante>
  created_at: Instante

FacilityShift (agregado)
  id: ShiftId
  facility_id: String
  key: String (unico por facility)
  label: String
  start_minute: i32 (0..1439, hora local)
  sort_order: i32
  retired_at: Option<Instante>
  retired_by: Option<Id<Actor>>
  created_at: Instante
  updated_at: Instante

WingCoverage (agregado temporal)
  id: CoverageId
  wing_id: String (opaco, sin FK)
  staff_group_id: Option<String> (opaco, sin FK)
  shift_key: String
  valid_from: Instante
  valid_to: Option<Instante>
  created_at: Instante
  created_by: Option<Id<Actor>>
```

### Value objects

| Tipo | Significado |
|---|---|
| `StaffGroupId` | Identificador opaco de grupo. |
| `MembershipId` | Identificador opaco de membresia. |
| `ShiftId` | Identificador opaco de turno. |
| `CoverageId` | Identificador opaco de cobertura. |

## Invariantes

| # | Invariante | Capa |
|---|---|---|
| 1 | Facility tiene shifts antes de cobertura | mana-app: `ensure_shift_exists` |
| 2 | Shift key unica por facility | Indice unico `(facility_id, key) WHERE retired_at IS NULL` |
| 3 | Dos shifts no empiezan en el mismo minuto | Indice unico `(facility_id, start_minute) WHERE retired_at IS NULL` |
| 4 | Shift keys validadas contra grilla local | Repo: lookup por facility_id |
| 5 | Maximo 1 cobertura por ala+turno en instante | Indice parcial + `assign_coverage_in_transaction` |
| 6 | Staff group pertenece a misma facility que ala | `ensure_group_facility` en mana-app |
| 7 | Miembro es usuario activo al inicio | Validacion en mana-app (port) |
| 8 | Reemplazo de grilla cierra coberturas afectadas | `replace_grid_in_transaction` |
| 9 | Queries historicas usan `valid_from <= at < valid_to` | Repositorio: filtros temporales |
| 10 | Cobertura no cambia semantica de alarmas | Documentacion |

## Tablas

### `staff_groups`

- `id` TEXT PK
- `facility_id` TEXT NOT NULL
- `name` TEXT NOT NULL
- `retired_at/retried_by` TEXT NULL (soft delete)
- Indice unico parcial `(facility_id, name) WHERE retired_at IS NULL`

### `staff_group_members`

- `id` TEXT PK
- `staff_group_id` TEXT NOT NULL FK
- `user_id` TEXT NOT NULL
- `valid_from` TEXT NOT NULL
- `valid_to` TEXT NULL
- Indice unico parcial `(user_id, staff_group_id) WHERE valid_to IS NULL`

### `facility_shifts`

- `id` TEXT PK
- `facility_id` TEXT NOT NULL
- `key` TEXT NOT NULL
- `label` TEXT NOT NULL
- `start_minute` INTEGER NOT NULL (0..1439)
- `sort_order` INTEGER NOT NULL
- `retired_at/retired_by` TEXT NULL
- Indices unicos parciales: key y start_minute por facility

### `unit_shift_coverages`

- `id` TEXT PK
- `wing_id` TEXT NOT NULL
- `staff_group_id` TEXT NULL
- `shift_key` TEXT NOT NULL
- `valid_from/valid_to` TEXT
- `created_at/created_by` TEXT
- Indice unico parcial `(wing_id, shift_key) WHERE valid_to IS NULL`
