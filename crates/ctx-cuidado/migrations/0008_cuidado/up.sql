CREATE TABLE rounds (
    id TEXT PRIMARY KEY NOT NULL,
    wing_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'in_progress',
    scheduled_for TEXT,
    started_at TEXT NOT NULL,
    completed_at TEXT,
    started_by TEXT NOT NULL,
    completed_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (status IN ('in_progress', 'completed', 'cancelled'))
);

CREATE TABLE round_tasks (
    id TEXT PRIMARY KEY NOT NULL,
    round_id TEXT NOT NULL REFERENCES rounds(id),
    resident_id TEXT NOT NULL,
    bed_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    note TEXT,
    completed_at TEXT,
    completed_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (status IN ('pending', 'completed'))
);

CREATE TABLE care_notes (
    id TEXT PRIMARY KEY NOT NULL,
    resident_id TEXT NOT NULL,
    author_id TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'general',
    body TEXT NOT NULL,
    duration_min INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (length(trim(body)) > 0)
);

CREATE UNIQUE INDEX rounds_wing_in_progress_idx
    ON rounds (wing_id)
    WHERE status = 'in_progress';

CREATE INDEX rounds_wing_started_idx
    ON rounds (wing_id, started_at, id);

CREATE INDEX round_tasks_round_idx
    ON round_tasks (round_id, status, id);

CREATE INDEX round_tasks_resident_idx
    ON round_tasks (resident_id, created_at, id);

CREATE INDEX care_notes_resident_idx
    ON care_notes (resident_id, created_at, id);
