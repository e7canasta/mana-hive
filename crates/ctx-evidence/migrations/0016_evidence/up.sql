-- ctx-evidence: tablas de evidencia, timelines y clip windows

-- Evidencia: eventos significativos destacados
CREATE TABLE evidence (
    id                  TEXT PRIMARY KEY,
    bed_id              TEXT NOT NULL,
    resident_id         TEXT NULL,
    evidence_type       TEXT NOT NULL,
    category            TEXT NOT NULL,
    scene_event_id      TEXT NOT NULL,
    scene_event_json    TEXT NOT NULL,
    rule_id             TEXT NULL,
    shift               TEXT NULL,
    risk_level          TEXT NULL,
    timestamp           TEXT NOT NULL,
    created_at          TEXT NOT NULL
);

CREATE INDEX idx_evidence_bed_id ON evidence(bed_id);
CREATE INDEX idx_evidence_resident_id ON evidence(resident_id);
CREATE INDEX idx_evidence_timestamp ON evidence(timestamp);
CREATE INDEX idx_evidence_category ON evidence(category);

-- Timelines: eventos con contexto (N anteriores + M posteriores)
CREATE TABLE timelines (
    id                  TEXT PRIMARY KEY,
    bed_id              TEXT NOT NULL,
    resident_id         TEXT NULL,
    anchor_event_id     TEXT NOT NULL,
    anchor_event_json   TEXT NOT NULL,
    before_events_json  TEXT NOT NULL DEFAULT '[]',
    after_events_json   TEXT NOT NULL DEFAULT '[]',
    window_start        TEXT NOT NULL,
    window_end          TEXT NOT NULL,
    created_at          TEXT NOT NULL,
    closed_at           TEXT NULL
);

CREATE INDEX idx_timelines_bed_id ON timelines(bed_id);
CREATE INDEX idx_timelines_resident_id ON timelines(resident_id);
CREATE INDEX idx_timelines_anchor_event_id ON timelines(anchor_event_id);

-- Clip windows: ventanas de detección de patrones
CREATE TABLE clip_windows (
    window_id           TEXT PRIMARY KEY,
    bed_id              TEXT NOT NULL,
    resident_id         TEXT NULL,
    started_at          TEXT NOT NULL,
    ended_at            TEXT NULL,
    timeout_minutes     INTEGER NOT NULL,
    events_json         TEXT NOT NULL DEFAULT '[]',
    state               TEXT NOT NULL DEFAULT 'open',
    close_condition_json TEXT NOT NULL,
    created_at          TEXT NOT NULL,
    closed_at           TEXT NULL
);

CREATE INDEX idx_clip_windows_bed_id ON clip_windows(bed_id);
CREATE INDEX idx_clip_windows_state ON clip_windows(state);
CREATE INDEX idx_clip_windows_started_at ON clip_windows(started_at);
