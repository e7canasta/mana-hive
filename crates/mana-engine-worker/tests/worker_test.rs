use std::sync::Arc;

use mana_engine_v2::DigitalTwin;
use mana_nats::NatsBroker;
use tokio::sync::RwLock;

/// Test that EngineWorker can be created
#[test]
fn test_engine_worker_creation() {
    // This test requires a NATS server running on localhost:4222
    // Skip if not available
    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(async {
        let broker = match NatsBroker::new("nats://localhost:4222").await {
            Ok(broker) => Arc::new(broker),
            Err(_) => {
                println!("NATS server not available, skipping test");
                return;
            }
        };

        let twin = Arc::new(RwLock::new(DigitalTwin::new()));
        let worker = mana_engine_worker::EngineWorker::new(broker, twin);
        
        // Just test that it can be created
        drop(worker);
    });
}
