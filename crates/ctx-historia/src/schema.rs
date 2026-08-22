diesel::table! {
    incident_detections (id) {
        id -> Text,
        source_record_id -> Text,
        resident_id -> Text,
        bed_id -> Nullable<Text>,
        source_alert_id -> Nullable<Text>,
        kind -> Text,
        severity -> Text,
        occurred_at -> Text,
        location -> Nullable<Text>,
        activity -> Nullable<Text>,
        injury_status -> Text,
        self_recovery -> Nullable<Integer>,
        response_seconds -> Nullable<Integer>,
        narrative -> Nullable<Text>,
        interventions_json -> Text,
        source -> Text,
        model_version -> Text,
        confidence -> Nullable<Double>,
        provenance_json -> Text,
        created_at -> Text,
    }
}

diesel::table! {
    incident_reviews (id) {
        /// `rowid` implicito de SQLite: es el orden de insercion, y para un log
        /// append-only ese es el orden real. No se selecciona nunca; existe
        /// para poder ordenar sin depender del reloj.
        rowid -> BigInt,
        id -> Text,
        incident_id -> Text,
        status -> Text,
        detection_verdict -> Nullable<Text>,
        review_note -> Nullable<Text>,
        resolved_at -> Nullable<Text>,
        actor_id -> Text,
        created_at -> Text,
    }
}

diesel::allow_tables_to_appear_in_same_query!(incident_detections, incident_reviews,);
