# Contexto `ctx-residencia`

Clase: soporte.

## Pregunta

Como esta compuesto el hogar y que espacio, cama y dispositivo existen en cada
unidad?

## Lenguaje y ownership

Este contexto posee la estructura fisica: residencias, alas, habitaciones,
camas, streams de camara, vinculaciones de detectores, planograma y regiones de
privacidad. No posee residentes, asignaciones, politica de alarmas, presentacion
del board ni Companion.

`stream_key` identifica un stream de camara de una habitacion. `monitor_key`
identifica un detector de una cama. Son conceptos distintos y newtypes Rust
distintos.

## Agregados

- `Facility`: nombre y zona horaria de una residencia.
- `Wing`: unidad funcional dentro de una residencia.
- `Room`: numero, tipo y stream de camara.
- `Bed`: cama fisica, etiqueta y vinculacion de detector.
- `Planogram`: conjunto de ubicaciones de un ala.

La jerarquia no es un unico agregado. Cada recurso tiene su propia frontera de
consistencia; los IDs de padres se validan en los comandos de aplicacion.

## Tablas

### `facilities`

```text
id             TEXT PRIMARY KEY
name           TEXT NOT NULL
timezone       TEXT NOT NULL
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

### `wings`

```text
id             TEXT PRIMARY KEY
facility_id    TEXT NOT NULL
name           TEXT NOT NULL
floor          TEXT NOT NULL
sort_order     INTEGER NOT NULL DEFAULT 0
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

### `rooms`

```text
id             TEXT PRIMARY KEY
wing_id        TEXT NOT NULL
number         TEXT NOT NULL
type           TEXT NOT NULL
stream_key     TEXT NULL
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

Indices unicos parciales imponen `(wing_id, number)` y `stream_key` entre las
habitaciones cuyo `retired_at IS NULL`. Un stream null es valido: una habitacion
puede no tener camara todavia.

### `beds`

```text
id             TEXT PRIMARY KEY
room_id        TEXT NOT NULL
label          TEXT NOT NULL
monitor_key    TEXT NULL
retired_at     TEXT NULL
retired_by     TEXT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

`monitor_key` es unico entre camas no retiradas. Los atributos de equipamiento
no viven en un blob de traits sin validar; el vocabulario controlado de
equipamiento pertenece a este contexto y puede normalizarse cuando el catalogo
este fijo.

### `planogram_placements`

```text
id             TEXT PRIMARY KEY
wing_id        TEXT NOT NULL
room_id        TEXT NOT NULL
x              REAL NOT NULL
y              REAL NOT NULL
sort_order     INTEGER NOT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

Hay como maximo una ubicacion activa por habitacion. Las coordenadas son valores
relativos normalizados, no un modelo CAD.

### `room_privacy_regions`

```text
id             TEXT PRIMARY KEY
room_id        TEXT NOT NULL
x              REAL NOT NULL
y              REAL NOT NULL
w              REAL NOT NULL
h              REAL NOT NULL
created_at     TEXT NOT NULL
updated_at     TEXT NOT NULL
```

La escritura reemplaza el conjunto de regiones de una habitacion en una
transaccion. Cada coordenada esta en `0..1`, ancho y alto son positivos y el
rectangulo debe quedar dentro del marco normalizado.

## Invariantes

1. A retired parent is not returned by active list queries.
2. A room number is unique inside an active wing.
3. A stream is bound to at most one active room.
4. A monitor is bound to at most one active bed.
5. A planogram placement references a room in the same wing.
6. A room appears at most once in a wing planogram.
7. A privacy set has at most eight valid normalized regions.
8. Removing a camera or monitor is explicit and does not silently reassign the
   device to another resource.
9. El contexto nunca decide quien ocupa una cama.

## API

### Facilities and wings

- `GET /api/v1/facilities` -> `{ facilities: Facility[] }`
- `GET /api/v1/facilities/{facilityId}` -> `FacilityDetail`
- `POST /api/v1/facilities` -> `201 Facility`
- `PATCH /api/v1/facilities/{facilityId}` -> `{ facility: Facility }`
- `GET /api/v1/wings` -> `{ wings: Wing[] }`
- `POST /api/v1/facilities/{facilityId}/wings` -> `201 Wing`
- `PATCH /api/v1/wings/{wingId}` -> `{ wing: Wing }`

### Rooms and beds

- `GET /api/v1/wings/{wingId}/rooms` -> `{ rooms: Room[] }`
- `POST /api/v1/wings/{wingId}/rooms` -> `201 Room`
- `PATCH /api/v1/rooms/{roomId}` -> `{ room: Room }`
- `GET /api/v1/rooms/{roomId}/beds` -> `{ beds: Bed[] }`
- `POST /api/v1/rooms/{roomId}/beds` -> `201 Bed`
- `GET /api/v1/beds` -> `{ beds: ResidenceBed[] }`
- `PATCH /api/v1/beds/{bedId}` -> `{ bed: Bed }`

`ResidenceBed` es un read model de aplicacion que agrega habitacion, ala y
ocupacion actual. No es una fila ni un agregado devuelto por este contexto.

### Planogram and privacy

- `GET /api/v1/wings/{wingId}/planogram`
- `PUT /api/v1/wings/{wingId}/planogram`
- `GET /api/v1/rooms/{roomId}/privacy-regions`
- `PUT /api/v1/rooms/{roomId}/privacy-regions`

La vista de Companion es una composicion de `mana-app`, no una API de este
contexto.

## Puertos entre contextos

- `FacilityLookup`: validate a facility or wing ID.
- `BedLookup`: validate a bed and return its room/wing identity.
- `MonitorBindingLookup`: map an incoming monitor key to a bed.

Estos puertos devuelven read models o IDs tipados. Nunca exponen filas Diesel.

Poblacion puede pedir una cama a traves de `mana-app`; Residencia nunca importa
Poblacion para mostrar ocupacion.

## Tests

- duplicate room number, stream and monitor return `409`;
- retired resources are hidden from active lists;
- invalid parent relationships return `404` or `422`;
- planogram uniqueness and normalized privacy rectangles;
- room and bed API responses match the client schemas;
- structure read models compose occupancy without changing ownership.

## No posee

- residents or assignments;
- current sensor state;
- staff shifts or coverage;
- alarm rules;
- UI-specific Companion composition.
