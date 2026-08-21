-- mana-hive hub: the System of Record.

CREATE TABLE events (
    global_seq   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stream       TEXT        NOT NULL,   -- e.g. 'scene.12A.2026-08-20', 'alarm.AL-2231'
    stream_seq   BIGINT      NOT NULL,   -- optimistic concurrency per stream
    event_id     TEXT        NOT NULL,   -- end-to-end idempotency (Nats-Msg-Id)
    type         TEXT        NOT NULL,   -- 'scene.transition-detected'
    version      INT         NOT NULL,
    source       TEXT        NOT NULL,   -- 'scene-engine', 'ia-cell/12A', ...
    payload      JSONB       NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (stream, stream_seq),
    UNIQUE (event_id)                    -- bus redelivery lands here as Duplicate
);
CREATE INDEX ix_events_stream ON events (stream, stream_seq);
CREATE INDEX ix_events_type   ON events (type, occurred_at);

CREATE TABLE consumer_watermarks (
    consumer    TEXT PRIMARY KEY,
    global_seq  BIGINT      NOT NULL DEFAULT 0,
    advanced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Judgment telemetry, outside the domain ledger (append-only).
CREATE TABLE decision_records (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    engine       TEXT        NOT NULL,   -- 'sentinel-evaluator@1.4.0+ab12cd'
    stimulus     TEXT        NOT NULL,   -- EventRef: stream + seq
    inputs       JSONB       NOT NULL,   -- fingerprints: twin, rules, coverage, calibration
    output       JSONB       NOT NULL,
    explanation  JSONB       NOT NULL,   -- steps + discards
    took_ms      INT         NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_decision_stimulus ON decision_records (stimulus);
