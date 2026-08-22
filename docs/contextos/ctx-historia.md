# Contexto `ctx-historia`

Clase: soporte.

## Pregunta

Que evidencia clinica se registro, que incidentes requieren revision y que
decidio un humano sobre ellos?

## Lenguaje y ownership

Este contexto separa dos ciclos de vida que antes se guardaban en una fila de
`incidents`:

- `IncidentDetection`: evidencia inmutable ingerida desde una fuente de IA u
  operativa.
- `IncidentReview`: juicio humano append-only sobre esa evidencia.

El incidente mostrado al cliente es un read model que compone la deteccion con
todas las revisiones y una proyeccion `current`. El contexto no posee eventos de
sensor crudos ni resumenes analiticos diarios.

## Agregados

### `IncidentDetection`

Raiz de un evento detectado. Una fuente interna puede insertarlo de forma
idempotente y no cambia despues de aceptarlo.

### `IncidentReview`

Decision de revision append-only. Un caso puede revisarse, reabrirse y volver a
revisarse. La ultima revision es la actual; la historia queda visible para
lectores autorizados.

## Tablas

### `incident_detections`

```text
id                  TEXT PRIMARY KEY
source_record_id    TEXT NOT NULL UNIQUE
resident_id         TEXT NOT NULL
bed_id              TEXT NULL
source_alert_id     TEXT NULL
kind                TEXT NOT NULL       -- fall | bed_exit | wandering | transfer | other
severity            TEXT NOT NULL       -- low | medium | high | critical
occurred_at         TEXT NOT NULL
location            TEXT NULL
activity            TEXT NULL
injury_status       TEXT NOT NULL
self_recovery       INTEGER NULL
response_seconds    INTEGER NULL
narrative           TEXT NULL
interventions_json  TEXT NOT NULL DEFAULT '[]'
source              TEXT NOT NULL
model_version       TEXT NOT NULL
confidence          REAL NULL
provenance_json     TEXT NOT NULL DEFAULT '{}'
created_at          TEXT NOT NULL
```

El ID del registro fuente es la clave de idempotencia. Payload y provenance
tienen limites y se validan en el borde de ingesta.

### `incident_reviews`

```text
id                  TEXT PRIMARY KEY
incident_id         TEXT NOT NULL
status              TEXT NOT NULL       -- open | under_review | closed
detection_verdict   TEXT NULL           -- fall | not_a_fall | uncertain | safe_to_ground
review_note         TEXT NULL
resolved_at         TEXT NULL
actor_id            TEXT NOT NULL
created_at          TEXT NOT NULL
```

La tabla es append-only. `incident_id` apunta a un ID de deteccion sin foreign
key entre contextos. Un indice sobre `(incident_id, created_at, id)` resuelve la
revision actual de forma determinista.

## Invariantes

1. Un `source_record_id` duplicado devuelve la deteccion existente y no crea un
   segundo incidente.
2. La ingesta puede crear una deteccion, pero no insertar una revision.
3. La ingesta no puede cerrar un incidente ni fijar un veredicto.
4. Una revision siempre tiene actor y timestamp del servidor.
5. Las revisiones nunca se pisan; el estado actual es la ultima revision valida.
6. `safe_to_ground` no es un veredicto de caida aunque el residente haya tocado el
   floor.
7. El vocabulario de deteccion y el de revision son distintos de los IDs de
   reglas de alerta.
8. La secuencia y las metricas de respuesta derivadas vienen de read models de
   Observacion y Cuidado, no se copian en la deteccion.

## API

### Ingestion

- `POST /internal/v1/clinical/incidents`
- Header: `x-clinical-secret` or a service credential
- New record -> `201 { incident, duplicate: false }`
- Repeated source record -> `200 { incident, duplicate: true }`

### Reading

- `GET /api/v1/residents/{residentId}/incidents`
- `GET /api/v1/incidents/{incidentId}/sequence`

La respuesta de incidente es la forma compuesta:

```json
{
  "id": "incident-1",
  "resident_id": "resident-1",
  "occurred_at": "2026-08-18T03:12:44.000Z",
  "detection": { "kind": "fall", "severity": "high" },
  "reviews": [],
  "current": { "status": "open", "detection_verdict": null, "resolved_at": null }
}
```

### Review

```text
POST /api/v1/incidents/{incidentId}/reviews
```

Esto reemplaza el `PATCH` viejo que mutaba una fila porque la operacion agrega
una decision humana. Requiere `incidents.manage` y devuelve el incidente
compuesto con su historia de revisiones.

La actualizacion del cliente es parte del mismo cambio: `incident.kind` pasa a
`incident.detection.kind`, y la pantalla de revision envia una revision nueva
en vez de mutar un objeto en el lugar.

## Puertos entre contextos

- Observation sequence by bed and time;
- resident and location read models;
- alert lookup for `source_alert_id`;
- AuditPort for each review.

Daily sleep, mobility and bathroom summaries are served by the observation /
perception subsystem until a dedicated read service exists. They are not
business rows owned by this context.

## Tests

- idempotent ingestion;
- ingestion cannot write review columns because they do not exist in its model;
- review, reopen and review again preserves all three entries;
- closed detection with `not_a_fall` remains distinct from open/closed status;
- sequence composition and date range validation;
- exact new incident contract and client migration.

## No posee

- sensor events;
- daily analytic summaries;
- alarm rule configuration;
- alert lifecycle;
- resident assignment history.
