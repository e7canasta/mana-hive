//! Shared worker loop utilities: tracing init, shutdown signal, NATS connection.
//!
//! Eliminates boilerplate across mana-engine, mana-sentinel, mana-vigilancia.

use std::sync::Arc;

use tokio::sync::broadcast;
use tracing::info;
use tracing_subscriber::EnvFilter;

use crate::NatsBroker;

/// Initialize tracing with env filter and standard formatting.
pub fn init_tracing(default_level: &str) {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| EnvFilter::new(default_level)),
        )
        .with_ansi(false)
        .with_target(true)
        .with_file(true)
        .with_line_number(true)
        .init();
}

/// Spawn a task that listens for SIGTERM/SIGINT and sends shutdown signal.
pub fn spawn_shutdown_handler(shutdown: broadcast::Sender<()>) {
    tokio::spawn(async move {
        shutdown_signal(shutdown).await;
    });
}

/// Await SIGTERM or CTRL+C, then send shutdown signal.
pub async fn shutdown_signal(shutdown: broadcast::Sender<()>) {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{signal, SignalKind};

        let mut terminate = signal(SignalKind::terminate()).expect("SIGTERM handler");
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {}
            _ = terminate.recv() => {}
        }
    }

    #[cfg(not(unix))]
    {
        let _ = tokio::signal::ctrl_c().await;
    }

    info!("Shutdown signal received");
    let _ = shutdown.send(());
}

/// Connect to NATS and return broker + shutdown channel.
///
/// Returns `(broker, shutdown_sender)` ready for use in a worker.
pub async fn connect_nats(nats_url: &str) -> Result<(Arc<NatsBroker>, broadcast::Sender<()>), crate::NatsError> {
    let broker = Arc::new(NatsBroker::new(nats_url).await?);
    let (shutdown, _) = broadcast::channel::<()>(1);
    spawn_shutdown_handler(shutdown.clone());
    Ok((broker, shutdown))
}
