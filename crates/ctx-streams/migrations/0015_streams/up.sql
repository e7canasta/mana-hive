CREATE TABLE streams (
    id TEXT PRIMARY KEY NOT NULL,
    room_id TEXT NOT NULL,
    stream_key TEXT NOT NULL,
    name TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX streams_room_idx ON streams (room_id, active, id);

CREATE UNIQUE INDEX streams_active_room_key_idx
    ON streams (room_id, stream_key)
    WHERE active = 1;

CREATE TABLE stream_regions (
    id TEXT PRIMARY KEY NOT NULL,
    stream_id TEXT NOT NULL REFERENCES streams(id),
    region_type TEXT NOT NULL CHECK(region_type IN (
        'bathroom','hallway','exit','bed','furniture','person','object'
    )),
    points TEXT NOT NULL,
    label TEXT,
    is_static INTEGER NOT NULL DEFAULT 1,
    updated_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX regions_stream_idx ON stream_regions (stream_id, id);
