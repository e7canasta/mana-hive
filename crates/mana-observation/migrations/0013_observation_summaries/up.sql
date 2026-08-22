-- Resumenes diarios: observaciones producidas por una fuente analitica.
--
-- Comparten sobre con `incident_detections`: `source_record_id` unico para que
-- reintentar no duplique, mas procedencia (`source`, `model_version`,
-- `confidence`, `provenance_json`). Son evidencia, no registro: nadie los
-- corrige a mano, se reingieren.
--
-- `(resident_id, observed_on)` es unico: un residente tiene un resumen por dia
-- y por tipo. Reingerir el mismo dia reemplaza; es la unica escritura de
-- observacion que no es append-only, y lo es porque la fuente puede recalcular.
CREATE TABLE sleep_summaries (
    id                    TEXT PRIMARY KEY NOT NULL,
    source_record_id      TEXT NOT NULL UNIQUE,
    resident_id           TEXT NOT NULL,
    observed_on           TEXT NOT NULL,
    calm_minutes          INTEGER NOT NULL,
    restless_minutes      INTEGER NOT NULL,
    awake_minutes         INTEGER NOT NULL,
    out_of_bed_minutes    INTEGER NOT NULL,
    bed_exit_count        INTEGER NOT NULL,
    wake_count            INTEGER NOT NULL,
    source                TEXT NOT NULL,
    model_version         TEXT NOT NULL,
    confidence            REAL NULL,
    provenance_json       TEXT NOT NULL DEFAULT '{}',
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL
);
CREATE UNIQUE INDEX idx_sleep_summaries_resident_day
    ON sleep_summaries (resident_id, observed_on);

CREATE TABLE mobility_summaries (
    id                    TEXT PRIMARY KEY NOT NULL,
    source_record_id      TEXT NOT NULL UNIQUE,
    resident_id           TEXT NOT NULL,
    observed_on           TEXT NOT NULL,
    in_bed_minutes        INTEGER NOT NULL,
    out_of_bed_minutes    INTEGER NOT NULL,
    out_of_sight_minutes  INTEGER NOT NULL,
    walking_minutes       INTEGER NOT NULL,
    distance_meters       REAL NULL,
    transfer_count        INTEGER NOT NULL,
    source                TEXT NOT NULL,
    model_version         TEXT NOT NULL,
    confidence            REAL NULL,
    provenance_json       TEXT NOT NULL DEFAULT '{}',
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL
);
CREATE UNIQUE INDEX idx_mobility_summaries_resident_day
    ON mobility_summaries (resident_id, observed_on);

CREATE TABLE bathroom_summaries (
    id                     TEXT PRIMARY KEY NOT NULL,
    source_record_id       TEXT NOT NULL UNIQUE,
    resident_id            TEXT NOT NULL,
    observed_on            TEXT NOT NULL,
    visit_count            INTEGER NOT NULL,
    night_visit_count      INTEGER NOT NULL,
    assisted_count         INTEGER NOT NULL,
    total_minutes          INTEGER NOT NULL,
    longest_visit_minutes  INTEGER NOT NULL,
    source                 TEXT NOT NULL,
    model_version          TEXT NOT NULL,
    confidence             REAL NULL,
    provenance_json        TEXT NOT NULL DEFAULT '{}',
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL
);
CREATE UNIQUE INDEX idx_bathroom_summaries_resident_day
    ON bathroom_summaries (resident_id, observed_on);
