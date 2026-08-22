use mana_nats::{NatsBroker, topics::topics};

/// Test that NatsBroker can be created (requires NATS server running)
#[tokio::test]
async fn test_broker_creation() {
    // This test requires a NATS server running on localhost:4222
    // Skip if not available
    let broker = match NatsBroker::new("nats://localhost:4222").await {
        Ok(broker) => broker,
        Err(_) => {
            println!("NATS server not available, skipping test");
            return;
        }
    };

    assert!(broker.is_connected());
}

/// Test that topics are defined correctly
#[test]
fn test_topics_defined() {
    assert_eq!(topics::EVT_PERCEPTION, "evt_perception");
    assert_eq!(topics::EVT_SCENE, "evt_scene");
    assert_eq!(topics::EVT_NOTIF, "evt_notif");
}

/// Test that NotificationEvent can be serialized and deserialized
#[test]
fn test_notification_event_serialization() {
    let notif = mana_nats::NotificationEvent {
        category: "alarm".to_string(),
        bed_id: "118-A".to_string(),
        resident_id: Some("res-001".to_string()),
        event_type: "Transition".to_string(),
        timestamp: "2026-08-20T02:00:00Z".to_string(),
        rule_id: Some("dwell_Standing".to_string()),
        risk_level: Some("high".to_string()),
        trace_id: None,
    };

    let json = serde_json::to_string(&notif).unwrap();
    let deserialized: mana_nats::NotificationEvent = serde_json::from_str(&json).unwrap();

    assert_eq!(deserialized.category, "alarm");
    assert_eq!(deserialized.bed_id, "118-A");
    assert_eq!(deserialized.resident_id, Some("res-001".to_string()));
    assert_eq!(deserialized.event_type, "Transition");
    assert_eq!(deserialized.rule_id, Some("dwell_Standing".to_string()));
    assert_eq!(deserialized.risk_level, Some("high".to_string()));
}
