CREATE TABLE audit_log (
    id TEXT PRIMARY KEY NOT NULL,
    actor_id TEXT,
    action TEXT NOT NULL CHECK (length(trim(action)) > 0),
    entity_type TEXT NOT NULL CHECK (length(trim(entity_type)) > 0),
    entity_id TEXT NOT NULL CHECK (length(trim(entity_id)) > 0),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
);

CREATE INDEX audit_log_entity_time_idx
    ON audit_log (entity_type, entity_id, created_at);

CREATE INDEX audit_log_actor_time_idx
    ON audit_log (actor_id, created_at);

CREATE INDEX audit_log_action_time_idx
    ON audit_log (action, created_at);
