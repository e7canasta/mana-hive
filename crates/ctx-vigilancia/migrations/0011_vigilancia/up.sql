CREATE TABLE alerts (
    id               TEXT PRIMARY KEY NOT NULL,
    resident_id      TEXT NULL,
    bed_id           TEXT NOT NULL,
    evidence_kind    TEXT NOT NULL,
    evidence_ref     TEXT NULL,
    rule_id          TEXT NOT NULL,
    level            TEXT NOT NULL,
    status           TEXT NOT NULL,
    status_actor_id  TEXT NULL,
    status_at        TEXT NULL,
    title            TEXT NOT NULL,
    detail           TEXT NULL,
    occurred_at      TEXT NOT NULL,
    escalation_level INTEGER NOT NULL DEFAULT 0,
    escalated_at     TEXT NULL,
    escalated_to     TEXT NULL,
    created_at       TEXT NOT NULL,
    updated_at       TEXT NOT NULL
);

CREATE TABLE alert_transitions (
    id               TEXT PRIMARY KEY NOT NULL,
    alert_id         TEXT NOT NULL,
    from_status      TEXT NULL,
    to_status        TEXT NOT NULL,
    actor_id         TEXT NULL,
    occurred_at      TEXT NOT NULL,
    sequence         INTEGER NOT NULL,
    FOREIGN KEY (alert_id) REFERENCES alerts(id)
);

CREATE TABLE notification_deliveries (
    id               TEXT PRIMARY KEY NOT NULL,
    alert_id         TEXT NOT NULL,
    recipient_kind   TEXT NOT NULL,
    recipient_id     TEXT NOT NULL,
    channel          TEXT NOT NULL,
    escalation_level INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT NOT NULL,
    FOREIGN KEY (alert_id) REFERENCES alerts(id)
);

CREATE TABLE notification_delivery_events (
    id               TEXT PRIMARY KEY NOT NULL,
    delivery_id      TEXT NOT NULL,
    kind             TEXT NOT NULL,
    reason           TEXT NULL,
    occurred_at      TEXT NOT NULL,
    FOREIGN KEY (delivery_id) REFERENCES notification_deliveries(id)
);

CREATE TABLE alert_escalations (
    id               TEXT PRIMARY KEY NOT NULL,
    alert_id         TEXT NOT NULL,
    level            INTEGER NOT NULL,
    target_id        TEXT NOT NULL,
    occurred_at      TEXT NOT NULL,
    created_at       TEXT NOT NULL,
    FOREIGN KEY (alert_id) REFERENCES alerts(id)
);

CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_bed ON alerts(bed_id, status);
CREATE INDEX idx_alerts_resident ON alerts(resident_id) WHERE resident_id IS NOT NULL;
CREATE INDEX idx_alert_transitions_alert ON alert_transitions(alert_id, sequence);
CREATE INDEX idx_notification_deliveries_alert ON notification_deliveries(alert_id);
CREATE INDEX idx_notification_delivery_events_delivery ON notification_delivery_events(delivery_id);
CREATE INDEX idx_alert_escalations_alert ON alert_escalations(alert_id);
