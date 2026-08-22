CREATE TABLE planogram_placements (
    id TEXT PRIMARY KEY NOT NULL,
    wing_id TEXT NOT NULL REFERENCES wings(id),
    room_id TEXT NOT NULL REFERENCES rooms(id),
    x REAL NOT NULL DEFAULT 0,
    y REAL NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE room_privacy_regions (
    id TEXT PRIMARY KEY NOT NULL,
    room_id TEXT NOT NULL REFERENCES rooms(id),
    x REAL NOT NULL,
    y REAL NOT NULL,
    w REAL NOT NULL,
    h REAL NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX planogram_wing_order_idx
    ON planogram_placements (wing_id, active, sort_order, id);

CREATE UNIQUE INDEX planogram_active_room_idx
    ON planogram_placements (room_id)
    WHERE active = 1;

CREATE INDEX privacy_regions_room_idx
    ON room_privacy_regions (room_id, active, id);
