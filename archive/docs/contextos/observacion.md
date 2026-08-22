# Subsistema de Observacion

Tipo: ciclo de vida de datos, no bounded context de negocio.

## Pregunta

Que informo el detector y cual es el ultimo estado proyectado de cada cama?

## Por que no es un `ctx-*`

La observacion no decide que significa una alarma, no revisa incidentes y no
administra residentes. Es evidencia externa y una proyeccion operacional. Su
retencion, volumen y transporte pueden cambiar sin cambiar el modelo de
Registro.

El adapter inicial puede vivir dentro de `mana-hub`. El destino operativo es un
subsistema que pueda consumir del bridge o de NATS y escribir Parquet sin hacer
que la base transaccional sea el almacen analitico.

## Tablas de la fase inicial

### `sensor_events`

```text
id                 TEXT PRIMARY KEY
source_event_id    TEXT NOT NULL UNIQUE
bed_id             TEXT NOT NULL       -- opaque BedId
resident_id        TEXT NULL           -- occupant at receipt/occurrence
monitor_key        TEXT NOT NULL
kind               TEXT NOT NULL
room_state         TEXT NULL
substate           TEXT NULL
zone               TEXT NULL
state              TEXT NULL
sleeping           INTEGER NULL
alert_level        TEXT NULL
occurred_at        TEXT NOT NULL
received_at        TEXT NOT NULL
payload_json       TEXT NOT NULL
```

`payload_json` es una copia de borde para campos nuevos del detector. Los
campos que participan en consultas o reglas se proyectan a columnas tipadas.

### `current_bed_states`

```text
bed_id             TEXT PRIMARY KEY
resident_id        TEXT NULL
room_state         TEXT NULL
state              TEXT NOT NULL
substate           TEXT NULL
sleeping           INTEGER NOT NULL DEFAULT 0
alert_level        TEXT NOT NULL DEFAULT 'low'
state_since        TEXT NULL
updated_at         TEXT NOT NULL
source             TEXT NOT NULL
source_event_id    TEXT NULL
```

Es una proyeccion, no una fuente de verdad. Puede borrarse cuando cambia el
ocupante y reconstruirse desde el stream de eventos y la nueva asignacion.

## Invariantes

1. `source_event_id` hace idempotente la ingesta.
2. Un evento es inmutable despues de aceptarse.
3. `received_at` lo asigna el hub; `occurred_at` viene de la fuente y se valida.
4. Unknown no equivale a false o cero.
5. El estado actual es reemplazable y reconstruible.
6. Cambiar el ocupante de una cama limpia su proyeccion en la misma transaccion
   de aplicacion.
7. La frescura se deriva de `updated_at` y parametros de plataforma; no se
   persiste como `live`, `stale` u `offline`.
8. El detector informa observaciones; la politica decide si una observacion
   crea una alerta.

## Resumenes diarios

Los resumenes de sueño, movilidad y bano son observaciones producidas por una
fuente analitica. Durante la transicion pueden materializarse en SQLite para la
API; el destino es Parquet y el motor de consulta es DuckDB.

Their logical records keep the following common envelope:

```text
id                 TEXT PRIMARY KEY
source_record_id   TEXT NOT NULL UNIQUE
resident_id        TEXT NOT NULL
observed_on        TEXT NOT NULL       -- YYYY-MM-DD
source             TEXT NOT NULL
model_version      TEXT NOT NULL
confidence         REAL NULL
provenance_json    TEXT NOT NULL DEFAULT '{}'
created_at         TEXT NOT NULL
updated_at         TEXT NOT NULL
```

Los valores especificos del payload siguen siendo tipados:

- sleep: calm, restless, awake, out-of-bed minutes, exits and wakes;
- mobility: in-bed, out-of-bed, out-of-sight, walking minutes, distance,
  speed and transfers;
- bathroom: total, night and assisted visits, total and longest duration.

Las invariantes de ingesta son:

- `wake_count >= bed_exit_count`;
- `night_visit_count <= visit_count`;
- `assisted_count <= visit_count`;
- `longest_visit_minutes <= total_minutes`;
- mobility minutes sum to at most `1440`.

La API calcula totales, eficiencia y promedios como campos de read model. El
cliente no recalcula metricas clinicas desde filas parciales.

## Contrato de eventos

El endpoint interno inicial se conserva:

```text
POST /internal/v1/events
```

Autentica el bridge, valida el envelope, guarda el evento y actualiza la
proyeccion. IDs fuente duplicados devuelven `200` con
`duplicate: true`; a new event returns `201`.

El vocabulario se documenta aparte porque la capacidad del detector crece con
el tiempo. Los campos actuales incluyen `room_state`, `substate`, `sleeping` y,
when available, `zone`, posture and aid/object observations. The detector never
receives resident policy and never makes a clinical diagnosis.

## Read models

Estos endpoints los compone `mana-app` desde Residencia, Poblacion, Observacion,
Historia, Politica y Vigilancia:

- `GET /api/v1/wings/{wingId}/board`
- `GET /api/v1/residents/{residentId}/current-state`
- `GET /api/v1/residents/{residentId}/events`
- `GET /api/v1/residents/{residentId}/timeline`
- `GET /api/v1/companion/rooms`

Ninguna de estas vistas de superficie es una tabla poseida por una aplicacion
React.

## Separacion futura

La frontera de un proceso futuro es:

```text
bridge -> internal event contract / NATS -> observation consumer
                                      -> hot projection
                                      -> Parquet
                                      -> DuckDB read models
```

La API publica permanece estable mientras almacenamiento y transporte se mueven
detras del contrato.

## Tests

- comportamiento de eventos duplicados;
- politica de eventos fuera de orden;
- reconstruccion de proyeccion;
- limpieza de estado al cambiar ocupante;
- umbrales de frescura;
- campos de payload compatibles hacia adelante;
- read models de board y residente desde escenas seed.
