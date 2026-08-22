-- Event Store: persiste eventos crudos de NATS para event sourcing y auditoría.
--
-- Estas tablas almacenan los eventos tal como llegan por NATS, sin procesamiento.
-- Son la fuente de verdad para reconstrucción de estado y auditoría.

-- Scene Events: emitidos por Engine cuando detecta cambios de estado
CREATE TABLE scene_events (
    id                TEXT PRIMARY KEY NOT NULL,
    event_id          TEXT NOT NULL UNIQUE,
    bed_id            TEXT NOT NULL,
    resident_id       TEXT NULL,
    event_type        TEXT NOT NULL,
    from_state        TEXT NULL,
    to_state          TEXT NULL,
    trigger_type      TEXT NULL,
    timestamp         TEXT NOT NULL,
    payload_json      TEXT NOT NULL DEFAULT '{}',
    received_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_scene_events_bed_time ON scene_events (bed_id, timestamp);

-- Notification Events: emitidos por Sentinel cuando categoriza eventos
CREATE TABLE notification_events (
    id                TEXT PRIMARY KEY NOT NULL,
    category          TEXT NOT NULL,
    bed_id            TEXT NOT NULL,
    resident_id       TEXT NULL,
    event_type        TEXT NOT NULL,
    timestamp         TEXT NOT NULL,
    rule_id           TEXT NULL,
    risk_level        TEXT NULL,
    payload_json      TEXT NOT NULL DEFAULT '{}',
    received_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_notification_events_bed_time ON notification_events (bed_id, timestamp);
CREATE INDEX idx_notification_events_category ON notification_events (category);
