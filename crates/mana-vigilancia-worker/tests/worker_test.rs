use std::sync::Arc;

use ctx_vigilancia::VigilanciaStore;
use mana_nats::NatsBroker;
use mana_storage::build_pool;

/// Test that VigilanciaWorker can be created
#[test]
fn test_vigilancia_worker_creation() {
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

        let pool = build_pool(":memory:").unwrap();
        ctx_vigilancia::run_migrations(&pool).unwrap();
        let store = VigilanciaStore::new(pool);
        
        let worker = mana_vigilancia_worker::VigilanciaWorker::new(broker, store);
        
        // Just test that it can be created
        drop(worker);
    });
}
