CREATE TABLE facilities (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    timezone TEXT NOT NULL,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(name)) > 0),
    CHECK (length(trim(timezone)) > 0)
);

CREATE TABLE wings (
    id TEXT PRIMARY KEY NOT NULL,
    facility_id TEXT NOT NULL REFERENCES facilities(id),
    name TEXT NOT NULL,
    floor TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(name)) > 0),
    CHECK (length(trim(floor)) > 0),
    CHECK (sort_order >= 0)
);

CREATE TABLE rooms (
    id TEXT PRIMARY KEY NOT NULL,
    wing_id TEXT NOT NULL REFERENCES wings(id),
    number TEXT NOT NULL,
    room_type TEXT NOT NULL,
    stream_key TEXT,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(number)) > 0),
    CHECK (length(trim(room_type)) > 0)
);

CREATE TABLE beds (
    id TEXT PRIMARY KEY NOT NULL,
    room_id TEXT NOT NULL REFERENCES rooms(id),
    label TEXT NOT NULL,
    monitor_key TEXT,
    retired_at TEXT,
    retired_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(label)) > 0)
);

CREATE UNIQUE INDEX rooms_active_number_idx
    ON rooms (wing_id, number)
    WHERE retired_at IS NULL;

CREATE UNIQUE INDEX rooms_active_stream_idx
    ON rooms (stream_key)
    WHERE stream_key IS NOT NULL AND retired_at IS NULL;

CREATE UNIQUE INDEX beds_active_monitor_idx
    ON beds (monitor_key)
    WHERE monitor_key IS NOT NULL AND retired_at IS NULL;

CREATE INDEX wings_facility_order_idx
    ON wings (facility_id, sort_order, id);

CREATE INDEX rooms_wing_order_idx
    ON rooms (wing_id, number, id);

CREATE INDEX beds_room_order_idx
    ON beds (room_id, label, id);
