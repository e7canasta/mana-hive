use std::{env, fmt};

use reqwest::{header::CONTENT_TYPE, Client, Method, StatusCode, Url};
use serde::{de::DeserializeOwned, Serialize};
use serde_json::Value;
use thiserror::Error;

pub const DEFAULT_BASE_URL: &str = "http://localhost:8780";

#[derive(Debug, Error)]
pub enum ManaError {
    #[error("URL base invalida: {0}")]
    InvalidBaseUrl(String),
    #[error("ruta HTTP invalida: {0}")]
    InvalidPath(String),
    #[error("fallo de red: {0}")]
    Request(#[source] reqwest::Error),
    #[error("no se pudo serializar el JSON: {0}")]
    Serialize(#[source] serde_json::Error),
    #[error("respuesta HTTP {status}: {message}")]
    Http {
        status: u16,
        message: String,
        body: Option<Value>,
    },
    #[error("respuesta HTTP {status} no contiene JSON valido: {source}")]
    InvalidResponseJson {
        status: u16,
        #[source]
        source: serde_json::Error,
    },
    #[error("respuesta HTTP {status} no contiene body")]
    MissingResponseBody { status: u16 },
    #[error("el bearer token no puede estar vacio")]
    EmptyBearerToken,
}

#[derive(Clone, Debug, Serialize)]
pub struct ApiResponse<T> {
    pub status: u16,
    pub data: Option<T>,
}

impl<T> ApiResponse<T> {
    pub fn is_success(&self) -> bool {
        (200..300).contains(&self.status)
    }

    pub fn status_code(&self) -> StatusCode {
        StatusCode::from_u16(self.status).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR)
    }

    pub fn map<U, F>(self, map: F) -> ApiResponse<U>
    where
        F: FnOnce(T) -> U,
    {
        ApiResponse {
            status: self.status,
            data: self.data.map(map),
        }
    }

    pub fn require_data(self) -> Result<T, ManaError> {
        self.data.ok_or(ManaError::MissingResponseBody {
            status: self.status,
        })
    }
}

#[derive(Clone, Eq, PartialEq)]
pub struct BearerToken(String);

impl BearerToken {
    pub fn new(value: impl Into<String>) -> Result<Self, ManaError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(ManaError::EmptyBearerToken);
        }
        Ok(Self(value))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn into_inner(self) -> String {
        self.0
    }
}

impl fmt::Debug for BearerToken {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple("BearerToken")
            .field(&"[redacted]")
            .finish()
    }
}

impl TryFrom<String> for BearerToken {
    type Error = ManaError;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::new(value)
    }
}

impl TryFrom<&str> for BearerToken {
    type Error = ManaError;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        Self::new(value)
    }
}

impl From<BearerToken> for String {
    fn from(value: BearerToken) -> Self {
        value.0
    }
}

#[derive(Clone)]
pub struct ManaClient {
    http: Client,
    base_url: Url,
    bearer_token: Option<BearerToken>,
}

impl fmt::Debug for ManaClient {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("ManaClient")
            .field("base_url", &self.base_url)
            .field("bearer_token", &self.bearer_token)
            .finish()
    }
}

impl ManaClient {
    pub fn new(base_url: impl AsRef<str>) -> Result<Self, ManaError> {
        let source = base_url.as_ref().trim();
        let parsed = Url::parse(source)
            .map_err(|error| ManaError::InvalidBaseUrl(format!("{source} ({error})")))?;
        if !matches!(parsed.scheme(), "http" | "https") || parsed.host_str().is_none() {
            return Err(ManaError::InvalidBaseUrl(source.to_owned()));
        }
        let mut base_url = parsed;
        if base_url.path().is_empty() {
            base_url.set_path("/");
        }
        Ok(Self {
            http: Client::new(),
            base_url,
            bearer_token: None,
        })
    }

    pub fn from_env() -> Result<Self, ManaError> {
        let base_url = env::var("MANA_HUB_URL")
            .ok()
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| DEFAULT_BASE_URL.to_owned());
        Self::new(base_url)
    }

    pub fn with_http_client(base_url: impl AsRef<str>, http: Client) -> Result<Self, ManaError> {
        let mut client = Self::new(base_url)?;
        client.http = http;
        Ok(client)
    }

    pub fn base_url(&self) -> &Url {
        &self.base_url
    }

    pub fn bearer_token(&self) -> Option<&BearerToken> {
        self.bearer_token.as_ref()
    }

    pub fn set_bearer_token(&mut self, token: BearerToken) {
        self.bearer_token = Some(token);
    }

    pub fn set_token(&mut self, token: impl Into<String>) -> Result<(), ManaError> {
        self.set_bearer_token(BearerToken::new(token)?);
        Ok(())
    }

    pub fn clear_bearer_token(&mut self) {
        self.bearer_token = None;
    }

    pub async fn request<T>(&self, method: Method, path: &str) -> Result<ApiResponse<T>, ManaError>
    where
        T: DeserializeOwned,
    {
        self.send(method, path, None).await
    }

    pub fn request_raw(&self, method: Method, path: &str) -> reqwest::RequestBuilder {
        let url = self
            .endpoint(path)
            .unwrap_or_else(|_| self.base_url.join(path).unwrap_or(self.base_url.clone()));
        let mut request = self.http.request(method, url);
        if let Some(token) = &self.bearer_token {
            request = request.bearer_auth(token.as_str());
        }
        request
    }

    pub async fn request_json<T, B>(
        &self,
        method: Method,
        path: &str,
        body: B,
    ) -> Result<ApiResponse<T>, ManaError>
    where
        T: DeserializeOwned,
        B: Serialize,
    {
        let body = serde_json::to_vec(&body).map_err(ManaError::Serialize)?;
        self.send(method, path, Some(body)).await
    }

    /// Igual que [`ManaClient::request_json`] pero con headers extra.
    ///
    /// Existe para las rutas `/internal/`, que no autentican con sesion sino
    /// con un secreto compartido del bridge. Es un canal distinto y conviene
    /// que se note en el tipo.
    pub async fn request_json_with_headers<T, B>(
        &self,
        method: Method,
        path: &str,
        body: B,
        headers: &[(&str, &str)],
    ) -> Result<ApiResponse<T>, ManaError>
    where
        T: DeserializeOwned,
        B: Serialize,
    {
        let body = serde_json::to_vec(&body).map_err(ManaError::Serialize)?;
        let owned: Vec<(String, String)> = headers
            .iter()
            .map(|(key, value)| ((*key).to_owned(), (*value).to_owned()))
            .collect();
        self.send_with_headers(method, path, Some(body), &owned)
            .await
    }

    async fn send<T>(
        &self,
        method: Method,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<ApiResponse<T>, ManaError>
    where
        T: DeserializeOwned,
    {
        self.send_with_headers(method, path, body, &[]).await
    }

    async fn send_with_headers<T>(
        &self,
        method: Method,
        path: &str,
        body: Option<Vec<u8>>,
        headers: &[(String, String)],
    ) -> Result<ApiResponse<T>, ManaError>
    where
        T: DeserializeOwned,
    {
        let url = self.endpoint(path)?;
        let mut request = self.http.request(method, url);
        if let Some(token) = &self.bearer_token {
            request = request.bearer_auth(token.as_str());
        }
        for (key, value) in headers {
            request = request.header(key.as_str(), value.as_str());
        }
        if let Some(body) = body {
            request = request.header(CONTENT_TYPE, "application/json").body(body);
        }

        let response = request.send().await.map_err(ManaError::Request)?;
        let status = response.status();
        let status_code = status.as_u16();
        let bytes = response.bytes().await.map_err(ManaError::Request)?;

        if !status.is_success() {
            let body = serde_json::from_slice::<Value>(&bytes).ok();
            let message = http_error_message(status, &bytes, body.as_ref());
            return Err(ManaError::Http {
                status: status_code,
                message,
                body,
            });
        }

        if bytes.is_empty() || bytes.iter().all(|byte| byte.is_ascii_whitespace()) {
            return Ok(ApiResponse {
                status: status_code,
                data: None,
            });
        }

        let data =
            serde_json::from_slice(&bytes).map_err(|source| ManaError::InvalidResponseJson {
                status: status_code,
                source,
            })?;
        Ok(ApiResponse {
            status: status_code,
            data: Some(data),
        })
    }

    fn endpoint(&self, path: &str) -> Result<Url, ManaError> {
        let path = path.trim();
        let (path_only, query) = path.split_once('?').unwrap_or((path, ""));
        if path_only.is_empty() || !path_only.starts_with('/') {
            return Err(ManaError::InvalidPath(path.to_owned()));
        }

        let base_path = self.base_url.path().trim_end_matches('/');
        let combined_path = if base_path.is_empty() {
            path_only.to_owned()
        } else {
            format!("{base_path}{path_only}")
        };
        let mut endpoint = self.base_url.clone();
        endpoint.set_path(&combined_path);
        endpoint.set_query((!query.is_empty()).then_some(query));
        endpoint.set_fragment(None);
        Ok(endpoint)
    }
}

fn http_error_message(status: StatusCode, bytes: &[u8], body: Option<&Value>) -> String {
    if let Some(message) = body
        .and_then(|value| value.get("error"))
        .and_then(|error| error.get("message"))
        .and_then(Value::as_str)
    {
        return message.to_owned();
    }

    let text = String::from_utf8_lossy(bytes).trim().to_owned();
    if text.is_empty() {
        status
            .canonical_reason()
            .unwrap_or("respuesta HTTP no exitosa")
            .to_owned()
    } else {
        text
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bearer_token_debug_is_redacted() {
        let token = BearerToken::new("secret-token").unwrap();
        assert!(!format!("{token:?}").contains("secret-token"));
    }

    #[test]
    fn empty_bearer_token_is_rejected() {
        assert!(matches!(
            BearerToken::new("  "),
            Err(ManaError::EmptyBearerToken)
        ));
    }

    #[test]
    fn base_path_is_preserved_when_building_requests() {
        let client = ManaClient::new("http://localhost:8780/gateway").unwrap();
        let endpoint = client.endpoint("/health").unwrap();
        assert_eq!(endpoint.as_str(), "http://localhost:8780/gateway/health");
    }
}
