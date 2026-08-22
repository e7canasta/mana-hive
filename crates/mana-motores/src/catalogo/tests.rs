//! Los tests corren contra el **catalogo real**, no contra una muestra: es el
//! archivo que el hub carga al arrancar, y si deja de resolver, la fase entera
//! deja de tener sentido.

use serde_json::json;

use super::*;

const CATALOGO: &str = include_str!("../../../../config/alarm-catalog.toml");

fn catalogo() -> AlarmCatalog {
    AlarmCatalog::parse(CATALOGO).expect("el catalogo real tiene que parsear")
}

#[test]
fn el_catalogo_real_parsea_entero() {
    let catalogo = catalogo();

    assert_eq!(catalogo.rules.len(), 29);
    assert_eq!(catalogo.groups.len(), 4);
    assert_eq!(catalogo.templates.len(), 6);
    assert_eq!(catalogo.presets.len(), 3, "un preset por nivel de riesgo");
    assert_eq!(catalogo.shift_hours.day_start, 7);
    assert_eq!(catalogo.shift_hours.night_start, 19);

    for level in [RiskLevel::Low, RiskLevel::Medium, RiskLevel::High] {
        let preset = catalogo.preset_for(level).expect("el nivel tiene preset");
        assert_eq!(
            preset.len(),
            29,
            "el preset de {} cubre todas las reglas",
            level.as_str()
        );
    }
}

#[test]
fn cada_regla_declara_su_clase_con_su_temporizador() {
    // La clase no se declara aparte: la lleva el temporizador. Que las dos
    // cosas puedan discrepar seria un segundo lugar donde equivocarse.
    for rule in &catalogo().rules {
        match rule.timer_param().map(|param| param.kind.as_str()) {
            Some("dwell") => assert_eq!(rule.class, Class::Dwell, "{}", rule.id),
            Some("confirm") => assert_eq!(rule.class, Class::Transition, "{}", rule.id),
            _ => assert_eq!(rule.class, Class::None, "{}", rule.id),
        }
    }
}

#[test]
fn las_siete_reglas_operativas_del_motor_existen_con_su_clase() {
    let catalogo = catalogo();

    for id in ["bed_exit", "bed_edge", "sitting_in_bed", "bed_entry"] {
        let rule = catalogo
            .find_rule(id)
            .unwrap_or_else(|| panic!("falta {id}"));
        assert_eq!(rule.class, Class::Transition, "{id}");
    }
    for id in ["out_of_bed_dwell", "in_bed_dwell", "room_absence_dwell"] {
        let rule = catalogo
            .find_rule(id)
            .unwrap_or_else(|| panic!("falta {id}"));
        assert_eq!(rule.class, Class::Dwell, "{id}");
    }
}

#[test]
fn el_preset_del_nivel_es_la_capa_base() {
    let reglas =
        catalogo().resolve_rules(RiskLevel::High, MobilityAid::None, false, None, &json!({}));

    let fuera = &reglas["out_of_bed_dwell"];
    assert_eq!(fuera.day, Action::Notify);
    assert_eq!(fuera.night, Action::Alarm);
    assert_eq!(fuera.timer_minutes(), 30);
    assert_eq!(fuera.sensitivity(), "high");
    assert_eq!(fuera.source, RuleSource::Preset);
    assert!(!fuera.customized);

    // Y el nivel bajo, de dia, no avisa: es el caso que prueba que el preset
    // manda de verdad y no que todo termina encendido.
    let bajas =
        catalogo().resolve_rules(RiskLevel::Low, MobilityAid::None, false, None, &json!({}));
    assert_eq!(bajas["out_of_bed_dwell"].day, Action::Off);
}

#[test]
fn la_plantilla_pisa_al_preset_y_deja_dicho_de_donde_salio() {
    let reglas = catalogo().resolve_rules(
        RiskLevel::Low,
        MobilityAid::None,
        false,
        Some("night_wandering"),
        &json!({}),
    );

    let fuera = &reglas["out_of_bed_dwell"];
    assert_eq!(fuera.night, Action::Alarm, "la plantilla refuerza la noche");
    assert_eq!(fuera.day, Action::Off, "y no toca el dia del preset");
    assert_eq!(fuera.timer_minutes(), 20, "con su propia tolerancia");
    assert_eq!(fuera.source, RuleSource::Template);
}

#[test]
fn el_ajuste_manual_pisa_a_la_plantilla() {
    let overrides = json!({ "out_of_bed_dwell": { "day": "alarm", "dwell_minutes": 5 } });
    let reglas = catalogo().resolve_rules(
        RiskLevel::Low,
        MobilityAid::None,
        true,
        Some("night_wandering"),
        &overrides,
    );

    let fuera = &reglas["out_of_bed_dwell"];
    assert_eq!(fuera.day, Action::Alarm);
    assert_eq!(fuera.night, Action::Alarm, "lo que el ajuste no toca queda");
    assert_eq!(fuera.timer_minutes(), 5);
    assert_eq!(fuera.source, RuleSource::Custom);
    assert!(fuera.customized);
}

#[test]
fn en_modo_preset_el_ajuste_queda_guardado_y_no_se_aplica() {
    // Volver al preset no puede costar perder lo que alguien configuro.
    let overrides = json!({ "out_of_bed_dwell": { "day": "alarm" } });
    let reglas =
        catalogo().resolve_rules(RiskLevel::High, MobilityAid::None, false, None, &overrides);

    assert_eq!(reglas["out_of_bed_dwell"].day, Action::Notify);
    assert_eq!(reglas["out_of_bed_dwell"].source, RuleSource::Preset);
}

#[test]
fn la_regla_bloqueada_no_se_puede_apagar_en_ninguna_capa() {
    let catalogo = catalogo();
    assert!(catalogo.find_rule("fall").expect("fall existe").locked);

    let overrides = json!({ "fall": { "day": "off", "night": "off" } });
    let reglas = catalogo.resolve_rules(RiskLevel::Low, MobilityAid::None, true, None, &overrides);
    assert_ne!(reglas["fall"].day, Action::Off, "caida siempre suena");
    assert_ne!(reglas["fall"].night, Action::Off);

    // Y guardarlo tampoco se puede: no se descarta en silencio, se rechaza.
    let error = catalogo
        .validate_override("fall", "day", &json!("off"))
        .expect_err("apagar fall es un error");
    assert!(matches!(error, CatalogError::BlockedRule(_)));
}

#[test]
fn una_regla_de_silla_no_aplica_a_quien_no_usa_silla() {
    let catalogo = catalogo();

    let sin_accesorio =
        catalogo.resolve_rules(RiskLevel::High, MobilityAid::None, false, None, &json!({}));
    assert!(!sin_accesorio.contains_key("wheelchair_exit"));

    let con_silla = catalogo.resolve_rules(
        RiskLevel::High,
        MobilityAid::Wheelchair,
        false,
        None,
        &json!({}),
    );
    assert!(con_silla.contains_key("wheelchair_exit"));
}

#[test]
fn un_ajuste_invalido_se_denuncia_en_vez_de_descartarse() {
    let catalogo = catalogo();

    // `fall` confirma como maximo 2 minutos.
    assert!(catalogo
        .validate_override("fall", "delay_minutes", &json!(999))
        .is_err());
    assert!(catalogo
        .validate_override("bed_exit", "sensitivity", &json!("altisima"))
        .is_err());
    assert!(matches!(
        catalogo.validate_override("bed_exit", "no_existe", &json!(1)),
        Err(CatalogError::InvalidOverride { .. })
    ));
    assert!(matches!(
        catalogo.validate_override("no_existe", "sensitivity", &json!("high")),
        Err(CatalogError::UnknownRule(_))
    ));

    // Y el documento entero, como llega del cliente.
    let overrides = json!({ "bed_exit": { "night": "alarm", "delay_minutes": 3 } });
    assert!(catalogo.validate_overrides(&overrides).is_ok());
    let malo = json!({ "bed_exit": { "night": "gritar" } });
    assert!(catalogo.validate_overrides(&malo).is_err());
}

#[test]
fn el_conjunto_vacio_no_es_una_configuracion_valida() {
    // Seria una regla encendida que no puede disparar nunca. Para dejar de
    // vigilar el accesorio esta la accion "off", que queda en el registro.
    let catalogo = catalogo();
    assert!(catalogo
        .validate_override("bed_rail", "watch", &json!([]))
        .is_err());
    assert!(catalogo
        .validate_override("bed_rail", "watch", &json!(["up"]))
        .is_ok());
}

#[test]
fn el_accesorio_sigue_al_contrato_del_cliente() {
    // `packages/contracts` acepta none | walker | wheelchair. No hay baston.
    assert_eq!(MobilityAid::parse("walker").unwrap(), MobilityAid::Walker);
    assert!(MobilityAid::parse("cane").is_err());
}

#[test]
fn el_nivel_de_riesgo_se_parsea_y_se_nombra() {
    assert_eq!(RiskLevel::parse("high").unwrap(), RiskLevel::High);
    assert_eq!(RiskLevel::Medium.as_str(), "medium");
    assert!(RiskLevel::parse("altisimo").is_err());
}

#[test]
fn buscar_reglas_mira_id_etiqueta_grupo_y_detalle() {
    let catalogo = catalogo();
    let encontradas = catalogo.search_rules("cama");
    assert!(encontradas.iter().any(|r| r.id == "bed_exit"));
    assert!(catalogo
        .search_rules("wheelchair")
        .iter()
        .any(|r| r.id == "wheelchair_exit"));
}
