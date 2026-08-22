diesel::table! {
    staff_groups (id) {
        id -> Text,
        facility_id -> Text,
        name -> Text,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    staff_group_members (id) {
        id -> Text,
        staff_group_id -> Text,
        user_id -> Text,
        valid_from -> Text,
        valid_to -> Nullable<Text>,
        created_at -> Text,
    }
}

diesel::table! {
    facility_shifts (id) {
        id -> Text,
        facility_id -> Text,
        key -> Text,
        label -> Text,
        start_minute -> Integer,
        sort_order -> Integer,
        retired_at -> Nullable<Text>,
        retired_by -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    unit_shift_coverages (id) {
        id -> Text,
        wing_id -> Text,
        staff_group_id -> Nullable<Text>,
        shift_key -> Text,
        valid_from -> Text,
        valid_to -> Nullable<Text>,
        created_at -> Text,
        created_by -> Nullable<Text>,
    }
}

diesel::joinable!(staff_group_members -> staff_groups (staff_group_id));

diesel::allow_tables_to_appear_in_same_query!(
    staff_groups,
    staff_group_members,
    facility_shifts,
    unit_shift_coverages,
);
