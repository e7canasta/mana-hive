//! Decide si una recomendacion puede cambiar un perfil sin confirmacion humana.
//!
//! Autopilot no vuelve a calcular el riesgo. Recibe la recomendacion ya
//! explicada y aplica una politica de seguridad asimetrica: una subida puede
//! aplicarse con evidencia suficiente, una bajada solo se propone y una
//! recomendacion sin evidencia se descarta.

use chrono::Duration;
use mana_kernel::Instante;
use serde::Deserialize;

use crate::{Recomendacion, RiskLevel};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AutopilotAction {
    Keep,
    Apply,
    Propose,
    Skip,
}

impl AutopilotAction {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Keep => "keep",
            Self::Apply => "apply",
            Self::Propose => "propose",
            Self::Skip => "skip",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AutopilotReason {
    Disabled,
    AlreadyAtRecommendation,
    InsufficientEvidence,
    Cooldown,
    IncreaseAllowed,
    DecreaseRequiresConfirmation,
}

impl AutopilotReason {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Disabled => "disabled",
            Self::AlreadyAtRecommendation => "already_at_recommendation",
            Self::InsufficientEvidence => "insufficient_evidence",
            Self::Cooldown => "cooldown",
            Self::IncreaseAllowed => "increase_allowed",
            Self::DecreaseRequiresConfirmation => "decrease_requires_confirmation",
        }
    }
}

/// Politica de aplicacion. Los cortes de la recomendacion viven en otra
/// politica: este dato solo responde cuando autopilot puede actuar solo.
#[derive(Debug, Clone, Deserialize)]
pub struct AutopilotPolicy {
    /// Cantidad minima de reglas con evidencia observada para aceptar un
    /// cambio. Tambien evita proponer una bajada basada en una ventana vacia.
    #[serde(default = "minimum_signals_for_raise_default")]
    pub minimum_signals_for_raise: i32,
    /// Tiempo minimo entre versiones de perfil, en dias.
    #[serde(default = "minimum_days_between_changes_default")]
    pub minimum_days_between_changes: i64,
}

fn minimum_signals_for_raise_default() -> i32 {
    1
}

fn minimum_days_between_changes_default() -> i64 {
    7
}

impl Default for AutopilotPolicy {
    fn default() -> Self {
        Self {
            minimum_signals_for_raise: minimum_signals_for_raise_default(),
            minimum_days_between_changes: minimum_days_between_changes_default(),
        }
    }
}

/// Parte del perfil que autopilot necesita para tomar la decision.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AutopilotProfile {
    pub risk_level: RiskLevel,
    pub autopilot: bool,
}

#[derive(Debug, Clone)]
pub struct AutopilotInput {
    pub current_profile: AutopilotProfile,
    pub recommendation: Recomendacion,
    pub last_change_at: Option<Instante>,
    pub now: Instante,
}

/// Decision reconstruible y apta para auditoria. Los factores siguen en la
/// recomendacion de entrada; aca quedan los datos que explican la accion.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AutopilotDecision {
    pub action: AutopilotAction,
    pub reason: AutopilotReason,
    pub current_level: RiskLevel,
    pub recommended_level: RiskLevel,
    pub score: i32,
    pub signals_evaluated: i32,
}

/// Decide si autopilot puede aplicar la recomendacion recibida.
pub fn decidir(input: &AutopilotInput, policy: &AutopilotPolicy) -> AutopilotDecision {
    let current_level = input.current_profile.risk_level;
    let recommendation = &input.recommendation;
    let base = |action, reason| AutopilotDecision {
        action,
        reason,
        current_level,
        recommended_level: recommendation.level,
        score: recommendation.score,
        signals_evaluated: recommendation.signals_evaluated,
    };

    if !input.current_profile.autopilot {
        return base(AutopilotAction::Skip, AutopilotReason::Disabled);
    }

    // Estar ya en el nivel recomendado no necesita evidencia ni crea otra
    // version. Es un no-op explicito, no una aplicacion silenciosa.
    if current_level == recommendation.level {
        return base(
            AutopilotAction::Keep,
            AutopilotReason::AlreadyAtRecommendation,
        );
    }

    let minimum_signals = policy.minimum_signals_for_raise.max(0);
    if recommendation.signals_evaluated.max(0) < minimum_signals {
        return base(AutopilotAction::Skip, AutopilotReason::InsufficientEvidence);
    }

    if cooldown_active(input, policy) {
        return base(AutopilotAction::Keep, AutopilotReason::Cooldown);
    }

    if level_rank(recommendation.level) > level_rank(current_level) {
        base(AutopilotAction::Apply, AutopilotReason::IncreaseAllowed)
    } else {
        base(
            AutopilotAction::Propose,
            AutopilotReason::DecreaseRequiresConfirmation,
        )
    }
}

fn cooldown_active(input: &AutopilotInput, policy: &AutopilotPolicy) -> bool {
    let days = policy.minimum_days_between_changes.max(0);
    days > 0
        && input.last_change_at.is_some_and(|last| {
            input
                .now
                .as_datetime()
                .signed_duration_since(last.into_datetime())
                < Duration::days(days)
        })
}

fn level_rank(level: RiskLevel) -> u8 {
    match level {
        RiskLevel::Low => 0,
        RiskLevel::Medium => 1,
        RiskLevel::High => 2,
    }
}

#[cfg(test)]
mod tests;
