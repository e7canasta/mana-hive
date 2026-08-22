use std::collections::BTreeMap;

use mana_sdk::ManaClient;
use thiserror::Error;

use crate::session;

#[derive(Debug, Error)]
pub enum CliError {
    #[error("{0}")]
    Usage(String),
    #[error(transparent)]
    Sdk(#[from] mana_sdk::ManaError),
    #[error(transparent)]
    Scene(#[from] mana_sdk::SceneError),
    #[error("no se pudo serializar la salida JSON: {0}")]
    Output(#[from] serde_json::Error),
    #[error("no se pudo leer el token de sesion: {0}")]
    Session(#[from] session::SessionError),
}

pub struct CommandSpec {
    pub verb: &'static str,
    pub description: &'static str,
    pub options: &'static [&'static str],
}

pub const COMMANDS: &[(&str, &[CommandSpec])] = &[
    (
        "api",
        &[CommandSpec {
            verb: "health",
            description: "estado del hub",
            options: &["base-url"],
        }],
    ),
    (
        "identidad",
        &[
            CommandSpec {
                verb: "login",
                description: "inicia sesion y guarda el token",
                options: &["base-url", "username", "password", "no-store"],
            },
            CommandSpec {
                verb: "logout",
                description: "cierra sesion y borra el token guardado",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "me",
                description: "usuario de la sesion actual",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "usuarios",
                description: "listado de usuarios",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "create-user",
                description: "crear usuario",
                options: &[
                    "base-url",
                    "token",
                    "username",
                    "display-name",
                    "role",
                    "job-title",
                    "password",
                ],
            },
            CommandSpec {
                verb: "update-user",
                description: "actualizar usuario",
                options: &[
                    "base-url",
                    "token",
                    "user-id",
                    "display-name",
                    "role",
                    "job-title",
                    "active",
                    "password",
                ],
            },
        ],
    ),
    (
        "auditoria",
        &[CommandSpec {
            verb: "log",
            description: "entradas de auditoria",
            options: &[
                "base-url",
                "token",
                "limit",
                "action",
                "entity-type",
                "entity-id",
            ],
        }],
    ),
    (
        "residencia",
        &[
            CommandSpec {
                verb: "facilities",
                description: "listado de facilities",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "facility",
                description: "detalle de una facility",
                options: &["base-url", "token", "facility-id"],
            },
            CommandSpec {
                verb: "tree",
                description: "arbol completo de una facility (wings→rooms→beds)",
                options: &["base-url", "token", "facility-id"],
            },
            CommandSpec {
                verb: "wings",
                description: "listado de wings",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "rooms",
                description: "habitaciones de un wing",
                options: &["base-url", "token", "wing-id"],
            },
            CommandSpec {
                verb: "beds",
                description: "camas de una habitacion",
                options: &["base-url", "token", "room-id"],
            },
            CommandSpec {
                verb: "camas",
                description: "read model global de camas",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "planograma",
                description: "planograma de un wing como grid",
                options: &["base-url", "token", "wing-id", "json"],
            },
            CommandSpec {
                verb: "privacidad",
                description: "regiones de privacidad de una habitacion",
                options: &["base-url", "token", "room-id"],
            },
            CommandSpec {
                verb: "create-facility",
                description: "crear facility",
                options: &["base-url", "token", "name", "timezone"],
            },
            CommandSpec {
                verb: "update-facility",
                description: "actualizar facility",
                options: &["base-url", "token", "facility-id", "name", "timezone"],
            },
            CommandSpec {
                verb: "create-wing",
                description: "crear ala de una facility",
                options: &["base-url", "token", "facility-id", "name", "floor", "sort-order"],
            },
            CommandSpec {
                verb: "update-wing",
                description: "actualizar ala",
                options: &["base-url", "token", "wing-id", "name", "floor", "sort-order"],
            },
            CommandSpec {
                verb: "create-room",
                description: "crear habitacion de un wing",
                options: &["base-url", "token", "wing-id", "number", "type", "stream-key"],
            },
            CommandSpec {
                verb: "update-room",
                description: "actualizar habitacion",
                options: &["base-url", "token", "room-id", "number", "type", "stream-key"],
            },
            CommandSpec {
                verb: "create-bed",
                description: "crear cama de una habitacion",
                options: &["base-url", "token", "room-id", "label", "monitor-key"],
            },
            CommandSpec {
                verb: "update-bed",
                description: "actualizar cama",
                options: &["base-url", "token", "bed-id", "label", "monitor-key"],
            },
            CommandSpec {
                verb: "save-planogram",
                description: "guardar planograma de un wing (body=JSON)",
                options: &["base-url", "token", "wing-id", "body"],
            },
            CommandSpec {
                verb: "save-privacy",
                description: "guardar regiones de privacidad (body=JSON)",
                options: &["base-url", "token", "room-id", "body"],
            },
        ],
    ),
    (
        "poblacion",
        &[
            CommandSpec {
                verb: "residentes",
                description: "padron de residentes",
                options: &["base-url", "token", "q"],
            },
            CommandSpec {
                verb: "residente",
                description: "detalle de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "alta",
                description: "alta de un residente",
                options: &[
                    "base-url",
                    "token",
                    "full-name",
                    "external-id",
                    "birth-date",
                    "admission-date",
                ],
            },
            CommandSpec {
                verb: "asignar",
                description: "asignar cama a residente",
                options: &["base-url", "token", "resident-id", "bed-id"],
            },
            CommandSpec {
                verb: "liberar",
                description: "liberar cama de su asignacion",
                options: &["base-url", "token", "bed-id"],
            },
            CommandSpec {
                verb: "egreso",
                description: "egresar residente",
                options: &["base-url", "token", "resident-id", "discharged-at"],
            },
            CommandSpec {
                verb: "assignments",
                description: "asignaciones de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "update",
                description: "actualizar residente",
                options: &[
                    "base-url",
                    "token",
                    "resident-id",
                    "full-name",
                    "external-id",
                    "birth-date",
                    "admission-date",
                ],
            },
        ],
    ),
    (
        "cobertura",
        &[
            CommandSpec {
                verb: "grilla",
                description: "grilla de turnos de una facility",
                options: &["base-url", "token", "facility-id"],
            },
            CommandSpec {
                verb: "reemplazar-grilla",
                description: "reemplazar grilla de turnos",
                options: &["base-url", "token", "facility-id", "shifts"],
            },
            CommandSpec {
                verb: "grupos",
                description: "grupos de staff de una facility",
                options: &["base-url", "token", "facility-id"],
            },
            CommandSpec {
                verb: "grupo",
                description: "detalle de un grupo con miembros",
                options: &["base-url", "token", "group-id"],
            },
            CommandSpec {
                verb: "crear-grupo",
                description: "crear grupo de staff",
                options: &["base-url", "token", "facility-id", "name"],
            },
            CommandSpec {
                verb: "miembros",
                description: "reemplazar miembros de un grupo",
                options: &["base-url", "token", "group-id", "users"],
            },
            CommandSpec {
                verb: "cobertura",
                description: "cobertura actual de un ala",
                options: &["base-url", "token", "wing-id", "at"],
            },
            CommandSpec {
                verb: "asignar-cobertura",
                description: "asignar grupo a ala y turno",
                options: &["base-url", "token", "wing-id", "shift-key", "group-id"],
            },
            CommandSpec {
                verb: "update-group",
                description: "actualizar grupo de staff",
                options: &["base-url", "token", "group-id", "name"],
            },
            CommandSpec {
                verb: "clear-coverage",
                description: "quitar cobertura de un ala y turno",
                options: &["base-url", "token", "wing-id", "shift-key"],
            },
        ],
    ),
    (
        "cuidado",
        &[
            CommandSpec {
                verb: "ronda-actual",
                description: "ronda en progreso de un ala",
                options: &["base-url", "token", "wing-id"],
            },
            CommandSpec {
                verb: "rondas",
                description: "historial de rondas de un ala",
                options: &["base-url", "token", "wing-id", "limit"],
            },
            CommandSpec {
                verb: "crear-ronda",
                description: "crear ronda para un ala",
                options: &["base-url", "token", "wing-id"],
            },
            CommandSpec {
                verb: "completar-ronda",
                description: "completar una ronda",
                options: &["base-url", "token", "round-id"],
            },
            CommandSpec {
                verb: "tarea",
                description: "actualizar tarea de ronda",
                options: &["base-url", "token", "task-id", "status", "note"],
            },
            CommandSpec {
                verb: "notas",
                description: "notas de cuidado de un residente",
                options: &["base-url", "token", "resident-id", "limit"],
            },
            CommandSpec {
                verb: "nota",
                description: "crear nota de cuidado",
                options: &[
                    "base-url",
                    "token",
                    "resident-id",
                    "body",
                    "kind",
                    "duration-min",
                ],
            },
            CommandSpec {
                verb: "cancel-round",
                description: "cancelar una ronda en progreso",
                options: &["base-url", "token", "round-id"],
            },
        ],
    ),
    (
        "observacion",
        &[
            CommandSpec {
                verb: "board",
                description: "board de un ala con estado y frescura por cama",
                options: &["base-url", "token", "wing-id"],
            },
            CommandSpec {
                verb: "estado",
                description: "estado actual proyectado de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "eventos",
                description: "ultimos eventos del detector para un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "timeline",
                description: "sueno, movilidad y bano de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "sueno",
                description: "resumenes diarios de sueno",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "movilidad",
                description: "resumenes diarios de movilidad",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "bano",
                description: "resumenes diarios de bano",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "habitaciones",
                description: "habitaciones con stream y ocupantes",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "mirar",
                description: "autorizar mirada al stream de una habitacion",
                options: &["base-url", "token", "room-id"],
            },
            CommandSpec {
                verb: "reporte",
                description: "resumen de residencia: camas, ocupacion y observacion",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "ingerir",
                description: "ingerir un evento del detector (usa secreto, no sesion)",
                options: &[
                    "base-url",
                    "secret",
                    "source-event-id",
                    "monitor-key",
                    "kind",
                    "state",
                    "occurred-at",
                ],
            },
            CommandSpec {
                verb: "ingest-sleep",
                description: "ingerir resumen de sueno (body=JSON, usa secreto)",
                options: &["base-url", "secret", "body"],
            },
            CommandSpec {
                verb: "ingest-mobility",
                description: "ingerir resumen de movilidad (body=JSON, usa secreto)",
                options: &["base-url", "secret", "body"],
            },
            CommandSpec {
                verb: "ingest-bathroom",
                description: "ingerir resumen de bano (body=JSON, usa secreto)",
                options: &["base-url", "secret", "body"],
            },
        ],
    ),
    (
        "historia",
        &[
            CommandSpec {
                verb: "incidentes",
                description: "incidentes de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "incidente",
                description: "detalle de un incidente con revisiones",
                options: &["base-url", "token", "incident-id"],
            },
            CommandSpec {
                verb: "revisar",
                description: "crear revision de un incidente",
                options: &[
                    "base-url",
                    "token",
                    "incident-id",
                    "status",
                    "verdict",
                    "note",
                ],
            },
            CommandSpec {
                verb: "ingest-incident",
                description: "ingerir deteccion de incidente",
                options: &[
                    "base-url",
                    "token",
                    "source-record-id",
                    "resident-id",
                    "bed-id",
                    "source-alert-id",
                    "kind",
                    "severity",
                    "occurred-at",
                    "location",
                    "activity",
                    "injury-status",
                    "self-recovery",
                    "response-seconds",
                    "narrative",
                    "interventions-json",
                    "source",
                    "model-version",
                    "confidence",
                    "provenance-json",
                ],
            },
        ],
    ),
    (
        "politica",
        &[
            CommandSpec {
                verb: "catalogo",
                description: "catalogo de alarmas",
                options: &["base-url"],
            },
            CommandSpec {
                verb: "presets",
                description: "buscar presets de alarmas",
                options: &["base-url", "token", "q"],
            },
            CommandSpec {
                verb: "perfil",
                description: "perfil de alarmas de un residente",
                options: &["base-url", "token", "resident-id", "at"],
            },
            CommandSpec {
                verb: "historial",
                description: "historial de perfiles de un residente",
                options: &["base-url", "token", "resident-id"],
            },
            CommandSpec {
                verb: "actualizar",
                description: "actualizar perfil de alarmas",
                options: &[
                    "base-url",
                    "token",
                    "resident-id",
                    "body",
                    "mobility-aid",
                    "autopilot",
                    "mode",
                    "template-id",
                    "overrides",
                ],
            },
            CommandSpec {
                verb: "autopilot",
                description: "ejecutar autopilot para residentes activos",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "apply-recommendation",
                description: "aplicar una recomendacion de perfil",
                options: &[
                    "base-url",
                    "token",
                    "resident-id",
                    "template-id",
                    "overrides-json",
                    "catalog-version",
                ],
            },
        ],
    ),
    (
        "vigilancia",
        &[
            CommandSpec {
                verb: "listar",
                description: "listar alertas",
                options: &["base-url", "token"],
            },
            CommandSpec {
                verb: "crear",
                description: "crear alerta",
                options: &[
                    "base-url",
                    "token",
                    "bed-id",
                    "rule-id",
                    "level",
                    "title",
                    "evidence-kind",
                    "resident-id",
                    "detail",
                    "occurred-at",
                ],
            },
            CommandSpec {
                verb: "detalle",
                description: "detalle de una alerta",
                options: &["base-url", "token", "alert-id"],
            },
            CommandSpec {
                verb: "transicion",
                description: "transicionar alerta",
                options: &["base-url", "token", "alert-id", "to-status", "actor-id"],
            },
            CommandSpec {
                verb: "entregas",
                description: "entregas de notificacion de una alerta",
                options: &["base-url", "token", "alert-id"],
            },
            CommandSpec {
                verb: "view",
                description: "marcar alerta como vista",
                options: &["base-url", "token", "alert-id"],
            },
            CommandSpec {
                verb: "create-delivery",
                description: "crear entrega de notificacion",
                options: &[
                    "base-url",
                    "token",
                    "alert-id",
                    "recipient-kind",
                    "recipient-id",
                    "channel",
                    "escalation-level",
                ],
            },
            CommandSpec {
                verb: "delivery-event",
                description: "registrar evento de una entrega",
                options: &["base-url", "token", "delivery-id", "kind", "reason"],
            },
        ],
    ),
    (
        "streams",
        &[
            CommandSpec {
                verb: "list",
                description: "listar streams de una room",
                options: &["base-url", "token", "room-id"],
            },
            CommandSpec {
                verb: "create",
                description: "registrar stream/camara",
                options: &["base-url", "token", "room-id", "stream-key", "name"],
            },
            CommandSpec {
                verb: "get",
                description: "detalle de un stream",
                options: &["base-url", "token", "stream-id"],
            },
            CommandSpec {
                verb: "regions",
                description: "listar regiones de un stream",
                options: &["base-url", "token", "stream-id"],
            },
            CommandSpec {
                verb: "set-regions",
                description: "reemplazar regiones de un stream",
                options: &["base-url", "token", "stream-id", "body"],
            },
            CommandSpec {
                verb: "update-region",
                description: "actualizar puntos de una region",
                options: &["base-url", "token", "stream-id", "region-id", "points"],
            },
        ],
    ),
    (
        "scene",
        &[
            CommandSpec {
                verb: "validate",
                description: "valida una escena",
                options: &["file"],
            },
            CommandSpec {
                verb: "load",
                description: "ejecuta una escena",
                options: &["file", "base-url"],
            },
        ],
    ),
];

#[derive(Clone, Default)]
pub struct Options {
    pub verb: String,
    values: BTreeMap<String, String>,
}

impl Options {
    pub fn parse(arguments: &[String], specs: &[(&str, &[CommandSpec])]) -> Result<Self, CliError> {
        let mut parsed = BTreeMap::new();
        let mut index = 0;
        while index < arguments.len() {
            let argument = &arguments[index];
            let Some(raw) = argument.strip_prefix("--") else {
                return Err(CliError::Usage(format!(
                    "argumento invalido: {argument}\n\n{}",
                    usage()
                )));
            };
            let (key, value) = match raw.split_once('=') {
                Some((key, value)) => (key.to_owned(), value.to_owned()),
                None => {
                    index += 1;
                    let Some(value) = arguments.get(index) else {
                        return Err(CliError::Usage(format!(
                            "falta valor para --{raw}\n\n{}",
                            usage()
                        )));
                    };
                    if value.starts_with("--") {
                        return Err(CliError::Usage(format!(
                            "falta valor para --{raw}\n\n{}",
                            usage()
                        )));
                    }
                    (raw.to_owned(), value.clone())
                }
            };
            let allowed = specs
                .iter()
                .flat_map(|(_, commands)| commands.iter())
                .flat_map(|command| command.options.iter())
                .chain(std::iter::once(&"base-url"))
                .chain(std::iter::once(&"json"))
                .chain(std::iter::once(&"format"))
                .any(|allowed| *allowed == key);
            if !allowed {
                return Err(CliError::Usage(format!(
                    "opcion no permitida: --{key}\n\n{}",
                    usage()
                )));
            }
            if parsed.insert(key.clone(), value).is_some() {
                return Err(CliError::Usage(format!("opcion repetida: --{key}")));
            }
            index += 1;
        }
        Ok(Self {
            verb: String::new(),
            values: parsed,
        })
    }

    #[must_use]
    pub fn with_verb(mut self, verb: &str) -> Self {
        self.verb = verb.to_owned();
        self
    }

    pub fn verb(&self) -> &str {
        &self.verb
    }

    pub fn get(&self, name: &str) -> Option<&str> {
        self.values
            .get(name)
            .map(String::as_str)
            .filter(|value| !value.is_empty())
    }

    pub fn required(&self, name: &str) -> Result<&str, CliError> {
        self.get(name)
            .ok_or_else(|| CliError::Usage(format!("falta --{name}\n\n{}", usage())))
    }

    pub fn has(&self, name: &str) -> bool {
        self.values.contains_key(name)
    }

    pub fn format(&self) -> &str {
        self.get("format").unwrap_or("pretty")
    }

    pub fn client(&self) -> Result<ManaClient, CliError> {
        match self.get("base-url") {
            Some(base_url) => Ok(ManaClient::new(base_url)?),
            None => Ok(ManaClient::from_env()?),
        }
    }

    pub fn token(&self) -> Result<Option<String>, CliError> {
        if let Some(token) = self.get("token") {
            return Ok(Some(token.to_owned()));
        }
        if let Ok(token) = std::env::var("MANA_API_TOKEN") {
            if !token.trim().is_empty() {
                return Ok(Some(token));
            }
        }
        Ok(session::read_token()?)
    }
}

pub fn usage() -> String {
    let mut lines = vec![
        "Uso: mana <dominio> <verbo> [--opcion=valor...]".to_owned(),
        String::new(),
    ];
    for (domain, commands) in COMMANDS {
        lines.push(domain.to_string());
        for command in *commands {
            lines.push(format!(
                "  {domain} {:<12} {}",
                command.verb, command.description
            ));
        }
        lines.push(String::new());
    }
    lines.push("Opciones comunes: --base-url=URL --token=TOKEN --format=pretty|json".to_owned());
    lines.join("\n")
}
