# Casos de uso: `ctx-cobertura`

## Frontera funcional

`ctx-cobertura` es propietario de la grilla laboral, grupos de staff, membresias
temporales y cobertura de alas. Nunca importa `ctx-identidad`, `ctx-residencia`
ni `ctx-poblacion`; los IDs de usuario, facility y ala son opacos y la
validacion de existencia vive en `mana-app`.

## Reglas del contexto

- Un turno laboral no es el eje `day`/`night` de la politica de alarmas.
- Los keys de turno son unicos por facility, no un enum global.
- Dos turnos no pueden empezar en el mismo minuto local.
- Un ala tiene a lo sumo una cobertura por turno en un instante.
- Un grupo pertenece a la misma facility que el ala que cubre.
- Una membresia es temporal; un usuario puede pertenecer a multiples grupos.
- Reemplazar la grilla cierra coberturas que usaban turnos removidos.
- Las queries historicas usan `valid_from <= at < valid_to`.

## COB-01 - Reemplazar grilla de turnos

**Objetivo:** Definir los turnos laborales de una facility.

**Actor primario:** Staff con capability `master.structure.write`.

**Disparador:** HTTP PUT `/api/v1/facilities/{facilityId}/shifts`.

**Precondiciones:** Sesion autenticada; facility existe.

**Flujo principal:**

1. El staff envia `{ shifts: [{ key, label, start_minute }] }`.
2. El sistema reemplaza la grilla: retira los turnos existentes, crea los nuevos.
3. Si un turno removido tenia coberturas abiertas, las cierra.
4. Devuelve `200 OK` con `{ facility_id, shifts, coverages_cleared }`.

**Alternos y excepciones:**

- `shifts` vacio -> 422 VALIDATION_ERROR
- key duplicado -> 409 CONFLICT
- start_minute duplicado -> 409 CONFLICT
- start_minute fuera de rango (0..1439) -> 422

**Postcondiciones:** La grilla refleja los turnos nuevos; coberturas de turnos
removidos quedan cerradas con `valid_to`.

**Realizacion:** `mana-app::AppState::replace_shift_grid` -> `CoverageStore::replace_grid`

## COB-02 - Crear grupo de staff

**Objetivo:** Crear un grupo nombrado asociado a una facility.

**Disparador:** HTTP POST `/api/v1/staff-groups`.

**Flujo principal:**

1. El staff envia `{ facility_id, name }`.
2. El sistema crea el grupo.
3. Devuelve `201 CREATED` con el `StaffGroup`.

**Alternos y excepciones:**

- `name` vacio -> 422
- Nombre duplicado en la misma facility (grupo activo) -> 409

## COB-03 - Agregar miembros a un grupo

**Objetivo:** Asignar usuarios a un grupo de staff.

**Disparador:** HTTP PUT `/api/v1/staff-groups/{groupId}/members`.

**Flujo principal:**

1. El staff envia `{ members: [{ user_id, valid_from? }] }`.
2. El sistema cierra las membresias activas existentes y crea las nuevas.
3. Devuelve `200 OK` con las membresias actuales.

**Alternos y excepciones:**

- Grupo no encontrado -> 404
- `valid_from` ausente -> usa `now`

**Postcondiciones:** El grupo tiene exactamente los miembros enviados como
activos; las membresias anteriores quedan cerradas (historial preservado).

## COB-04 - Asignar cobertura a un ala

**Objetivo:** Asignar un grupo a un turno de un ala.

**Disparador:** HTTP PUT `/api/v1/wings/{wingId}/coverage`.

**Flujo principal:**

1. El staff envia `{ staff_group_id, shift_key }`.
2. El sistema cierra cualquier cobertura abierta para ese turno en ese ala.
3. Crea la nueva cobertura.
4. Devuelve `200 OK` con la `WingCoverage`.

**Alternos y excepciones:**

- `shift_key` no existe en la grilla de la facility -> 422
- El grupo pertenece a otra facility -> 422

**Postcondiciones:** El ala tiene una cobertura abierta para ese turno.

## COB-05 - Reemplazar grilla remueve coberturas

**Objetivo:** Verificar que reemplazar la grilla cierra coberturas afectadas.

**Disparador:** COB-01 con turnos removidos.

**Postcondiciones:** `coverages_cleared` indica cuantas coberturas quedaron
cerradas. Las coberturas de turnos que permanecen no se alteran.
