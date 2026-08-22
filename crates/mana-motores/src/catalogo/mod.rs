//! El catalogo de alarmas y la resolucion del perfil efectivo.
//!
//! El catalogo **es dato**: vive en `config/alarm-catalog.toml` y cambiar un
//! umbral clinico es un diff de ese archivo, nunca un deploy. Este modulo lo
//! lee y sabe responder la unica pregunta que el motor necesita: **que hace
//! cada regla para este residente, de dia y de noche**.
//!
//! Esa respuesta sale de tres capas, en orden: el preset del nivel de riesgo,
//! la plantilla de perfil y el ajuste manual. Cada valor recuerda que capa lo
//! fijo, porque "por que suena esto" es una pregunta que alguien va a hacer.

use serde::Deserialize;
use serde_json::Value as JsonValue;
use std::collections::{BTreeMap, HashMap};

#[derive(Debug, thiserror::Error)]
pub enum CatalogError {
    #[error("TOML parse error: {0}")]
    Parse(String),
    #[error("unknown rule: {0}")]
    UnknownRule(String),
    #[error("unknown mobility aid: {0}")]
    UnknownMobilityAid(String),
    #[error("unknown risk level: {0}")]
    UnknownRiskLevel(String),
    #[error("invalid override: rule {rule}, param {param}: {reason}")]
    InvalidOverride {
        rule: String,
        param: String,
        reason: String,
    },
    #[error("blocked rule cannot be disabled: {0}")]
    BlockedRule(String),
}

/// Los tres accesorios que el contrato del cliente declara. **No hay baston**:
/// `packages/contracts` acepta `none | walker | wheelchair` y la forma wire la
/// manda el contrato.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MobilityAid {
    None,
    Walker,
    Wheelchair,
}

impl MobilityAid {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::None => "none",
            Self::Walker => "walker",
            Self::Wheelchair => "wheelchair",
        }
    }

    pub fn parse(value: &str) -> Result<Self, CatalogError> {
        match value {
            "none" => Ok(Self::None),
            "walker" => Ok(Self::Walker),
            "wheelchair" => Ok(Self::Wheelchair),
            _ => Err(CatalogError::UnknownMobilityAid(value.to_owned())),
        }
    }
}

/// El nivel de riesgo del residente. Es lo que elige el preset base, y era
/// exactamente el dato que al hub le faltaba para poder evaluar nada.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RiskLevel {
    Low,
    Medium,
    High,
}

impl RiskLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Low => "low",
            Self::Medium => "medium",
            Self::High => "high",
        }
    }

    pub fn parse(value: &str) -> Result<Self, CatalogError> {
        match value {
            "low" => Ok(Self::Low),
            "medium" => Ok(Self::Medium),
            "high" => Ok(Self::High),
            _ => Err(CatalogError::UnknownRiskLevel(value.to_owned())),
        }
    }
}

/// Que hace la regla cuando se cumple.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Action {
    Off,
    Notify,
    Alarm,
}

impl Action {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Off => "off",
            Self::Notify => "notify",
            Self::Alarm => "alarm",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "off" => Some(Self::Off),
            "notify" => Some(Self::Notify),
            "alarm" => Some(Self::Alarm),
            _ => None,
        }
    }
}

/// Los dos momentos del dia del residente. **No** son turnos laborales: la
/// grilla de turnos es de la residencia y vive en `ctx-cobertura`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Shift {
    Day,
    Night,
}

impl Shift {
    pub const ALL: [Shift; 2] = [Shift::Day, Shift::Night];

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Day => "day",
            Self::Night => "night",
        }
    }
}

/// Que dispara la regla. La clase no se declara aparte: la lleva el
/// temporizador de la regla, que es donde ya vivia.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Class {
    /// Se dispara por el cambio de estado; su temporizador es de confirmacion.
    Transition,
    /// Se dispara por el paso del tiempo; su temporizador es de tolerancia.
    Dwell,
    /// Sin temporizador: no la dispara ni el evento ni el reloj.
    #[default]
    None,
}

impl Class {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Transition => "transition",
            Self::Dwell => "dwell",
            Self::None => "none",
        }
    }
}

/// De que capa salio un valor. Es lo que permite contestar "por que suena
/// esto" sin releer una auditoria generica.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RuleSource {
    Preset,
    Template,
    Custom,
}

impl RuleSource {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Preset => "preset",
            Self::Template => "template",
            Self::Custom => "custom",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ParamType {
    Number,
    Enum,
    Multi,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ParamOption {
    pub value: String,
    pub label: String,
    #[serde(default)]
    pub detail: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ParamDef {
    pub key: String,
    /// `confirm`, `dwell`, `sensitivity` o `watch`.
    pub kind: String,
    #[serde(rename = "type")]
    pub param_type: ParamType,
    pub label: String,
    #[serde(default)]
    pub detail: Option<String>,
    #[serde(default)]
    pub unit: Option<String>,
    #[serde(default)]
    pub min: Option<f64>,
    #[serde(default)]
    pub max: Option<f64>,
    #[serde(default)]
    pub step: Option<f64>,
    #[serde(default)]
    pub options: Vec<ParamOption>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AlarmRule {
    pub id: String,
    pub group: String,
    pub label: String,
    #[serde(default)]
    pub short_label: Option<String>,
    #[serde(default)]
    pub detail: Option<String>,
    #[serde(default)]
    pub pictogram: Option<String>,
    #[serde(default)]
    pub art: Option<String>,
    /// `fall` es la unica regla bloqueada del catalogo: no se puede apagar en
    /// ninguna capa. Es condicion del dominio, no de la UI.
    #[serde(default)]
    pub locked: bool,
    /// Si la regla solo aplica a residentes con cierto accesorio.
    #[serde(default)]
    pub requires_aid: Option<Vec<MobilityAid>>,
    #[serde(default)]
    pub class: Class,
    #[serde(default)]
    pub params: Vec<ParamDef>,
}

impl AlarmRule {
    /// El parametro que declara el temporizador, si lo tiene.
    pub fn timer_param(&self) -> Option<&ParamDef> {
        self.params
            .iter()
            .find(|param| param.kind == "dwell" || param.kind == "confirm")
    }

    /// La regla aplica a un residente segun su accesorio: una regla de silla de
    /// ruedas no tiene sentido para quien no usa una.
    pub fn available_for(&self, aid: MobilityAid) -> bool {
        match &self.requires_aid {
            None => true,
            Some(aids) => aids.contains(&aid),
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct RuleGroup {
    pub id: String,
    pub label: String,
    #[serde(default)]
    pub detail: Option<String>,
}

/// Una entrada de la matriz de presets: que hace una regla en un nivel.
#[derive(Debug, Clone, Deserialize)]
pub struct PresetRule {
    pub day: Action,
    pub night: Action,
    #[serde(default)]
    pub params: HashMap<String, JsonValue>,
}

/// Una entrada de plantilla. Los turnos son opcionales: una plantilla puede
/// ajustar solo los parametros y dejar las acciones del preset.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct TemplateRule {
    #[serde(default)]
    pub day: Option<Action>,
    #[serde(default)]
    pub night: Option<Action>,
    #[serde(default)]
    pub params: HashMap<String, JsonValue>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Template {
    pub id: String,
    pub label: String,
    #[serde(default)]
    pub detail: Option<String>,
    #[serde(default)]
    pub recommended_for: Vec<String>,
    #[serde(default)]
    pub rules: HashMap<String, TemplateRule>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RiskFactor {
    pub id: String,
    pub label: String,
    #[serde(default)]
    pub icon: Option<String>,
}

#[derive(Debug, Clone, Copy, Deserialize)]
pub struct ShiftHours {
    pub day_start: u32,
    pub night_start: u32,
}

impl Default for ShiftHours {
    fn default() -> Self {
        Self {
            day_start: 7,
            night_start: 19,
        }
    }
}

/// Cuanto se sostiene el estado antes de avisar, segun la sensibilidad. Es
/// calibracion de politica, no mecanismo: el motor solo la aplica.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct SensitivityCalibration {
    #[serde(default)]
    pub factor: HashMap<String, f64>,
    #[serde(default)]
    pub floor_seconds: HashMap<String, i64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AlarmCatalog {
    pub version: String,
    #[serde(default)]
    pub levels: Vec<String>,
    #[serde(default)]
    pub mobility_aids: Vec<String>,
    #[serde(default)]
    pub actions: Vec<String>,
    #[serde(default)]
    pub shifts: Vec<String>,
    #[serde(default)]
    pub modes: Vec<String>,
    #[serde(default)]
    pub sensitivities: Vec<String>,
    #[serde(default)]
    pub shift_hours: ShiftHours,
    #[serde(default)]
    pub sensitivity_calibration: SensitivityCalibration,
    #[serde(default)]
    pub action_level: HashMap<String, String>,
    #[serde(default)]
    pub groups: Vec<RuleGroup>,
    #[serde(default)]
    pub rules: Vec<AlarmRule>,
    /// La matriz: `nivel -> regla -> que hace`.
    #[serde(default)]
    pub presets: HashMap<String, HashMap<String, PresetRule>>,
    #[serde(default)]
    pub templates: Vec<Template>,
    #[serde(default)]
    pub risk_factors: Vec<RiskFactor>,
    /// La politica del motor de recomendacion: ventanas, cortes y pesos.
    /// Node la tenia hardcodeada; aca es dato como el resto del catalogo.
    #[serde(default)]
    pub recomendacion: crate::recomendacion::PoliticaDeRecomendacion,
    /// La politica que decide cuando autopilot puede aplicar una recomendacion.
    #[serde(default)]
    pub autopilot: crate::autopilot::AutopilotPolicy,
}

/// Una regla ya resuelta para un residente: las tres capas aplicadas, con la
/// procedencia de lo que quedo.
#[derive(Debug, Clone, PartialEq)]
pub struct ResolvedRule {
    pub id: String,
    pub group: String,
    pub class: Class,
    pub day: Action,
    pub night: Action,
    pub locked: bool,
    pub source: RuleSource,
    pub customized: bool,
    pub params: BTreeMap<String, JsonValue>,
}

impl ResolvedRule {
    pub fn action_at(&self, shift: Shift) -> Action {
        match shift {
            Shift::Day => self.day,
            Shift::Night => self.night,
        }
    }

    /// El valor del temporizador en minutos, sea de confirmacion o de
    /// tolerancia. Sin temporizador configurado son cero minutos, que **no** es
    /// "sin espera": el piso de sensibilidad lo resuelve el motor.
    pub fn timer_minutes(&self) -> i64 {
        for key in ["dwell_minutes", "delay_minutes"] {
            if let Some(value) = self.params.get(key).and_then(JsonValue::as_f64) {
                return value.round() as i64;
            }
        }
        0
    }

    pub fn sensitivity(&self) -> &str {
        self.params
            .get("sensitivity")
            .and_then(JsonValue::as_str)
            .unwrap_or("standard")
    }
}

impl AlarmCatalog {
    pub fn parse(toml: &str) -> Result<Self, CatalogError> {
        toml::from_str(toml).map_err(|e| CatalogError::Parse(e.to_string()))
    }

    /// Load catalog from file. Uses `MANA_ALARM_CATALOG` or `ALARM_CATALOG_PATH` env var,
    /// falling back to `config/alarm-catalog.toml`.
    pub fn load() -> Result<Self, CatalogError> {
        let path = std::env::var("MANA_ALARM_CATALOG")
            .or_else(|_| std::env::var("ALARM_CATALOG_PATH"))
            .unwrap_or_else(|_| "config/alarm-catalog.toml".to_owned());
        let content = std::fs::read_to_string(&path)
            .map_err(|e| CatalogError::Parse(format!("{path}: {e}")))?;
        Self::parse(&content)
    }

    pub fn empty() -> Self {
        Self {
            version: "empty".to_owned(),
            levels: Vec::new(),
            mobility_aids: Vec::new(),
            actions: Vec::new(),
            shifts: Vec::new(),
            modes: Vec::new(),
            sensitivities: Vec::new(),
            shift_hours: ShiftHours::default(),
            sensitivity_calibration: SensitivityCalibration::default(),
            action_level: HashMap::new(),
            groups: Vec::new(),
            rules: Vec::new(),
            presets: HashMap::new(),
            templates: Vec::new(),
            risk_factors: Vec::new(),
            recomendacion: crate::recomendacion::PoliticaDeRecomendacion::default(),
            autopilot: crate::autopilot::AutopilotPolicy::default(),
        }
    }

    pub fn find_rule(&self, rule_id: &str) -> Option<&AlarmRule> {
        self.rules.iter().find(|r| r.id == rule_id)
    }

    pub fn find_template(&self, template_id: &str) -> Option<&Template> {
        self.templates.iter().find(|t| t.id == template_id)
    }

    pub fn preset_for(&self, level: RiskLevel) -> Option<&HashMap<String, PresetRule>> {
        self.presets.get(level.as_str())
    }

    pub fn search_rules(&self, query: &str) -> Vec<&AlarmRule> {
        let q = query.to_lowercase();
        self.rules
            .iter()
            .filter(|r| {
                r.id.to_lowercase().contains(&q)
                    || r.label.to_lowercase().contains(&q)
                    || r.group.to_lowercase().contains(&q)
                    || r.detail
                        .as_deref()
                        .is_some_and(|d| d.to_lowercase().contains(&q))
            })
            .collect()
    }

    /// Valida un parametro de override contra la definicion de su regla.
    ///
    /// Node descartaba en silencio lo que no validaba. Aca es un error: un
    /// ajuste clinico que el sistema ignora sin decirlo es la falla silenciosa
    /// que esta fase existe para eliminar.
    pub fn validate_override(
        &self,
        rule_id: &str,
        param_key: &str,
        value: &JsonValue,
    ) -> Result<(), CatalogError> {
        let rule = self
            .find_rule(rule_id)
            .ok_or_else(|| CatalogError::UnknownRule(rule_id.to_owned()))?;

        // Los turnos no son parametros: son la accion de la regla.
        if param_key == "day" || param_key == "night" {
            if rule.locked {
                return Err(CatalogError::BlockedRule(rule_id.to_owned()));
            }
            return match value.as_str().and_then(Action::parse) {
                Some(_) => Ok(()),
                None => Err(CatalogError::InvalidOverride {
                    rule: rule_id.to_owned(),
                    param: param_key.to_owned(),
                    reason: "expected one of: off, notify, alarm".to_owned(),
                }),
            };
        }

        if clean_param(rule, param_key, value).is_none() {
            let reason = match rule.params.iter().find(|p| p.key == param_key) {
                None => "unknown parameter".to_owned(),
                Some(def) => match def.param_type {
                    ParamType::Number => format!(
                        "expected a number between {} and {}",
                        def.min.unwrap_or(f64::MIN),
                        def.max.unwrap_or(f64::MAX)
                    ),
                    ParamType::Enum => format!(
                        "expected one of: {}",
                        def.options
                            .iter()
                            .map(|o| o.value.as_str())
                            .collect::<Vec<_>>()
                            .join(", ")
                    ),
                    ParamType::Multi => "expected a non-empty set of known values".to_owned(),
                },
            };
            return Err(CatalogError::InvalidOverride {
                rule: rule_id.to_owned(),
                param: param_key.to_owned(),
                reason,
            });
        }

        Ok(())
    }

    /// Valida el documento de overrides entero, como llega del cliente:
    /// `{ "bed_exit": { "night": "alarm", "delay_minutes": 2 } }`.
    pub fn validate_overrides(&self, overrides: &JsonValue) -> Result<(), CatalogError> {
        let Some(map) = overrides.as_object() else {
            return Ok(());
        };
        for (rule_id, entry) in map {
            let Some(params) = entry.as_object() else {
                continue;
            };
            for (key, value) in params {
                self.validate_override(rule_id, key, value)?;
            }
        }
        Ok(())
    }

    /// Las reglas efectivas de un residente: **preset del nivel, plantilla,
    /// ajuste manual**, en ese orden.
    ///
    /// Es la funcion que le faltaba al hub. Sin ella el catalogo se servia y
    /// nunca se evaluaba: habia politica guardada y ninguna forma de saber que
    /// significaba para una cama concreta.
    pub fn resolve_rules(
        &self,
        level: RiskLevel,
        mobility_aid: MobilityAid,
        custom: bool,
        template_id: Option<&str>,
        overrides: &JsonValue,
    ) -> BTreeMap<String, ResolvedRule> {
        let preset = self.preset_for(level);
        let template = template_id.and_then(|id| self.find_template(id));
        let empty = serde_json::Map::new();
        let overrides = overrides.as_object().unwrap_or(&empty);

        let mut resolved = BTreeMap::new();

        for rule in &self.rules {
            if !rule.available_for(mobility_aid) {
                continue;
            }

            let base = preset.and_then(|p| p.get(&rule.id));
            let mut day = base.map(|b| b.day).unwrap_or(Action::Off);
            let mut night = base.map(|b| b.night).unwrap_or(Action::Off);
            let mut params: BTreeMap<String, JsonValue> = base
                .map(|b| b.params.clone().into_iter().collect())
                .unwrap_or_default();
            let mut source = RuleSource::Preset;

            if let Some(from_template) = template.and_then(|t| t.rules.get(&rule.id)) {
                if !rule.locked {
                    if let Some(action) = from_template.day {
                        day = action;
                        source = RuleSource::Template;
                    }
                    if let Some(action) = from_template.night {
                        night = action;
                        source = RuleSource::Template;
                    }
                }
                for (key, value) in &from_template.params {
                    if let Some(clean) = clean_param(rule, key, value) {
                        params.insert(key.clone(), clean);
                        source = RuleSource::Template;
                    }
                }
            }

            // El ajuste manual solo cuenta en modo `custom`: en modo `preset` el
            // override queda guardado pero no se aplica, que es lo que permite
            // volver al preset sin perder lo que alguien habia configurado.
            if custom {
                if let Some(from_override) = overrides.get(&rule.id).and_then(JsonValue::as_object)
                {
                    if !rule.locked {
                        if let Some(action) = from_override
                            .get("day")
                            .and_then(|v| v.as_str())
                            .and_then(Action::parse)
                        {
                            day = action;
                            source = RuleSource::Custom;
                        }
                        if let Some(action) = from_override
                            .get("night")
                            .and_then(|v| v.as_str())
                            .and_then(Action::parse)
                        {
                            night = action;
                            source = RuleSource::Custom;
                        }
                    }
                    for (key, value) in from_override {
                        if key == "day" || key == "night" {
                            continue;
                        }
                        if let Some(clean) = clean_param(rule, key, value) {
                            params.insert(key.clone(), clean);
                            source = RuleSource::Custom;
                        }
                    }
                }
            }

            resolved.insert(
                rule.id.clone(),
                ResolvedRule {
                    id: rule.id.clone(),
                    group: rule.group.clone(),
                    class: rule.class,
                    day,
                    night,
                    locked: rule.locked,
                    source,
                    customized: source == RuleSource::Custom,
                    params,
                },
            );
        }

        resolved
    }
}

/// Un valor de parametro, validado contra su definicion. `None` es "no vale",
/// y quien llama decide si lo ignora o lo denuncia.
fn clean_param(rule: &AlarmRule, key: &str, value: &JsonValue) -> Option<JsonValue> {
    let def = rule.params.iter().find(|p| p.key == key)?;
    match def.param_type {
        ParamType::Enum => {
            let text = value.as_str()?;
            def.options
                .iter()
                .any(|o| o.value == text)
                .then(|| value.clone())
        }
        ParamType::Multi => {
            // Un conjunto vacio no es una configuracion valida: seria una regla
            // encendida que no puede disparar nunca. Para dejar de vigilar esta
            // la accion "off", que ademas queda en el registro de cambios.
            let items = value.as_array()?;
            if items.is_empty() {
                return None;
            }
            let known: Vec<&str> = items.iter().filter_map(JsonValue::as_str).collect();
            if known.len() != items.len() {
                return None;
            }
            known
                .iter()
                .all(|item| def.options.iter().any(|o| o.value == *item))
                .then(|| value.clone())
        }
        ParamType::Number => {
            let amount = value.as_f64()?;
            if def.min.is_some_and(|min| amount < min) || def.max.is_some_and(|max| amount > max) {
                return None;
            }
            Some(value.clone())
        }
    }
}

/// El perfil efectivo tal como lo consume el motor, resuelto contra el catalogo.
///
/// Toma primitivos para no depender de `ctx-politica`. El caller es responsable
/// de extraer estos campos de `AlarmProfileVersion` o de usar defaults.
pub fn perfil_efectivo(
    catalog: &AlarmCatalog,
    level: RiskLevel,
    aid: MobilityAid,
    is_custom: bool,
    template_id: &str,
    overrides: &JsonValue,
) -> crate::alarmas::PerfilEfectivo {
    let reglas = catalog.resolve_rules(level, aid, is_custom, Some(template_id), overrides);
    let reglas = reglas
        .into_iter()
        .filter_map(|(id, rule)| regla_para_el_motor(&rule).map(|r| (id, r)))
        .collect();
    crate::alarmas::PerfilEfectivo { reglas }
}

/// Traduce una regla resuelta a lo que el motor evalua.
fn regla_para_el_motor(rule: &ResolvedRule) -> Option<crate::alarmas::ReglaEfectiva> {
    let clase = match rule.class {
        Class::Transition => crate::alarmas::Clase::Transicion,
        Class::Dwell => crate::alarmas::Clase::Permanencia,
        Class::None => return None,
    };
    Some(crate::alarmas::ReglaEfectiva {
        id: rule.id.clone(),
        clase,
        dia: accion_para_el_motor(rule.day),
        noche: accion_para_el_motor(rule.night),
        minutos: rule.timer_minutes(),
        sensibilidad: crate::alarmas::Sensibilidad::parse(rule.sensitivity())
            .unwrap_or(crate::alarmas::Sensibilidad::Estandar),
        bloqueada: rule.locked,
        etiqueta: rule.id.clone(),
    })
}

fn accion_para_el_motor(action: Action) -> crate::alarmas::Accion {
    match action {
        Action::Off => crate::alarmas::Accion::Off,
        Action::Notify => crate::alarmas::Accion::Notify,
        Action::Alarm => crate::alarmas::Accion::Alarm,
    }
}

#[cfg(test)]
mod tests;
