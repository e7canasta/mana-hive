//! Motores puros del Registro.
//!
//! Este crate no puede consultar bases, stores ni bounded contexts. Recibe toda
//! la evidencia ya hidratada y devuelve una destilacion que `mana-app` decide
//! persistir. La frontera se hace cumplir por Cargo, no por convencion.

pub mod alarmas;
pub mod autopilot;
pub mod catalogo;
pub mod recomendacion;

pub use alarmas::{
    evaluar, reglas_pendientes, Accion, AlertaNueva, Clase, Contexto, Disparo, EstadoObservado,
    NivelAlerta, PerfilEfectivo, ReglaEfectiva, Sensibilidad, TipoEvidencia, Turno,
    REGLAS_OPERATIVAS,
};
pub use autopilot::{
    decidir, AutopilotAction, AutopilotDecision, AutopilotInput, AutopilotPolicy, AutopilotProfile,
    AutopilotReason,
};
pub use catalogo::{
    perfil_efectivo, Action, AlarmCatalog, AlarmRule, CatalogError, Class, MobilityAid, ParamDef,
    ParamOption, ParamType, PresetRule, ResolvedRule, RiskFactor, RiskLevel, RuleGroup, RuleSource,
    SensitivityCalibration, Shift, ShiftHours, Template, TemplateRule,
};
pub use recomendacion::{
    plantilla_sugerida, recomendar, Banda, Direccion, FactorDeRiesgo, PoliticaDeRecomendacion,
    Recomendacion, ReglaDeRiesgo, Senales,
};

#[cfg(test)]
mod alarmas_tests;
