diesel::table! {
    residents (id) {
        id -> Text,
        external_id -> Nullable<Text>,
        full_name -> Text,
        birth_date -> Nullable<Text>,
        admission_date -> Nullable<Text>,
        status -> Text,
        discharged_at -> Nullable<Text>,
        discharged_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    resident_bed_assignments (id) {
        /// `rowid` implicito de SQLite: orden de insercion. `starts_at` tiene
        /// milisegundos y dos asignaciones seguidas empatan, dejando que
        /// desempate un id aleatorio.
        rowid -> BigInt,
        id -> Text,
        resident_id -> Text,
        bed_id -> Text,
        starts_at -> Text,
        ends_at -> Nullable<Text>,
        created_at -> Text,
        created_by -> Nullable<Text>,
    }
}

diesel::table! {
    resident_attributes (id) {
        id -> Text,
        resident_id -> Text,
        code -> Text,
        value -> Text,
        source -> Text,
        source_ref -> Nullable<Text>,
        recorded_by -> Nullable<Text>,
        recorded_at -> Text,
        valid_from -> Text,
        valid_to -> Nullable<Text>,
    }
}

diesel::joinable!(resident_bed_assignments -> residents (resident_id));
diesel::joinable!(resident_attributes -> residents (resident_id));

diesel::allow_tables_to_appear_in_same_query!(
    residents,
    resident_bed_assignments,
    resident_attributes,
);
