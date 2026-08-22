use super::*;
use crate::AlarmCatalog;

fn instant(value: &str) -> Instante {
    value.parse().expect("instante valido")
}

fn recommendation(level: RiskLevel, signals_evaluated: i32) -> Recomendacion {
    Recomendacion {
        level,
        score: 6,
        factors: Vec::new(),
        signals_evaluated,
        suggested_template: "balanced".to_owned(),
    }
}

fn input(current: RiskLevel, recommended: RiskLevel, signals_evaluated: i32) -> AutopilotInput {
    AutopilotInput {
        current_profile: AutopilotProfile {
            risk_level: current,
            autopilot: true,
        },
        recommendation: recommendation(recommended, signals_evaluated),
        last_change_at: None,
        now: instant("2026-08-19T12:00:00.000Z"),
    }
}

#[test]
fn aplica_una_subida_con_evidencia_y_sin_cooldown() {
    let decision = decidir(&input(RiskLevel::Medium, RiskLevel::High, 2), &policy());

    assert_eq!(decision.action, AutopilotAction::Apply);
    assert_eq!(decision.reason, AutopilotReason::IncreaseAllowed);
    assert_eq!(decision.recommended_level, RiskLevel::High);
}

#[test]
fn propone_una_bajada_en_vez_de_aplicarla() {
    let decision = decidir(&input(RiskLevel::High, RiskLevel::Medium, 2), &policy());

    assert_eq!(decision.action, AutopilotAction::Propose);
    assert_eq!(
        decision.reason,
        AutopilotReason::DecreaseRequiresConfirmation
    );
}

#[test]
fn salta_si_no_hay_evidencia_suficiente() {
    let decision = decidir(&input(RiskLevel::Medium, RiskLevel::High, 0), &policy());

    assert_eq!(decision.action, AutopilotAction::Skip);
    assert_eq!(decision.reason, AutopilotReason::InsufficientEvidence);
}

#[test]
fn conserva_si_ya_esta_en_el_nivel_recomendado() {
    let decision = decidir(&input(RiskLevel::Medium, RiskLevel::Medium, 0), &policy());

    assert_eq!(decision.action, AutopilotAction::Keep);
    assert_eq!(decision.reason, AutopilotReason::AlreadyAtRecommendation);
}

#[test]
fn conserva_durante_el_piso_entre_cambios() {
    let mut input = input(RiskLevel::Medium, RiskLevel::High, 2);
    input.last_change_at = Some(instant("2026-08-15T12:00:00.000Z"));

    let decision = decidir(&input, &policy());

    assert_eq!(decision.action, AutopilotAction::Keep);
    assert_eq!(decision.reason, AutopilotReason::Cooldown);
}

#[test]
fn una_fecha_futura_tambien_bloquea_por_seguridad() {
    let mut input = input(RiskLevel::Medium, RiskLevel::High, 2);
    input.last_change_at = Some(instant("2026-08-20T12:00:00.000Z"));

    let decision = decidir(&input, &policy());

    assert_eq!(decision.action, AutopilotAction::Keep);
    assert_eq!(decision.reason, AutopilotReason::Cooldown);
}

#[test]
fn autopilot_apagado_no_escribe_aunque_haya_evidencia() {
    let mut input = input(RiskLevel::Medium, RiskLevel::High, 2);
    input.current_profile.autopilot = false;

    let decision = decidir(&input, &policy());

    assert_eq!(decision.action, AutopilotAction::Skip);
    assert_eq!(decision.reason, AutopilotReason::Disabled);
}

#[test]
fn la_politica_se_deserializa_desde_el_catalogo() {
    let catalog = AlarmCatalog::parse(
        r#"
version = "test"

[autopilot]
minimum_signals_for_raise = 3
minimum_days_between_changes = 10
"#,
    )
    .unwrap();

    assert_eq!(catalog.autopilot.minimum_signals_for_raise, 3);
    assert_eq!(catalog.autopilot.minimum_days_between_changes, 10);
}

fn policy() -> AutopilotPolicy {
    AutopilotPolicy {
        minimum_signals_for_raise: 1,
        minimum_days_between_changes: 7,
    }
}
