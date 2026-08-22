use std::{
    collections::{BTreeMap, HashMap},
    env, fs,
    path::Path,
};

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use thiserror::Error;

use crate::{health, AuditQuery, ManaClient, ManaError};

#[derive(Debug, Error)]
pub enum SceneError {
    #[error("no se pudo leer la escena {path}: {source}")]
    Read {
        path: String,
        #[source]
        source: std::io::Error,
    },
    #[error("JSON de escena invalido: {0}")]
    Json(#[from] serde_json::Error),
    #[error("escena invalida: {0}")]
    Validation(String),
    #[error("comando {command}: {message}")]
    Command { command: String, message: String },
    #[error("comando {command}: no se pudo serializar la respuesta: {source}")]
    ResponseJson {
        command: String,
        #[source]
        source: serde_json::Error,
    },
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct SceneMeta {
    #[serde(default)]
    pub id: String,
    #[serde(default, alias = "titulo")]
    pub title: String,
    #[serde(default, alias = "descripcion")]
    pub description: String,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct Scene {
    #[serde(default)]
    pub meta: SceneMeta,
    #[serde(default, alias = "contexto")]
    pub context: String,
    #[serde(default, alias = "actions", alias = "acciones")]
    pub commands: Vec<SceneCommand>,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct SceneCommand {
    #[serde(default)]
    pub name: String,
    #[serde(alias = "command", alias = "op")]
    pub action: String,
    #[serde(default, alias = "input", alias = "params", alias = "with")]
    pub args: Map<String, Value>,
    #[serde(default, rename = "assert", alias = "assertions")]
    pub assertions: BTreeMap<String, Value>,
    #[serde(default)]
    pub status: Option<u16>,
    #[serde(default)]
    pub capture: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
pub struct SceneCommandResult {
    pub name: String,
    pub action: String,
    pub status: u16,
    pub assertions: usize,
    pub response: Option<Value>,
}

#[derive(Clone, Debug, Serialize)]
pub struct SceneRunReport {
    pub scene_id: String,
    pub context: String,
    pub commands: Vec<SceneCommandResult>,
}

impl Scene {
    pub fn from_file(path: impl AsRef<Path>) -> Result<Self, SceneError> {
        let path = path.as_ref();
        let source = fs::read_to_string(path).map_err(|source| SceneError::Read {
            path: path.display().to_string(),
            source,
        })?;
        Self::from_json(&source)
    }

    pub fn from_json(source: &str) -> Result<Self, SceneError> {
        serde_json::from_str(source).map_err(SceneError::Json)
    }

    pub fn validate(&self) -> Result<(), SceneError> {
        if self.meta.id.trim().is_empty() {
            return Err(SceneError::Validation("meta.id es obligatorio".to_owned()));
        }
        if self.context.trim().is_empty() {
            return Err(SceneError::Validation("contexto es obligatorio".to_owned()));
        }
        if self.commands.is_empty() {
            return Err(SceneError::Validation(
                "commands no puede estar vacio".to_owned(),
            ));
        }

        for command in &self.commands {
            let name = command.label();
            let Some(action) = canonical_action(&command.action) else {
                return Err(SceneError::Validation(format!(
                    "{name} usa una accion desconocida: {}",
                    command.action
                )));
            };
            if let Some(status) = command.status {
                if !(100..=599).contains(&status) {
                    return Err(SceneError::Validation(format!(
                        "{name} tiene un status invalido: {status}"
                    )));
                }
            }
            validate_arguments(command, action)?;
            for assertion in command.assertions.keys() {
                if assertion.trim().is_empty() {
                    return Err(SceneError::Validation(format!(
                        "{name} tiene una assertion vacia"
                    )));
                }
            }
        }
        Ok(())
    }
}

impl SceneCommand {
    pub fn label(&self) -> &str {
        if self.name.trim().is_empty() {
            self.action.trim()
        } else {
            self.name.trim()
        }
    }
}

pub fn validate_scene(scene: &Scene) -> Result<(), SceneError> {
    scene.validate()
}

pub struct SceneRunner {
    client: ManaClient,
    captures: HashMap<String, Value>,
}

impl SceneRunner {
    pub fn new(client: ManaClient) -> Self {
        Self {
            client,
            captures: HashMap::new(),
        }
    }

    pub fn client(&self) -> &ManaClient {
        &self.client
    }

    pub fn client_mut(&mut self) -> &mut ManaClient {
        &mut self.client
    }

    pub async fn run(&mut self, scene: &Scene) -> Result<SceneRunReport, SceneError> {
        scene.validate()?;
        self.captures.clear();
        let mut commands = Vec::with_capacity(scene.commands.len());
        for command in &scene.commands {
            commands.push(self.run_command(command).await?);
        }
        Ok(SceneRunReport {
            scene_id: scene.meta.id.clone(),
            context: scene.context.clone(),
            commands,
        })
    }

    pub async fn run_file(&mut self, path: impl AsRef<Path>) -> Result<SceneRunReport, SceneError> {
        let scene = Scene::from_file(path)?;
        self.run(&scene).await
    }

    async fn run_command(
        &mut self,
        command: &SceneCommand,
    ) -> Result<SceneCommandResult, SceneError> {
        let command_name = command.label().to_owned();
        let action = canonical_action(&command.action).ok_or_else(|| {
            SceneError::Validation(format!(
                "{command_name} usa una accion desconocida: {}",
                command.action
            ))
        })?;
        let args = resolve_args(&self.captures, &command.args)
            .map_err(|message| command_error(command, message))?;
        let execution = self.execute(command, action, &args).await?;
        let expected_status = command.status.unwrap_or_else(|| default_status(action));
        if execution.status != expected_status {
            return Err(SceneError::Command {
                command: command_name,
                message: format!("status {}, esperaba {}", execution.status, expected_status),
            });
        }

        let assertions: Map<String, Value> = command
            .assertions
            .iter()
            .map(|(path, value)| (path.clone(), value.clone()))
            .collect();
        let assertions = resolve_args(&self.captures, &assertions)
            .map_err(|message| command_error(command, message))?;
        for (path, expected) in &assertions {
            let actual = value_at(execution.response.as_ref(), path).unwrap_or(Value::Null);
            if actual != *expected {
                return Err(SceneError::Command {
                    command: command.label().to_owned(),
                    message: format!("assertion {path}: obtuvo {}, esperaba {}", actual, expected),
                });
            }
        }

        if let Some(capture) = command.capture.as_deref() {
            let capture = capture.trim();
            if capture.is_empty() {
                return Err(SceneError::Validation(format!(
                    "{command_name} tiene un capture vacio"
                )));
            }
            self.captures.insert(
                capture.to_owned(),
                execution.response.clone().unwrap_or(Value::Null),
            );
        }

        Ok(SceneCommandResult {
            name: command.label().to_owned(),
            action: action.to_owned(),
            status: execution.status,
            assertions: command.assertions.len(),
            response: execution
                .response
                .map(|response| report_response(action, response)),
        })
    }

    async fn execute(
        &mut self,
        command: &SceneCommand,
        action: &str,
        args: &Map<String, Value>,
    ) -> Result<Execution, SceneError> {
        match action {
            "health" => {
                let response = health(&self.client).await;
                http_execution(command, response)
            }
            "login" => {
                let username = required_string(command, "username")?;
                let password = resolve_password(command)?;
                let response = self.client.login(username, password).await;
                http_execution(command, response)
            }
            "me" => {
                let response = self.client.current_user().await;
                http_execution(command, response)
            }
            "users.list" => {
                let include_inactive = args
                    .get("include_inactive")
                    .and_then(Value::as_bool)
                    .unwrap_or(false);
                let response = self.client.list_users_with_inactive(include_inactive).await;
                http_execution(command, response)
            }
            "users.create" => {
                let request = serde_json::from_value(Value::Object(args.clone()))
                    .map_err(|error| command_error(command, error.to_string()))?;
                let response = self.client.create(request).await;
                http_execution(command, response)
            }
            "users.update" => {
                let user_id = args
                    .get("user_id")
                    .or_else(|| args.get("id"))
                    .and_then(Value::as_str)
                    .ok_or_else(|| command_error(command, "falta args.user_id"))?;
                let mut request_args = args.clone();
                request_args.remove("user_id");
                request_args.remove("id");
                let request = serde_json::from_value(Value::Object(request_args))
                    .map_err(|error| command_error(command, error.to_string()))?;
                let response = self.client.update(user_id, request).await;
                http_execution(command, response)
            }
            "audit.list" => {
                let query = AuditQuery {
                    limit: args
                        .get("limit")
                        .and_then(Value::as_u64)
                        .map(|limit| limit as usize),
                    entity_type: args
                        .get("entity_type")
                        .and_then(Value::as_str)
                        .map(str::to_owned),
                    entity_id: args
                        .get("entity_id")
                        .and_then(Value::as_str)
                        .map(str::to_owned),
                    action: args
                        .get("action")
                        .and_then(Value::as_str)
                        .map(str::to_owned),
                };
                let response = self.client.list_audit(query).await;
                http_execution(command, response)
            }
            "logout" => {
                let response = self.client.logout().await;
                http_execution(command, response)
            }
            "facilities.list" => {
                let response = self.client.list_facilities().await;
                http_execution(command, response)
            }
            "facilities.detail" => {
                let facility_id = required_arg(command, args, "facility_id")?;
                let response = self.client.facility(&facility_id).await;
                http_execution(command, response)
            }
            "facilities.create" => {
                let request = serde_json::from_value(Value::Object(args.clone()))
                    .map_err(|error| command_error(command, error.to_string()))?;
                let response = self.client.create_facility(request).await;
                http_execution(command, response)
            }
            "facilities.update" => {
                let facility_id = required_arg(command, args, "facility_id")?;
                let request = request_minus(command, args, &["facility_id"])?;
                let response = self.client.update_facility(&facility_id, request).await;
                http_execution(command, response)
            }
            "wings.list" => {
                let response = self.client.list_wings().await;
                http_execution(command, response)
            }
            "wings.create" => {
                let facility_id = required_arg(command, args, "facility_id")?;
                let request = request_minus(command, args, &["facility_id"])?;
                let response = self.client.create_wing(&facility_id, request).await;
                http_execution(command, response)
            }
            "wings.update" => {
                let wing_id = required_arg(command, args, "wing_id")?;
                let request = request_minus(command, args, &["wing_id"])?;
                let response = self.client.update_wing(&wing_id, request).await;
                http_execution(command, response)
            }
            "rooms.list" => {
                let wing_id = required_arg(command, args, "wing_id")?;
                let response = self.client.list_rooms(&wing_id).await;
                http_execution(command, response)
            }
            "rooms.create" => {
                let wing_id = required_arg(command, args, "wing_id")?;
                let request = request_minus(command, args, &["wing_id"])?;
                let response = self.client.create_room(&wing_id, request).await;
                http_execution(command, response)
            }
            "rooms.update" => {
                let room_id = required_arg(command, args, "room_id")?;
                let request = request_minus(command, args, &["room_id"])?;
                let response = self.client.update_room(&room_id, request).await;
                http_execution(command, response)
            }
            "beds.list" => {
                let room_id = required_arg(command, args, "room_id")?;
                let response = self.client.list_beds(&room_id).await;
                http_execution(command, response)
            }
            "beds.all" => {
                let response = self.client.list_residence_beds().await;
                http_execution(command, response)
            }
            "beds.create" => {
                let room_id = required_arg(command, args, "room_id")?;
                let request = request_minus(command, args, &["room_id"])?;
                let response = self.client.create_bed(&room_id, request).await;
                http_execution(command, response)
            }
            "beds.update" => {
                let bed_id = required_arg(command, args, "bed_id")?;
                let request = request_minus(command, args, &["bed_id"])?;
                let response = self.client.update_bed(&bed_id, request).await;
                http_execution(command, response)
            }
            "planogram.get" => {
                let wing_id = required_arg(command, args, "wing_id")?;
                let response = self.client.planogram(&wing_id).await;
                http_execution(command, response)
            }
            "planogram.save" => {
                let wing_id = required_arg(command, args, "wing_id")?;
                let request = request_minus(command, args, &["wing_id"])?;
                let response = self.client.save_planogram(&wing_id, request).await;
                http_execution(command, response)
            }
            "privacy.regions.get" => {
                let room_id = required_arg(command, args, "room_id")?;
                let response = self.client.privacy_regions(&room_id).await;
                http_execution(command, response)
            }
            "privacy.regions.save" => {
                let room_id = required_arg(command, args, "room_id")?;
                let request = request_minus(command, args, &["room_id"])?;
                let response = self.client.save_privacy_regions(&room_id, request).await;
                http_execution(command, response)
            }
            "residents.list" => {
                let query = args.get("q").and_then(Value::as_str).map(str::to_owned);
                let response = self.client.list_residents(query.as_deref()).await;
                http_execution(command, response)
            }
            "residents.detail" => {
                let resident_id = required_arg(command, args, "resident_id")?;
                let response = self.client.resident(&resident_id).await;
                http_execution(command, response)
            }
            "residents.create" => {
                let request = serde_json::from_value(Value::Object(args.clone()))
                    .map_err(|error| command_error(command, error.to_string()))?;
                let response = self.client.create_resident(request).await;
                http_execution(command, response)
            }
            "residents.update" => {
                let resident_id = required_arg(command, args, "resident_id")?;
                let request = request_minus(command, args, &["resident_id"])?;
                let response = self.client.update_resident(&resident_id, request).await;
                http_execution(command, response)
            }
            "residents.discharge" => {
                let resident_id = required_arg(command, args, "resident_id")?;
                let request = request_minus(command, args, &["resident_id"])?;
                let response = self.client.discharge_resident(&resident_id, request).await;
                http_execution(command, response)
            }
            "residents.assignments.list" => {
                let resident_id = required_arg(command, args, "resident_id")?;
                let response = self.client.list_assignments(&resident_id).await;
                http_execution(command, response)
            }
            "residents.assignments.create" => {
                let resident_id = required_arg(command, args, "resident_id")?;
                let request = request_minus(command, args, &["resident_id"])?;
                let response = self.client.assign_bed(&resident_id, request).await;
                http_execution(command, response)
            }
            "beds.assignment.delete" => {
                let bed_id = required_arg(command, args, "bed_id")?;
                let response = self.client.release_bed(&bed_id).await;
                http_execution(command, response)
            }
            "streams.list" => {
                let room_id = required_arg(command, args, "room_id")?;
                let response = self.client.list_streams(&room_id).await;
                http_execution(command, response)
            }
            "streams.create" => {
                let room_id = required_arg(command, args, "room_id")?;
                let request = request_minus(command, args, &["room_id"])?;
                let response = self.client.create_stream(&room_id, request).await;
                http_execution(command, response)
            }
            "streams.get" => {
                let stream_id = required_arg(command, args, "stream_id")?;
                let response = self.client.get_stream(&stream_id).await;
                http_execution(command, response)
            }
            "streams.regions" => {
                let stream_id = required_arg(command, args, "stream_id")?;
                let response = self.client.list_regions(&stream_id).await;
                http_execution(command, response)
            }
            "streams.set-regions" => {
                let stream_id = required_arg(command, args, "stream_id")?;
                let regions = args
                    .get("regions")
                    .cloned()
                    .unwrap_or(Value::Array(vec![]));
                let body = serde_json::json!({ "regions": regions });
                let request: crate::ReplaceRegionsRequest =
                    serde_json::from_value(body)
                        .map_err(|error| command_error(command, error.to_string()))?;
                let response = self.client.replace_regions(&stream_id, request).await;
                http_execution(command, response)
            }
            "streams.update-region" => {
                let stream_id = required_arg(command, args, "stream_id")?;
                let region_id = required_arg(command, args, "region_id")?;
                let request = request_minus(command, args, &["stream_id", "region_id"])?;
                let response = self.client.update_region(&stream_id, &region_id, request).await;
                http_execution(command, response)
            }
            "facilities.tree" => {
                let facility_id = required_arg(command, args, "facility_id")?;
                let response = self.client.facility_tree(&facility_id).await;
                http_execution(command, response)
            }
            "custom" => {
                let method = args.get("method").and_then(Value::as_str).unwrap_or("GET");
                let path = required_arg(command, args, "path")?;
                let body = args.get("body");
                let headers = args.get("headers");
                let method = match method.to_ascii_uppercase().as_str() {
                    "GET" => reqwest::Method::GET,
                    "POST" => reqwest::Method::POST,
                    "PUT" => reqwest::Method::PUT,
                    "PATCH" => reqwest::Method::PATCH,
                    "DELETE" => reqwest::Method::DELETE,
                    other => {
                        return Err(command_error(
                            command,
                            format!("metodo HTTP no soportado: {other}"),
                        ))
                    }
                };
                let mut request = self.client.request_raw(method, &path);
                if let Some(hdrs) = headers.and_then(Value::as_object) {
                    for (key, value) in hdrs {
                        if let Some(val) = value.as_str() {
                            request = request.header(key.as_str(), val);
                        }
                    }
                }
                if let Some(body) = body {
                    let bytes = serde_json::to_vec(body)
                        .map_err(|error| command_error(command, error.to_string()))?;
                    request = request
                        .header(reqwest::header::CONTENT_TYPE, "application/json")
                        .body(bytes);
                }
                let response = request.send().await;
                match response {
                    Ok(resp) => {
                        let status = resp.status().as_u16();
                        let bytes = resp.bytes().await.unwrap_or_default();
                        let body: Value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
                        Ok(Execution {
                            status,
                            response: Some(body),
                        })
                    }
                    Err(error) => Err(command_error(command, error.to_string())),
                }
            }
            "sleep" => {
                let seconds = args
                    .get("seconds")
                    .and_then(Value::as_f64)
                    .map(|s| s.max(0.0))
                    .unwrap_or(1.0);
                tokio::time::sleep(std::time::Duration::from_secs_f64(seconds)).await;
                Ok(Execution {
                    status: 200,
                    response: Some(serde_json::json!({ "slept_seconds": seconds })),
                })
            }
            _ => Err(SceneError::Validation(format!(
                "accion no implementada: {action}"
            ))),
        }
    }
}

fn required_arg(
    command: &SceneCommand,
    args: &Map<String, Value>,
    key: &str,
) -> Result<String, SceneError> {
    let value = args.get(key).and_then(Value::as_str);
    match value.map(str::trim).filter(|value| !value.is_empty()) {
        Some(value) => Ok(value.to_owned()),
        None => Err(command_error(command, format!("falta args.{key}"))),
    }
}

fn request_minus<T: serde::de::DeserializeOwned>(
    command: &SceneCommand,
    args: &Map<String, Value>,
    keys: &[&str],
) -> Result<T, SceneError> {
    let mut request_args = args.clone();
    for key in keys {
        request_args.remove(*key);
    }
    serde_json::from_value(Value::Object(request_args))
        .map_err(|error| command_error(command, error.to_string()))
}

struct Execution {
    status: u16,
    response: Option<Value>,
}

fn response_value<T: Serialize>(
    response: crate::ApiResponse<T>,
    command: &SceneCommand,
) -> Result<Execution, SceneError> {
    let body = response
        .data
        .map(|data| {
            serde_json::to_value(data).map_err(|source| SceneError::ResponseJson {
                command: command.label().to_owned(),
                source,
            })
        })
        .transpose()?;
    Ok(Execution {
        status: response.status,
        response: body,
    })
}

fn http_execution<T: Serialize>(
    command: &SceneCommand,
    result: Result<crate::ApiResponse<T>, ManaError>,
) -> Result<Execution, SceneError> {
    match result {
        Ok(response) => response_value(response, command),
        Err(ManaError::Http { status, body, .. }) => Ok(Execution {
            status,
            response: body
                .or_else(|| Some(serde_json::json!({ "error": { "message": "error HTTP" } }))),
        }),
        Err(error) => Err(api_command_error(command, error)),
    }
}

fn canonical_action(action: &str) -> Option<&'static str> {
    match action.trim().to_ascii_lowercase().as_str() {
        "health" | "api.health" => Some("health"),
        "custom" => Some("custom"),
        "login" | "identidad.login" | "identity.login" | "auth.login" | "auth.login.post" => {
            Some("login")
        }
        "me" | "identidad.me" | "identity.me" | "auth.me" | "auth.me.get" => Some("me"),
        "users.list" | "identidad.users.list" | "identity.users.list" | "list_users" => {
            Some("users.list")
        }
        "users.create" | "identidad.users.create" | "identity.users.create" | "create" => {
            Some("users.create")
        }
        "users.update" | "identidad.users.update" | "identity.users.update" | "update" => {
            Some("users.update")
        }
        "audit.list" | "auditoria.list" | "audit.log.list" | "audit-log.list"
        | "audit-log.list.get" => Some("audit.list"),
        "logout" | "identidad.logout" | "identity.logout" | "auth.logout" | "auth.logout.post" => {
            Some("logout")
        }
        "sleep" | "wait" => Some("sleep"),
        "facilities.list" | "residencia.facilities.list" | "facilities.list.get" => {
            Some("facilities.list")
        }
        "facilities.detail"
        | "residencia.facilities.detail"
        | "facilities.get"
        | "facilities.detail.get" => Some("facilities.detail"),
        "facilities.create" | "residencia.facilities.create" | "facilities.create.post" => {
            Some("facilities.create")
        }
        "facilities.update" | "residencia.facilities.update" | "facilities.update.patch" => {
            Some("facilities.update")
        }
        "wings.list" | "residencia.wings.list" | "wings.list.get" => Some("wings.list"),
        "wings.create"
        | "residencia.wings.create"
        | "facilities.wings.create.post"
        | "wings.create.post" => Some("wings.create"),
        "wings.update" | "residencia.wings.update" | "wings.update.patch" => Some("wings.update"),
        "rooms.list" | "residencia.rooms.list" | "wings.rooms.list.get" | "rooms.list.get" => {
            Some("rooms.list")
        }
        "rooms.create" | "residencia.rooms.create" | "wings.rooms.create.post" => {
            Some("rooms.create")
        }
        "rooms.update" | "residencia.rooms.update" | "rooms.update.patch" => Some("rooms.update"),
        "beds.list" | "residencia.beds.list" | "rooms.beds.list.get" | "beds.list.get" => {
            Some("beds.list")
        }
        "beds.all" | "residencia.beds.all" | "beds.all.get" | "beds.list.all" => Some("beds.all"),
        "beds.create" | "residencia.beds.create" | "rooms.beds.create.post" => Some("beds.create"),
        "beds.update" | "residencia.beds.update" | "beds.update.patch" => Some("beds.update"),
        "planogram.get" | "residencia.planogram.get" | "wings.planogram.get" => {
            Some("planogram.get")
        }
        "planogram.save" | "residencia.planogram.save" | "wings.planogram.put" => {
            Some("planogram.save")
        }
        "privacy.regions.get" | "residencia.privacy.regions.get" | "rooms.privacy-regions.get" => {
            Some("privacy.regions.get")
        }
        "privacy.regions.save"
        | "residencia.privacy.regions.save"
        | "rooms.privacy-regions.put" => Some("privacy.regions.save"),
        "residents.list" | "poblacion.residents.list" | "residents.list.get" => {
            Some("residents.list")
        }
        "residents.detail"
        | "poblacion.residents.detail"
        | "residents.get"
        | "residents.detail.get" => Some("residents.detail"),
        "residents.create" | "poblacion.residents.create" | "residents.create.post" => {
            Some("residents.create")
        }
        "residents.update" | "poblacion.residents.update" | "residents.update.patch" => {
            Some("residents.update")
        }
        "residents.discharge" | "poblacion.residents.discharge" | "residents.discharge.post" => {
            Some("residents.discharge")
        }
        "residents.assignments.list"
        | "poblacion.residents.assignments.list"
        | "residents.assignments.get" => Some("residents.assignments.list"),
        "residents.assignments.create"
        | "poblacion.residents.assignments.create"
        | "residents.assignments.create.post" => Some("residents.assignments.create"),
        "beds.assignment.delete"
        | "poblacion.beds.assignment.delete"
        | "beds.assignment.release" => Some("beds.assignment.delete"),
        "streams.list" | "ctx-streams.streams.list" | "streams.list.get" => Some("streams.list"),
        "streams.create" | "ctx-streams.streams.create" | "streams.create.post" => {
            Some("streams.create")
        }
        "streams.get" | "ctx-streams.streams.get" | "streams.detail.get" => Some("streams.get"),
        "streams.regions"
        | "ctx-streams.streams.regions"
        | "streams.regions.list.get" => Some("streams.regions"),
        "streams.set-regions"
        | "ctx-streams.streams.regions.replace"
        | "streams.regions.replace.put" => Some("streams.set-regions"),
        "streams.update-region"
        | "ctx-streams.streams.regions.update"
        | "streams.regions.update.patch" => Some("streams.update-region"),
        "facilities.tree" | "residencia.facilities.tree" | "facilities.tree.get" => {
            Some("facilities.tree")
        }
        _ => None,
    }
}

fn default_status(action: &str) -> u16 {
    match action {
        "users.create"
        | "facilities.create"
        | "wings.create"
        | "rooms.create"
        | "beds.create"
        | "residents.create"
        | "residents.assignments.create"
        | "streams.create" => 201,
        "logout" => 204,
        _ => 200,
    }
}

fn validate_arguments(command: &SceneCommand, action: &str) -> Result<(), SceneError> {
    match action {
        "login" => {
            required_string(command, "username")?;
            if !command.args.contains_key("password")
                && !command.args.contains_key("password_env")
                && !command.args.contains_key("password_default")
            {
                return Err(SceneError::Validation(format!(
                    "{} requiere password, password_env o password_default",
                    command.label()
                )));
            }
        }
        "users.create" => {
            for key in ["username", "display_name", "role", "password"] {
                required_string(command, key)?;
            }
        }
        "users.update" => {
            let has_id = command
                .args
                .get("user_id")
                .or_else(|| command.args.get("id"))
                .and_then(Value::as_str)
                .map(|value| !value.trim().is_empty())
                .unwrap_or(false);
            if !has_id {
                return Err(SceneError::Validation(format!(
                    "{} requiere args.user_id",
                    command.label()
                )));
            }
        }
        "facilities.create" => {
            for key in ["name", "timezone"] {
                required_string(command, key)?;
            }
        }
        "facilities.update" => {
            required_id_arg(command, "facility_id")?;
        }
        "facilities.detail" => {
            required_id_arg(command, "facility_id")?;
        }
        "wings.create" => {
            required_id_arg(command, "facility_id")?;
            for key in ["name", "floor"] {
                required_string(command, key)?;
            }
        }
        "wings.update" => {
            required_id_arg(command, "wing_id")?;
        }
        "rooms.list" | "planogram.get" | "planogram.save" => {
            required_id_arg(command, "wing_id")?;
        }
        "rooms.create" => {
            required_id_arg(command, "wing_id")?;
            for key in ["number", "type"] {
                required_string(command, key)?;
            }
        }
        "rooms.update" => {
            required_id_arg(command, "room_id")?;
        }
        "beds.list" | "privacy.regions.get" | "privacy.regions.save" => {
            required_id_arg(command, "room_id")?;
        }
        "beds.create" => {
            required_id_arg(command, "room_id")?;
            required_string(command, "label")?;
        }
        "beds.update" => {
            required_id_arg(command, "bed_id")?;
        }
        "residents.create" => {}
        "residents.detail" | "residents.update" | "residents.discharge" => {
            required_id_arg(command, "resident_id")?;
        }
        "residents.assignments.list" => {
            required_id_arg(command, "resident_id")?;
        }
        "residents.assignments.create" => {
            required_id_arg(command, "resident_id")?;
            required_string(command, "bed_id")?;
        }
        "beds.assignment.delete" => {
            required_id_arg(command, "bed_id")?;
        }
        "streams.list" => {
            required_id_arg(command, "room_id")?;
        }
        "streams.get" | "streams.regions" | "streams.set-regions" => {
            required_id_arg(command, "stream_id")?;
        }
        "streams.update-region" => {
            required_id_arg(command, "stream_id")?;
            required_id_arg(command, "region_id")?;
        }
        "streams.create" => {
            required_id_arg(command, "room_id")?;
            required_string(command, "stream_key")?;
        }
        "facilities.tree" => {
            required_id_arg(command, "facility_id")?;
        }
        _ => {}
    }
    Ok(())
}

fn required_id_arg(command: &SceneCommand, key: &str) -> Result<(), SceneError> {
    let has_id = command
        .args
        .get(key)
        .and_then(Value::as_str)
        .map(|value| !value.trim().is_empty())
        .unwrap_or(false);
    if has_id {
        Ok(())
    } else {
        Err(SceneError::Validation(format!(
            "{} requiere args.{key}",
            command.label()
        )))
    }
}

fn required_string(command: &SceneCommand, key: &str) -> Result<String, SceneError> {
    let value = command.args.get(key).and_then(Value::as_str);
    match value.map(str::trim).filter(|value| !value.is_empty()) {
        Some(value) => Ok(value.to_owned()),
        None => Err(SceneError::Validation(format!(
            "{} requiere args.{key}",
            command.label()
        ))),
    }
}

fn resolve_password(command: &SceneCommand) -> Result<String, SceneError> {
    if let Some(password) = command.args.get("password").and_then(Value::as_str) {
        if !password.is_empty() {
            return Ok(password.to_owned());
        }
    }

    if let Some(variable) = command.args.get("password_env").and_then(Value::as_str) {
        let variable = variable.strip_prefix('$').unwrap_or(variable);
        if let Ok(password) = env::var(variable) {
            if !password.is_empty() {
                return Ok(password);
            }
        }
    }

    if let Some(password) = command
        .args
        .get("password_default")
        .and_then(Value::as_str)
        .filter(|password| !password.is_empty())
    {
        return Ok(password.to_owned());
    }

    Err(command_error(
        command,
        "no se pudo resolver la contrasena desde password_env o password_default",
    ))
}

fn value_at(root: Option<&Value>, expression: &str) -> Option<Value> {
    let mut current = root?;
    for segment in expression.split('.') {
        current = match current {
            Value::Object(object) => object.get(segment)?,
            Value::Array(array) if segment == "length" => {
                return Some(Value::from(array.len()));
            }
            Value::String(value) if segment == "length" => {
                return Some(Value::from(value.chars().count()));
            }
            Value::Array(array) => array.get(segment.parse::<usize>().ok()?)?,
            _ => return None,
        };
    }
    Some(current.clone())
}

fn resolve_args(
    captures: &HashMap<String, Value>,
    args: &Map<String, Value>,
) -> Result<Map<String, Value>, String> {
    args.iter()
        .map(|(key, value)| Ok((key.clone(), resolve_value(captures, value)?)))
        .collect()
}

fn resolve_value(captures: &HashMap<String, Value>, value: &Value) -> Result<Value, String> {
    match value {
        Value::String(text) => resolve_string(captures, text),
        Value::Array(items) => items
            .iter()
            .map(|item| resolve_value(captures, item))
            .collect::<Result<Vec<_>, _>>()
            .map(Value::Array),
        Value::Object(object) => object
            .iter()
            .map(|(key, value)| Ok((key.clone(), resolve_value(captures, value)?)))
            .collect::<Result<Map<_, _>, _>>()
            .map(Value::Object),
        other => Ok(other.clone()),
    }
}

fn resolve_string(captures: &HashMap<String, Value>, text: &str) -> Result<Value, String> {
    let reference = text
        .strip_prefix("{{")
        .and_then(|rest| rest.strip_suffix("}}"))
        .map(str::trim)
        .filter(|path| !path.contains("{{"));
    if let Some(path) = reference {
        return lookup(captures, path)
            .ok_or_else(|| format!("referencia no capturada: {path}"));
    }
    if text.contains("{{") {
        let mut output = String::new();
        let mut rest = text;
        while let Some(start) = rest.find("{{") {
            output.push_str(&rest[..start]);
            let Some(end) = rest[start..].find("}}") else {
                return Err("interpolacion sin cierre".to_owned());
            };
            let path = rest[start + 2..start + end].trim();
            match lookup(captures, path) {
                Some(Value::String(string)) => output.push_str(&string),
                Some(Value::Number(number)) => output.push_str(&number.to_string()),
                Some(other) => {
                    return Err(format!("la referencia {path} no es texto: {other}"));
                }
                None => return Err(format!("referencia no capturada: {path}")),
            }
            rest = &rest[start + end + 2..];
        }
        output.push_str(rest);
        return Ok(Value::String(output));
    }
    Ok(Value::String(text.to_owned()))
}

fn lookup(captures: &HashMap<String, Value>, path: &str) -> Option<Value> {
    let (name, rest) = match path.split_once('.') {
        Some((name, rest)) => (name, rest),
        None => (path, ""),
    };
    if let Some(offset) = name.strip_prefix("now") {
        return resolve_now(offset);
    }
    let mut current = captures.get(name)?;
    if !rest.is_empty() {
        for segment in rest.split('.') {
            current = match current {
                Value::Object(object) => object.get(segment)?,
                Value::Array(array) => array.get(segment.parse::<usize>().ok()?)?,
                _ => return None,
            };
        }
    }
    Some(current.clone())
}

/// Resuelve `{{now}}` (ISO-8601 UTC actual) y variantes con desplazamiento
/// en segundos: `{{now+60}}`, `{{now-300}}`. Útil para escenas que dependen
/// del tiempo real (p.ej. el barrido del motor que detecta permanencias).
fn resolve_now(offset: &str) -> Option<Value> {
    use chrono::{Duration, SecondsFormat, Utc};
    let base = Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true);
    let shifted = if offset.is_empty() {
        base
    } else {
        let (sign, seconds): (i64, i64) = if let Some(rest) = offset.strip_prefix('+') {
            (1, rest.parse().ok()?)
        } else if let Some(rest) = offset.strip_prefix('-') {
            (-1, rest.parse().ok()?)
        } else {
            return None;
        };
        let parsed = chrono::DateTime::parse_from_rfc3339(&base).ok()?;
        (parsed + Duration::seconds(sign * seconds))
            .with_timezone(&Utc)
            .to_rfc3339_opts(SecondsFormat::Millis, true)
    };
    Some(Value::String(shifted))
}

fn command_error(command: &SceneCommand, message: impl Into<String>) -> SceneError {
    SceneError::Command {
        command: command.label().to_owned(),
        message: message.into(),
    }
}

fn api_command_error(command: &SceneCommand, error: ManaError) -> SceneError {
    command_error(command, error.to_string())
}

fn report_response(action: &str, mut response: Value) -> Value {
    if action == "login" {
        if let Value::Object(object) = &mut response {
            if object.contains_key("token") {
                object.insert("token".to_owned(), Value::String("[redacted]".to_owned()));
            }
        }
    }
    response
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_identity_smoke_commands_and_dotted_assertions() {
        let scene = Scene::from_json(
            r#"
            {
              "meta": {"id": "smoke"},
              "contexto": "identidad",
              "commands": [
                {
                  "name": "login",
                  "action": "identidad.login",
                  "args": {
                    "username": "gaston",
                    "password_env": "DEMO_GASTON_PASSWORD",
                    "password_default": "gaston-demo"
                  },
                  "assert": {"user.username": "gaston"}
                },
                {"action": "me", "assert": {"user.role": "supervisor"}},
                {"action": "users.list", "assert": {"users.length": 2}},
                {"action": "logout", "status": 204}
              ]
            }
            "#,
        )
        .unwrap();

        scene.validate().unwrap();
        assert_eq!(scene.commands.len(), 4);
    }

    #[test]
    fn reads_array_length_and_indexes_in_assertions() {
        let body = serde_json::json!({"users": [{"username": "gaston"}]});
        assert_eq!(value_at(Some(&body), "users.length"), Some(Value::from(1)));
        assert_eq!(
            value_at(Some(&body), "users.0.username"),
            Some(Value::from("gaston"))
        );
    }

    #[test]
    fn redacts_tokens_from_scene_reports() {
        let response = report_response(
            "login",
            serde_json::json!({"token": "secret", "user": {"username": "gaston"}}),
        );
        assert_eq!(response["token"], "[redacted]");
        assert_eq!(response["user"]["username"], "gaston");
    }

    #[test]
    fn resolves_captures_with_typed_and_embedded_references() {
        let captures = HashMap::from([
            (
                "facility".to_owned(),
                serde_json::json!({"id": "facility-1"}),
            ),
            (
                "wing".to_owned(),
                serde_json::json!({"id": "wing-9", "sort_order": 2}),
            ),
        ]);

        let args = serde_json::json!({
            "facility_id": "{{ facility.id }}",
            "name": "Ala {{ wing.id }}",
            "sort_order": "{{ wing.sort_order }}",
            "literal": "texto sin referencias"
        });
        let resolved = resolve_args(&captures, args.as_object().unwrap()).unwrap();

        assert_eq!(resolved["facility_id"], "facility-1");
        assert_eq!(resolved["name"], "Ala wing-9");
        assert_eq!(resolved["sort_order"], Value::from(2));
        assert_eq!(resolved["literal"], "texto sin referencias");
    }

    #[test]
    fn rejects_unresolved_captures() {
        let captures = HashMap::new();
        let resolved = resolve_string(&captures, "{{ room.id }}");
        assert!(resolved.is_err());

        let resolved = resolve_string(&captures, "prefix-{{ room.id }}");
        assert!(resolved.is_err());
    }

    #[test]
    fn validates_the_residencia_blueprint_scene() {
        let path = concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/scenes/residencia-blueprint.json"
        );
        let scene = Scene::from_file(path).unwrap();
        scene.validate().unwrap();
        assert_eq!(scene.meta.id, "residencia-blueprint");
        assert!(!scene.commands.is_empty());
        assert!(scene
            .commands
            .iter()
            .any(|command| command.capture.is_some()));
    }

    #[test]
    fn validates_the_alarm_motor_blueprint_scene() {
        let path = concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/scenes/motores-alarmas-blueprint.json"
        );
        let scene = Scene::from_file(path).unwrap();
        scene.validate().unwrap();
        assert_eq!(scene.meta.id, "motores-alarmas-blueprint");
        assert!(scene
            .commands
            .iter()
            .any(|command| command.name.contains("crea alerta")));
    }
}
