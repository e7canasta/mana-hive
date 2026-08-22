CREATE TABLE residents (
    id TEXT PRIMARY KEY NOT NULL,
    external_id TEXT UNIQUE,
    full_name TEXT NOT NULL,
    birth_date TEXT,
    admission_date TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    discharged_at TEXT,
    discharged_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(full_name)) > 0),
    CHECK (status IN ('active', 'discharged'))
);

CREATE TABLE resident_bed_assignments (
    id TEXT PRIMARY KEY NOT NULL,
    resident_id TEXT NOT NULL REFERENCES residents(id),
    bed_id TEXT NOT NULL,
    starts_at TEXT NOT NULL,
    ends_at TEXT,
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE TABLE resident_attributes (
    id TEXT PRIMARY KEY NOT NULL,
    resident_id TEXT NOT NULL REFERENCES residents(id),
    code TEXT NOT NULL,
    value TEXT NOT NULL,
    source TEXT NOT NULL,
    source_ref TEXT,
    recorded_by TEXT,
    recorded_at TEXT NOT NULL,
    valid_from TEXT NOT NULL,
    valid_to TEXT,
    CHECK (length(trim(value)) > 0),
    CHECK (length(trim(source)) > 0)
);

CREATE INDEX assignments_resident_idx
    ON resident_bed_assignments (resident_id, starts_at, id);

CREATE INDEX assignments_bed_idx
    ON resident_bed_assignments (bed_id, starts_at, id);

CREATE INDEX attributes_resident_idx
    ON resident_attributes (resident_id, valid_from, id);
