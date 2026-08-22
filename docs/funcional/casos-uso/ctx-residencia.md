# Casos de uso: `ctx-residencia`

## Frontera funcional

`ctx-residencia` define el lugar fisico donde ocurre la operacion:

```text
Facility -> Wing -> Room -> Bed
```

Es dueno de nombres, jerarquia, dispositivos vinculados, estado de retiro
tecnico, planograma de alas y regiones de privacidad de habitaciones. No decide
quien ocupa una cama, quien trabaja en un ala ni que alarma corresponde a una
persona.

## Reglas del contexto

- No se crea un hijo bajo un padre inexistente o retirado.
- Una room number es unica dentro de una wing activa.
- Un `StreamKey` pertenece como maximo a una room activa.
- Un `MonitorKey` pertenece como maximo a una bed activa.
- `StreamKey` y `MonitorKey` son conceptos distintos aunque lleguen como texto.
- Campos de texto se recortan y tienen limites de longitud.
- `sort_order` de una wing no puede ser negativo.
- Retirar un padre lo excluye de lecturas activas y oculta sus hijos.
- El retiro explicito de estructura aun no esta expuesto como comando.
- El planograma de un ala tiene una version activa: guardar desactiva la
  anterior e inserta la nueva.
- Una habitacion no puede repetirse en el planograma activo de su ala.
- Las coordenadas del planograma son finitas y su `sort_order` no negativo.
- Las regiones de privacidad estan normalizadas dentro de `0..1`, son a lo
  sumo 8 y su guardado tambien reemplaza la version activa.

## RES-01 - Definir una residencia

**Objetivo:** registrar o corregir una residencia operable.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** alta o cambio de una facility.

**Precondiciones:** actor autenticado y autorizado; para modificar, facility
existente.

**Flujo principal:**

1. Se valida nombre y zona horaria.
2. En alta se genera `FacilityId` y se crea como activa.
3. En cambio se carga la facility y se aplican solo los campos enviados.
4. Se persiste la facility.
5. Se registra `facility.created` o `facility.updated`.

**Alternos y excepciones:**

- nombre o timezone vacio o demasiado largo: `VALIDATION_ERROR`;
- facility inexistente al actualizar: `NOT_FOUND`;
- fallo de auditoria: rollback de la facility.

**Postcondiciones:** la facility puede ser padre de wings; una lectura activa la
puede devolver.

**Realizacion:** `mana-app::create_facility` / `update_facility` sobre
`ResidenceStore`; handlers `facilities.create.post` y `facilities.update.patch`
sirviendo desde Rust; SDK `create_facility` / `update_facility`.

## RES-02 - Organizar una wing

**Objetivo:** dividir una residencia en unidades operativas ordenables.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** alta o cambio de una wing.

**Precondiciones:** la facility padre existe y esta activa.

**Flujo principal:**

1. Se valida nombre, piso y `sort_order`.
2. En alta se genera `WingId` y se vincula a la facility.
3. En cambio se carga la wing y se aplican los campos presentes.
4. Se persiste el resultado.
5. Se registra `wing.created` o `wing.updated`.

**Alternos y excepciones:**

- facility inexistente o retirada: `NOT_FOUND`;
- texto invalido o sort negativo: `VALIDATION_ERROR`;
- fallo de auditoria: rollback.

**Postcondiciones:** la wing aparece en las lecturas activas de su facility y
puede recibir rooms.

**Realizacion:** `mana-app::create_wing` / `update_wing`; handlers
`facilities.wings.create.post` y `wings.update.patch` sirviendo desde Rust; SDK
`create_wing` / `update_wing`.

## RES-03 - Definir una habitacion y su camara

**Objetivo:** registrar el espacio donde posteriormente pueden existir camas y,
opcionalmente, vincular su stream de camara.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** alta o cambio de una room.

**Precondiciones:** la wing padre existe y esta activa.

**Flujo principal:**

1. Se valida numero, tipo y `stream_key` opcional.
2. En alta se genera `RoomId`; si no llega tipo, aplicacion usa `single`.
3. Se comprueba que el numero no este usado en la wing activa.
4. Se comprueba que el stream no este vinculado a otra room activa.
5. Se persiste la room.
6. Se registra `room.created` o `room.updated`.

**Alternos y excepciones:**

- wing inexistente o retirada: `NOT_FOUND`;
- numero o stream duplicado: `CONFLICT`;
- enviar `stream_key: null` elimina la vinculacion de camara de forma explicita;
- room type, numero o stream invalidos: `VALIDATION_ERROR`;
- fallo de auditoria: rollback.

**Postcondiciones:** la room es identificable dentro de su wing y su stream no
puede quedar asignado a dos rooms activas.

**Realizacion:** `mana-app::create_room` / `update_room`; handlers
`wings.rooms.create.post` y `rooms.update.patch` sirviendo desde Rust; SDK
`create_room` / `update_room`.

## RES-04 - Definir una cama y su monitor

**Objetivo:** registrar una cama fisica y, opcionalmente, asociar el detector que
la observa.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** alta o cambio de una bed.

**Precondiciones:** la room padre existe y esta activa.

**Flujo principal:**

1. Se valida label y `monitor_key` opcional.
2. En alta se genera `BedId` y se vincula a la room.
3. Se comprueba que el monitor no este usado por otra bed activa.
4. Se persiste la bed.
5. Se registra `bed.created` o `bed.updated`.

**Alternos y excepciones:**

- room inexistente o retirada: `NOT_FOUND`;
- monitor duplicado: `CONFLICT`;
- enviar `monitor_key: null` elimina la vinculacion de forma explicita;
- label o monitor invalido: `VALIDATION_ERROR`;
- fallo de auditoria: rollback.

**Postcondiciones:** la bed queda disponible para futuros casos de poblacion,
pero no se asigna ningun residente.

**Realizacion:** `mana-app::create_bed` / `update_bed`; handlers
`rooms.beds.create.post` y `beds.update.patch` sirviendo desde Rust; SDK
`create_bed` / `update_bed`.

## RES-05 - Consultar estructura activa

**Objetivo:** permitir que un operador conozca la estructura utilizable sin
mostrar ramas que dependen de un padre retirado.

**Actor primario:** operador con `master.structure.read`.

**Disparadores:** listar facilities, consultar detalle, listar wings, rooms o
beds.

**Flujo principal:**

1. Se autentica y autoriza al actor.
2. Se consulta el owner correspondiente.
3. Las listas filtran recursos retirados.
4. Las listas de hijos verifican que el padre este activo.
5. El detalle de una facility compone sus wings activas.

**Alternos y excepciones:**

- padre inexistente o retirado: `NOT_FOUND` o lista no disponible;
- actor sin lectura: `FORBIDDEN`.

**Postcondiciones:** no cambia estructura ni auditoria. La lectura nunca devuelve
un hijo activo bajo un padre retirado.

**Realizacion:** `list_facilities`, `facility_detail`, `list_wings`,
`list_rooms` y `list_beds` en `mana-app` y `ResidenceStore`; SDK
`list_facilities`, `facility`, `list_rooms` y `list_beds`.

## RES-06 - Planograma de un ala

**Objetivo:** definir la disposicion espacial de las habitaciones sobre el plano
de un ala, que alimenta la vista de vigilancia.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** consultar o guardar la grilla de un ala.

**Precondiciones:** el ala existe y esta activa; las habitaciones del planograma
pertenecen al ala.

**Flujo principal (guardar):**

1. Se valida cada placement: coordenadas finitas y `sort_order` no negativo.
2. Se comprueba que no haya habitaciones duplicadas en el envio.
3. Se comprueba que cada habitacion pertenezca al ala y este activa.
4. Se desactiva la version activa anterior y se inserta la nueva.
5. Se registra `planogram.updated` con la cantidad de placements.

**Flujo principal (consultar):**

1. Se autoriza `master.structure.read`.
2. Se devuelven los placements de la version activa con numero, tipo y stream de
   cada habitacion.

**Alternos y excepciones:**

- ala inexistente o retirada: `NOT_FOUND`;
- habitacion duplicada en el envio: `CONFLICT`
  (`Habitacion duplicada en el planograma`);
- habitacion inexistente, retirada o de otro ala: `NOT_FOUND`
  (`Habitacion no encontrada`);
- coordenadas no finitas o sort negativo: `VALIDATION_ERROR`;
- fallo de auditoria: rollback.

**Postcondiciones:** el ala tiene exactamente una version activa de planograma
con los placements enviados.

**Realizacion:** `mana-app::planogram` / `save_planogram`; handlers
`wings.planogram.get` y `wings.planogram.put` sirviendo desde Rust
(`GET/PUT /api/v1/wings/:wingId/planogram`); SDK `planogram` / `save_planogram`.

## RES-07 - Regiones de privacidad de una habitacion

**Objetivo:** definir los rectangulos que enmascaran el video de una habitacion.

**Actor primario:** operador con `master.structure.write`.

**Disparador:** consultar o guardar las regiones de una room.

**Precondiciones:** la habitacion existe y esta activa.

**Flujo principal (guardar):**

1. Se valida cada region: valores finitos y normalizados dentro de `0..1`
   (`x + w <= 1` y `y + h <= 1`, con `w > 0` y `h > 0`).
2. Se limita a `MAX_PRIVACY_REGIONS` (8) regiones.
3. Se desactiva la version activa anterior y se inserta la nueva.
4. Se registra `room.privacy_regions.updated` con la cantidad de regiones.

**Flujo principal (consultar):**

1. Se autoriza `master.structure.read`.
2. Se devuelven las regiones de la version activa.

**Alternos y excepciones:**

- habitacion inexistente o retirada: `NOT_FOUND`;
- region fuera de `0..1`, no finita o con tamano invalido: `VALIDATION_ERROR`;
- mas de 8 regiones: `VALIDATION_ERROR`;
- fallo de auditoria: rollback.

**Postcondiciones:** la habitacion tiene una version activa de regiones (puede
ser vacia, lo que equivale a sin enmascaramiento).

**Realizacion:** `mana-app::privacy_regions` / `save_privacy_regions`; handlers
`rooms.privacy-regions.get` y `rooms.privacy-regions.put` sirviendo desde Rust
(`GET/PUT /api/v1/rooms/:roomId/privacy-regions`); SDK `privacy_regions` /
`save_privacy_regions`.

## RES-08 - Consultar vista global de alas y camas

**Objetivo:** alimentar la vista de la residencia entera: alas con su cantidad
de camas activas y camas con su ubicacion completa (habitacion, ala, piso y
stream) para decidir donde ubicar a alguien.

**Actor primario:** operador con `master.structure.read`.

**Disparadores:** `GET /api/v1/wings` (lista global) y `GET /api/v1/beds`
(overview de camas).

**Flujo principal:**

1. Se autoriza `master.structure.read`.
2. `list_wings` devuelve las alas activas de facilities activas con
   `bed_count` (camas activas no retiradas).
3. `list_beds` devuelve las camas activas con numero, tipo y stream de la
   habitacion y ala, piso y wing de la cama.
4. No se escribe estructura ni auditoria.

**Alternos y excepciones:**

- sin alas o sin camas: listas vacias;
- actor sin lectura: `FORBIDDEN`.

**Postcondiciones:** lecturas sin ramas retiradas; `bed_count` y ubicacion
quedan disponibles para el tablero.

**Realizacion:** `mana-app::list_wings` (usa `list_wings_overview`) y
`list_residence_beds`; handlers `wings.list.get` y `beds.list.get` sirviendo
desde Rust; SDK `list_wings` (con `bed_count`) y `list_residence_beds`.

## Fuera del corte actual

- Retiro explicito de facility, wing, room o bed desde un command.
- Read model de ocupacion (`resident_id` / `resident_name` en camas) y
  asignacion de residentes: dependen de `ctx-poblacion`.
- Turnos, grupos de staff, cobertura por ala y `hasRoundPlan`: pertenecen a
  `ctx-cobertura` y siguen en Node.
