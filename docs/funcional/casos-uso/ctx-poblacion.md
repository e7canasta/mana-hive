# Casos de uso: `ctx-poblacion`

## Frontera funcional

`ctx-poblacion` es propietario del padron de residentes, su ciclo de vida de
admision/egreso, las asignaciones residente-cama y los atributos con
provenance. Nunca importa `ctx-residencia` ni `ctx-observacion`; las
coordenadas de cama son opacas (`BedRef`) y la validacion de existencia vive en
`mana-app`.

## Reglas del contexto

- Un residente tiene exactamente un estado: `active` o `discharged`.
- El egreso es una accion de negocio separada de la liberacion de camas.
- Una cama puede tener a lo sumo una asignacion abierta a la vez.
- Un residente puede tener a lo sumo una asignacion abierta a la vez.
- Asignar una nueva cama cierra la asignacion abierta anterior (mudanza).
- Liberar una cama sin asignacion abierta es un `409 CONFLICT` deliberado.
- La fecha de egreso no puede preceder a la fecha de ingreso.
- Un residente egresado no puede recibir nuevas asignaciones.
- Los atributos requieren `source` y `recorded_at` (provenance obligatorio).
- Las fechas de calendario son `NaiveDate` (YYYY-MM-DD); los timestamps son
  `Instante` (RFC3339 millis).

## POP-01 - Alta de residente

**Objetivo:** Registrar un nuevo residente en el padron.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP POST `/api/v1/residents`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El staff envia `{ full_name, external_id?, birth_date?, admission_date? }`.
2. El sistema crea el residente con `status = active`.
3. Devuelve `201 CREATED` con el `ResidentRecord`.

**Alternos y excepciones:**

- `full_name` vacio o ausente -> 422 VALIDATION_ERROR
- `birth_date` o `admission_date` con formato invalido -> 422
- Token invalido -> 401
- Capability insuficiente -> 403

**Postcondiciones:** El residente existe en el padron con `status = active`.

**Realizacion:** `mana-app::AppState::create_resident` -> `PopulationStore::create_resident_in_transaction` -> `POST /api/v1/residents`

## POP-02 - Actualizar residente

**Objetivo:** Modificar datos de un residente existente.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP PATCH `/api/v1/residents/{residentId}`.

**Precondiciones:** El residente existe.

**Flujo principal:**

1. El staff envia los campos a actualizar (parciales).
2. El sistema valida y aplica los cambios.
3. Devuelve `200 OK` con el `ResidentRecord` actualizado.

**Alternos y excepciones:**

- Residente inexistente -> 404
- Ningun campo proporcionado -> 422 EMPTY_UPDATE
- Campos vacios o con formato invalido -> 422

**Postcondiciones:** Los campos indicados del residente estan actualizados.

**Realizacion:** `mana-app::AppState::update_resident` -> `PopulationStore::update_resident_in_transaction` -> `PATCH /api/v1/residents/{residentId}`

## POP-03 - Listar residentes

**Objetivo:** Consultar el padron con filtro opcional por nombre.

**Actor primario:** Staff con capability `master.structure.read`.

**Disparador:** HTTP GET `/api/v1/residents?q=...`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El staff consulta el padron (con o sin filtro `q`).
2. El sistema compone el read model: residentes + asignaciones abiertas +
   camas de Residencia (para mostrar habitacion y ala).
3. Devuelve `200 OK` con `{ residents: [...] }`.

**Alternos y excepciones:**

- Sin residentes -> lista vacia (no es error)
- Token invalido -> 401

**Postcondiciones:** No modifica estado.

**Realizacion:** `mana-app::AppState::list_residents` -> `PopulationStore::list_residents` + `list_open_assignments` + `ResidenceStore::list_beds_all` -> `GET /api/v1/residents`

## POP-04 - Detalle de residente

**Objetivo:** Obtener el registro completo de un residente.

**Actor primario:** Staff con capability `master.structure.read`.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}`.

**Precondiciones:** El residente existe.

**Flujo principal:**

1. El staff consulta un residente por ID.
2. Devuelve `200 OK` con el `ResidentRecord` (sin read model de habitacion).

**Alternos y excepciones:**

- Residente inexistente -> 404

**Postcondiciones:** No modifica estado.

**Realizacion:** `mana-app::AppState::resident_detail` -> `PopulationStore::get_resident` -> `GET /api/v1/residents/{residentId}`

## POP-05 - Asignar cama

**Objetivo:** Vincular un residente a una cama, cerrando asignaciones previas
de ambos lados.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP POST `/api/v1/residents/{residentId}/assignments`.

**Precondiciones:** El residente existe y esta activo; la cama existe y esta
activa.

**Flujo principal:**

1. El staff envia `{ bed_id }`.
2. El sistema valida que el residente esta activo (Poblacion).
3. El sistema valida que la cama existe y esta activa (Residencia,
   `ensure_bed_active_in_transaction`).
4. En una sola transaccion:
   - Cierra la asignacion abierta del residente (si tiene).
   - Cierra la asignacion abierta de la cama (si tiene).
   - Crea la nueva asignacion.
5. Registra entradas de auditoria para las asignaciones cerradas y la nueva.
6. Devuelve `201 CREATED` con el `BedAssignmentRecord`.

**Alternos y excepciones:**

- Residente inexistente -> 404
- Residente egresado -> 409 (la asignacion se rechaza porque no esta activo;
  en realidad la validacion es en `assign_in_transaction` que verifica
  `ensure_resident_active`)
- Cama inexistente o inactiva -> 404
- Solapamiento de intervalos -> 422

**Postcondiciones:** El residente tiene una asignacion abierta a la cama
indicada; las asignaciones previas de ambos lados estan cerradas.

**Realizacion:** `mana-app::AppState::assign_bed` -> `PopulationStore::assign_in_transaction` + `ResidenceStore::ensure_bed_active_in_transaction` -> `POST /api/v1/residents/{residentId}/assignments`

## POP-06 - Listar asignaciones

**Objetivo:** Consultar el historial de asignaciones de un residente.

**Actor primario:** Staff con capability `master.structure.read`.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/assignments`.

**Precondiciones:** El residente existe.

**Flujo principal:**

1. El staff consulta las asignaciones de un residente.
2. Devuelve `200 OK` con `{ assignments: [...] }` ordenado por `starts_at` asc.

**Alternos y excepciones:**

- Residente inexistente -> 404
- Sin asignaciones -> lista vacia

**Postcondiciones:** No modifica estado.

**Realizacion:** `mana-app::AppState::list_assignments` -> `PopulationStore::list_assignments` -> `GET /api/v1/residents/{residentId}/assignments`

## POP-07 - Liberar cama

**Objetivo:** Cerrar la asignacion abierta de una cama.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP DELETE `/api/v1/beds/{bedId}/assignment`.

**Precondiciones:** La cama existe.

**Flujo principal:**

1. El staff indica la cama a liberar.
2. El sistema cierra la asignacion abierta de esa cama.
3. Registra entrada de auditoria.
4. Devuelve `200 OK` con el `BedAssignmentRecord` cerrado.

**Alternos y excepciones:**

- Cama sin asignacion abierta (ya libre) -> 409 CONFLICT (deliberado)
- Cama inexistente -> 404

**Postcondiciones:** La cama no tiene asignacion abierta.

**Realizacion:** `mana-app::AppState::release_bed` -> `PopulationStore::release_in_transaction` -> `DELETE /api/v1/beds/{bedId}/assignment`

## POP-08 - Egresar residente

**Objetivo:** Dar de alta al residente cerrando su asignacion abierta.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP POST `/api/v1/residents/{residentId}/discharge`.

**Precondiciones:** El residente existe y esta activo.

**Flujo principal:**

1. El staff envia `{ discharged_at? }` (si se omite, usa la fecha actual).
2. En una sola transaccion:
   - Cambia el estado del residente a `discharged`.
   - Cierra la asignacion abierta del residente (si tiene).
3. Registra entradas de auditoria para el egreso y la asignacion cerrada.
4. Devuelve `200 OK` con el `ResidentRecord`.

**Alternos y excepciones:**

- Residente inexistente -> 404
- Residente ya egresado -> 409
- Fecha de egreso anterior a la de ingreso -> 422

**Postcondiciones:** El residente tiene `status = discharged`,
`discharged_at` y `discharged_by` set; su asignacion abierta (si tenia) esta
cerrada.

**Realizacion:** `mana-app::AppState::discharge_resident` -> `PopulationStore::discharge_in_transaction` -> `POST /api/v1/residents/{residentId}/discharge`

## Fuera del corte actual

- Rutas HTTP para atributos (solo dominio y tests en F3).
- Proyecciones de Observacion (hook documentado, audit entry como registro).
- Busqueda full-text avanzada (solo filtro `q` por nombre).
- Paginacion de resultados.
