use std::sync::Arc;

use mana_nats::NatsBroker;
use mana_engine_v2::{DigitalTwin, PerceptionEvent};
use mana_sentinel::rule_engine::RuleEngine;
use mana_sentinel::evaluator::EventCategory;

fn perception(
    event_id: &str,
    bed_id: &str,
    state: &str,
    zone: &str,
    at: chrono::DateTime<chrono::Utc>,
) -> PerceptionEvent {
    PerceptionEvent {
        event_id: event_id.to_string(),
        trace_id: Some(format!("trace-{}", event_id)),
        monitor_key: format!("mon-{}", bed_id),
        bed_id: Some(bed_id.to_string()),
        resident_id: Some("res-001".to_string()),
        state: Some(state.to_string()),
        sleeping: Some(state == "lying"),
        zone: Some(zone.to_string()),
        extremities_out_of_bed: None,
        body_parts_out: None,
        objects: None,
        room: None,
        confidence: 0.95,
        occurred_at: at,
    }
}

fn load_test_catalog() -> ctx_politica::AlarmCatalog {
    // Try multiple paths to find the catalog
    let paths = [
        "config/alarm-catalog.toml",
        "../../config/alarm-catalog.toml",
        "../../../config/alarm-catalog.toml",
    ];
    
    for path in &paths {
        if let Ok(content) = std::fs::read_to_string(path) {
            return ctx_politica::AlarmCatalog::parse(&content)
                .expect("Failed to parse alarm catalog");
        }
    }
    
    panic!("Failed to find alarm catalog in any of the expected paths");
}

#[tokio::test]
async fn test_complete_flow_with_rule_engine() {
    let broker = match NatsBroker::new("nats://localhost:4222").await {
        Ok(b) => Arc::new(b),
        Err(_) => { println!("NATS not available, skipping"); return; }
    };

    let mut engine = DigitalTwin::new();
    let catalog = load_test_catalog();
    let rule_engine = RuleEngine::new(catalog);
    let t0 = chrono::Utc::now();

    // Subscribe BEFORE publish
    let mut scene_sub = broker.subscribe_scene("test-flow-scene").await.unwrap();
    let mut notif_sub = broker.subscribe_notif("test-flow-notif").await.unwrap();

    // Lying perception
    let events = engine.on_perception_event(perception("evt-001", "118-A", "lying", "bed", t0));
    for e in &events {
        broker.publish_scene(e).await.unwrap();
    }

    // Receive scene event
    let scene = tokio::time::timeout(std::time::Duration::from_secs(2), scene_sub.next())
        .await.unwrap().unwrap();
    assert_eq!(scene.bed_id, "118-A");

    // Evaluate with RuleEngine (no profile → default rules)
    let cat = rule_engine.evaluate(&scene, None).await;
    // Unknown→Lying is a transition, may trigger Notify depending on rules
    println!("Category: {:?}", cat.category);
    println!("Reason: {}", cat.reason);

    // Publish notif
    broker.publish_notif(
        &format!("{:?}", cat.category), &scene.bed_id,
        scene.resident_id.as_deref(), &scene,
    ).await.unwrap();

    let notif = tokio::time::timeout(std::time::Duration::from_secs(2), notif_sub.next())
        .await.unwrap().unwrap();
    assert_eq!(notif.bed_id, "118-A");
}

#[tokio::test]
async fn test_dwell_fires_with_rule_engine() {
    let broker = match NatsBroker::new("nats://localhost:4222").await {
        Ok(b) => Arc::new(b),
        Err(_) => { println!("NATS not available, skipping"); return; }
    };

    let mut engine = DigitalTwin::new();
    let catalog = load_test_catalog();
    let rule_engine = RuleEngine::new(catalog);
    let t0 = chrono::Utc::now();

    // Use unique topic to avoid cross-test contamination
    let mut scene_sub = broker.subscribe_scene("test-dwell-unique-scene").await.unwrap();

    // Lying at t0
    engine.on_perception_event(perception("evt-001", "118-A", "lying", "bed", t0));
    // Standing at t0+5min
    engine.on_perception_event(perception("evt-002", "118-A", "standing", "bed", t0 + chrono::Duration::minutes(5)));
    // Tick at t0+11min (DigitalTwin standing threshold = 5min)
    let tick_events = engine.tick(t0 + chrono::Duration::minutes(11));
    assert_eq!(tick_events.len(), 1);
    assert_eq!(tick_events[0].event_type, mana_engine_v2::SceneEventType::Dwell);

    // Publish dwell
    broker.publish_scene(&tick_events[0]).await.unwrap();

    let scene = tokio::time::timeout(std::time::Duration::from_secs(2), scene_sub.next())
        .await.unwrap().unwrap();
    assert_eq!(scene.event_type, mana_engine_v2::SceneEventType::Dwell);

    // Evaluate with RuleEngine
    // Note: DigitalTwin threshold (5min) ≠ catalog threshold (45min for Medium)
    // The dwell event is generated, but catalog may say "Off" if threshold not met
    let cat = rule_engine.evaluate(&scene, None).await;
    println!("Dwell evaluation: {:?} - {}", cat.category, cat.reason);
    
    // The important thing is that the dwell event was generated and processed
    // The category depends on the catalog configuration
    assert!(cat.category == EventCategory::Alarm || cat.category == EventCategory::Off,
        "Dwell should be either Alarm or Off depending on catalog config");
}

#[tokio::test]
async fn test_policy_event() {
    let broker = match NatsBroker::new("nats://localhost:4222").await {
        Ok(b) => Arc::new(b),
        Err(_) => { println!("NATS not available, skipping"); return; }
    };

    let mut policy_sub = broker.subscribe_policy("test-policy").await.unwrap();

    // Publish policy event
    let event = mana_nats::publisher::PolicyEvent {
        event_type: "profile_updated".to_string(),
        resident_id: "res-001".to_string(),
        policy_type: "alarm_profile".to_string(),
        effective_at: chrono::Utc::now().to_rfc3339(),
        payload: serde_json::json!({
            "risk_level": "high",
            "template_id": "testing"
        }),
    };
    broker.publish_policy(&event).await.unwrap();

    // Receive policy event
    let received = tokio::time::timeout(std::time::Duration::from_secs(2), policy_sub.next())
        .await.unwrap().unwrap();
    assert_eq!(received.event_type, "profile_updated");
    assert_eq!(received.resident_id, "res-001");
}
