# Modelo de dominio: `ctx-historia`

## Pregunta del contexto

Que evidencia clinica se registro, que incidentes requieren revision y que
decidio un humano sobre ellos?

## Objetos de dominio

```text
IncidentDetection (agregado raiz, inmutable)
  id: DetectionId
  source_record_id: String (UNIQUE, clave de idempotencia)
  resident_id: String (opaco, sin FK)
  bed_id: Option<String>
  source_alert_id: Option<String>
  kind: IncidentKind (fall | bed_exit | wandering | transfer | other)
  severity: Severity (low | medium | high | critical)
  occurred_at: Instante
  location: Option<String>
  activity: Option<String>
  injury_status: String
  self_recovery: Option<bool>
  response_seconds: Option<i32>
  narrative: Option<String>
  interventions_json: String (default '[]')
  source: String
  model_version: String
  confidence: Option<f64>
  provenance_json: String (default '{}')
  created_at: Instante

IncidentReview (agregado append-only)
  id: ReviewId
  incident_id: String (opaco, sin FK entre contextos)
  status: ReviewStatus (open | under_review | closed)
  detection_verdict: Option<DetectionVerdict> (fall | not_a_fall | uncertain | safe_to_ground)
  review_note: Option<String>
  resolved_at: Option<Instante>
  actor_id: Id<Actor>
  created_at: Instante
```

## Invariantes

| # | Invariante | Enforcement |
|---|-----------|-------------|
| 1 | `source_record_id` duplicado devuelve la deteccion existente | Indice UNIQUE + `ingest_in_transaction` |
| 2 | La ingesta puede crear una deteccion, pero no insertar una revision | Modelo: `IncidentDetection` no tiene campos de revision |
| 3 | La ingesta no puede cerrar un incidente ni fijar un veredicto | No hay endpoint de ingesta que acepte status/veredicto |
| 4 | Una revision siempre tiene actor y timestamp del servidor | Dominio puro |
| 5 | Las revisiones nunca se pisan; el estado actual es la ultima revision valida | Append-only, `current` = ultima por created_at |
| 6 | `safe_to_ground` no es un veredicto de caida | Documentacion + validacion en edge |
| 7 | El vocabulario de deteccion y revision son distintos de reglas de alerta | Documentacion |
| 8 | Las metricas de respuesta vienen de read models de Observacion/Cuidado | No se copian en la deteccion |

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

Indice: `(incident_id, created_at, id)` para resolver la revision actual de
forma determinista.

## Subdominios

- `detecciones`: `IncidentDetection`, `DetectionsRepo` (ingest idempotente,
  get, list_by_resident).
- `revisiones`: `IncidentReview`, `RevisionesRepo` (create_review,
  list_by_incident, get_current_review).

## Puertos entre contextos

- Observation sequence by bed and time.
- Resident and location read models.
- Alert lookup for `source_alert_id`.
- AuditPort for each review.

## Tests

- Ingesta idempotente (mismo source_record_id).
- Ingesta no puede escribir columnas de revision.
- Revision, reabrir y revisar de nuevo preserva las 3 entradas.
- Cierre con `not_a_fall` permanece distinto de open/closed status.
- Secuencia y validacion de rango de fechas.
- Contrato exacto de nuevo incidente y migracion de cliente.
