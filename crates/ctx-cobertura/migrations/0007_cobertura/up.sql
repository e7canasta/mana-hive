CREATE TABLE staff_groups (
    id TEXT PRIMARY KEY NOT NULL,
    facility_id TEXT NOT NULL,
    name TEXT NOT NULL,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(name)) > 0)
);

CREATE TABLE staff_group_members (
    id TEXT PRIMARY KEY NOT NULL,
    staff_group_id TEXT NOT NULL REFERENCES staff_groups(id),
    user_id TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_to TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE facility_shifts (
    id TEXT PRIMARY KEY NOT NULL,
    facility_id TEXT NOT NULL,
    key TEXT NOT NULL,
    label TEXT NOT NULL,
    start_minute INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(key)) > 0),
    CHECK (length(trim(label)) > 0),
    CHECK (start_minute >= 0 AND start_minute < 1440)
);

CREATE TABLE unit_shift_coverages (
    id TEXT PRIMARY KEY NOT NULL,
    wing_id TEXT NOT NULL,
    staff_group_id TEXT,
    shift_key TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_to TEXT,
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE UNIQUE INDEX staff_groups_facility_name_idx
    ON staff_groups (facility_id, name)
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX staff_group_members_user_group_valid_idx
    ON staff_group_members (user_id, staff_group_id)
    WHERE valid_to IS NULL;

CREATE INDEX staff_group_members_group_idx
    ON staff_group_members (staff_group_id, valid_from, id);

CREATE UNIQUE INDEX facility_shifts_facility_key_idx
    ON facility_shifts (facility_id, key)
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX facility_shifts_facility_minute_idx
    ON facility_shifts (facility_id, start_minute)
    WHERE retired_at IS NULL;

CREATE INDEX facility_shifts_facility_order_idx
    ON facility_shifts (facility_id, sort_order, id);

CREATE UNIQUE INDEX coverage_wing_shift_valid_idx
    ON unit_shift_coverages (wing_id, shift_key)
    WHERE valid_to IS NULL;

CREATE INDEX coverage_wing_idx
    ON unit_shift_coverages (wing_id, valid_from, id);
