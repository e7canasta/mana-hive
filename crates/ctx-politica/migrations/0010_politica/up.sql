CREATE TABLE alarm_profile_versions (
    id               TEXT PRIMARY KEY NOT NULL,
    resident_id      TEXT NOT NULL,
    valid_from       TEXT NOT NULL,
    valid_to         TEXT NULL,
    mobility_aid     TEXT NOT NULL,
    autopilot        INTEGER NOT NULL,
    mode             TEXT NOT NULL,
    template_id      TEXT NOT NULL,
    overrides_json   TEXT NOT NULL DEFAULT '{}',
    catalog_version  TEXT NOT NULL,
    updated_by       TEXT NULL,
    created_at       TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_alarm_profiles_one_current
    ON alarm_profile_versions(resident_id)
    WHERE valid_to IS NULL;

CREATE INDEX idx_alarm_profiles_resident_valid
    ON alarm_profile_versions(resident_id, valid_from, valid_to);
