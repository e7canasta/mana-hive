diesel::table! {
    alerts (id) {
        id -> Text,
        resident_id -> Nullable<Text>,
        bed_id -> Text,
        evidence_kind -> Text,
        evidence_ref -> Nullable<Text>,
        rule_id -> Text,
        level -> Text,
        status -> Text,
        status_actor_id -> Nullable<Text>,
        status_at -> Nullable<Text>,
        title -> Text,
        detail -> Nullable<Text>,
        occurred_at -> Text,
        escalation_level -> Integer,
        escalated_at -> Nullable<Text>,
        escalated_to -> Nullable<Text>,
        created_at -> Text,
        updated_at -> Text,
    }
}

diesel::table! {
    alert_transitions (id) {
        id -> Text,
        alert_id -> Text,
        from_status -> Nullable<Text>,
        to_status -> Text,
        actor_id -> Nullable<Text>,
        occurred_at -> Text,
        sequence -> Integer,
    }
}

diesel::table! {
    notification_deliveries (id) {
        id -> Text,
        alert_id -> Text,
        recipient_kind -> Text,
        recipient_id -> Text,
        channel -> Text,
        escalation_level -> Integer,
        created_at -> Text,
    }
}

diesel::table! {
    notification_delivery_events (id) {
        id -> Text,
        delivery_id -> Text,
        kind -> Text,
        reason -> Nullable<Text>,
        occurred_at -> Text,
    }
}

diesel::table! {
    alert_escalations (id) {
        id -> Text,
        alert_id -> Text,
        level -> Integer,
        target_id -> Text,
        occurred_at -> Text,
        created_at -> Text,
    }
}

diesel::joinable!(alert_transitions -> alerts (alert_id));
diesel::joinable!(notification_deliveries -> alerts (alert_id));
diesel::joinable!(notification_delivery_events -> notification_deliveries (delivery_id));
diesel::joinable!(alert_escalations -> alerts (alert_id));
