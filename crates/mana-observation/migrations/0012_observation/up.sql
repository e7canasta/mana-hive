-- Evidencia: lo que informo el detector. Append-only.
--
-- `bed_id` es NULLABLE a proposito. El detector conoce `monitor_key`, no camas;
-- resolver monitor_key -> cama -> residente es un cruce que puede fallar cuando
-- una camara todavia no esta vinculada. Un evento que llego es un hecho: se
-- acepta igual y queda recuperable. Rechazarlo perderia evidencia de una camara
-- que si esta viendo algo, que es la falla silenciosa que este sistema existe
-- para eliminar.
CREATE TABLE sensor_events (
    id                TEXT PRIMARY KEY NOT NULL,
    source_event_id   TEXT NOT NULL UNIQUE,
    monitor_key       TEXT NOT NULL,
    bed_id            TEXT NULL,
    resident_id       TEXT NULL,
    kind              TEXT NOT NULL,
    room_state        TEXT NULL,
    substate          TEXT NULL,
    zone              TEXT NULL,
    state             TEXT NULL,
    sleeping          INTEGER NULL,
    occurred_at       TEXT NOT NULL,
    received_at       TEXT NOT NULL,
    payload_json      TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_sensor_events_bed_time ON sensor_events (bed_id, occurred_at);

-- Los eventos sin resolver son una superficie que alguien tiene que mirar, no
-- un residuo. El indice parcial hace que contarlos sea trivial.
CREATE INDEX idx_sensor_events_unresolved ON sensor_events (monitor_key)
    WHERE bed_id IS NULL;

-- Estado actual: proyeccion del ultimo evento por cama. Reemplazable y
-- reconstruible, nunca fuente de verdad.
--
-- Dos ausencias deliberadas respecto del modelo de Node:
--
--   * `sleeping` es NULL-able y sin default. `DEFAULT 0` convertiria "no se" en
--     "no esta durmiendo", que es la invariante 4 rota en el propio esquema.
--   * NO existe `alert_level`. Es un veredicto de politica, y el detector
--     informa observaciones mientras la politica decide (invariante 8).
--     Persistirlo aca reimportaria el defecto que el rewrite existe para sacar.
--
-- La frescura tampoco se persiste: se deriva de `updated_at`.
CREATE TABLE current_bed_states (
    bed_id            TEXT PRIMARY KEY NOT NULL,
    resident_id       TEXT NULL,
    room_state        TEXT NULL,
    state             TEXT NOT NULL,
    substate          TEXT NULL,
    sleeping          INTEGER NULL,
    state_since       TEXT NULL,
    updated_at        TEXT NOT NULL,
    source            TEXT NOT NULL,
    source_event_id   TEXT NULL
);
