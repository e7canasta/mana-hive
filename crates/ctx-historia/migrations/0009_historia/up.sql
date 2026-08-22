CREATE TABLE incident_detections (
    id                  TEXT PRIMARY KEY NOT NULL,
    source_record_id    TEXT NOT NULL UNIQUE,
    resident_id         TEXT NOT NULL,
    bed_id              TEXT NULL,
    source_alert_id     TEXT NULL,
    kind                TEXT NOT NULL,
    severity            TEXT NOT NULL,
    occurred_at         TEXT NOT NULL,
    location            TEXT NULL,
    activity            TEXT NULL,
    injury_status       TEXT NOT NULL,
    self_recovery       INTEGER NULL,
    response_seconds    INTEGER NULL,
    narrative           TEXT NULL,
    interventions_json  TEXT NOT NULL DEFAULT '[]',
    source              TEXT NOT NULL,
    model_version       TEXT NOT NULL,
    confidence          REAL NULL,
    provenance_json     TEXT NOT NULL DEFAULT '{}',
    created_at          TEXT NOT NULL
);

CREATE TABLE incident_reviews (
    id                  TEXT PRIMARY KEY NOT NULL,
    incident_id         TEXT NOT NULL,
    status              TEXT NOT NULL,
    detection_verdict   TEXT NULL,
    review_note         TEXT NULL,
    resolved_at         TEXT NULL,
    actor_id            TEXT NOT NULL,
    created_at          TEXT NOT NULL
);

CREATE INDEX idx_incident_reviews_incident
    ON incident_reviews (incident_id, created_at, id);
