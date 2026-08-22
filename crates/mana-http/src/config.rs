use std::env;

use thiserror::Error;

/// Configuracion del proceso HTTP.
#[derive(Clone, Debug)]
pub struct HubConfig {
    pub host: String,
    pub port: u16,
    /// Token estatico para server-to-server. No vence.
    pub server_token: Option<String>,
}

#[derive(Debug, Error)]
pub enum ConfigError {
    #[error("API_PORT no es un puerto valido: {0}")]
    InvalidPort(String),
    #[error("API_HOST no puede estar vacio")]
    EmptyHost,
}

impl HubConfig {
    pub fn new(host: impl Into<String>, port: u16) -> Result<Self, ConfigError> {
        let host = host.into();
        if host.trim().is_empty() {
            return Err(ConfigError::EmptyHost);
        }
        Ok(Self {
            host,
            port,
            server_token: None,
        })
    }

    pub fn from_env() -> Result<Self, ConfigError> {
        let host = env::var("API_HOST").unwrap_or_else(|_| "0.0.0.0".to_owned());
        let port_value = env::var("API_PORT").unwrap_or_else(|_| "8780".to_owned());
        let port = port_value
            .parse::<u16>()
            .map_err(|_| ConfigError::InvalidPort(port_value.clone()))?;
        let server_token = env::var("MANA_HUB_SERVER_TOKEN").ok().filter(|s| !s.is_empty());
        Ok(Self {
            host,
            port,
            server_token,
        })
    }
}
