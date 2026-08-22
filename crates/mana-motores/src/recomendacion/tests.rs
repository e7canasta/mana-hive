//! Contra la politica real del catalogo, no contra una muestra: si los pesos
//! del archivo dejan de producir el nivel que esperamos, es un cambio clinico y
//! tiene que doler aca.

use super::*;
use crate::catalogo::AlarmCatalog;

const CATALOGO: &str = include_str!("../../../../config/alarm-catalog.toml");

fn catalogo() -> AlarmCatalog {
    AlarmCatalog::parse(CATALOGO).unwrap()
}

fn recomendar_con(senales: Senales, accesorio: MobilityAid, rasgos: &[&str]) -> Recomendacion {
    let catalogo = catalogo();
    let rasgos: Vec<String> = rasgos.iter().map(|r| (*r).to_owned()).collect();
    recomendar(
        &senales,
        accesorio,
        &rasgos,
        &catalogo.recomendacion,
        &catalogo.templates,
    )
}

#[test]
fn la_politica_del_catalogo_se_carga_entera() {
    let politica = catalogo().recomendacion;
    assert_eq!(politica.window_days, 14);
    assert_eq!(politica.incident_window_days, 90);
    assert_eq!(politica.medium_at, 3);
    assert_eq!(politica.high_at, 6);
    assert_eq!(politica.reglas.len(), 11);
}

#[test]
fn sin_una_sola_senal_no_recomienda_nada_y_lo_dice() {
    // El caso que importa: un residente recien ingresado. El nivel bajo **no**
    // significa "esta bien", significa "no sabemos", y `signals_evaluated` es lo
    // que permite distinguirlo. Sin ese numero, la ausencia de evidencia se lee
    // como evidencia de ausencia.
    let r = recomendar_con(Senales::default(), MobilityAid::None, &[]);

    assert_eq!(r.score, 0);
    assert_eq!(r.signals_evaluated, 0);
    assert_eq!(r.level, RiskLevel::Low);
    assert!(r.factors.is_empty());
}

#[test]
fn una_senal_no_observada_no_cuenta_como_cero() {
    // Velocidad de marcha ausente no es "camina lentisimo" ni "camina bien":
    // es que nadie la midio, y la regla no se evalua.
    let sin_marcha = recomendar_con(
        Senales {
            walking_speed_mps: None,
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(sin_marcha.signals_evaluated, 0);
    assert_eq!(sin_marcha.score, 0);

    let con_marcha_lenta = recomendar_con(
        Senales {
            walking_speed_mps: Some(0.4),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(con_marcha_lenta.signals_evaluated, 1);
    assert_eq!(con_marcha_lenta.score, 2);
}

#[test]
fn la_marcha_no_pondera_en_silla_de_ruedas() {
    // En silla de ruedas la ausencia de velocidad es el patron esperado, no un
    // deterioro. Puntuarla seria castigar a alguien por su accesorio.
    let senales = Senales {
        walking_speed_mps: Some(0.2),
        ..Default::default()
    };

    let caminando = recomendar_con(senales.clone(), MobilityAid::Walker, &[]);
    assert!(caminando.factors.iter().any(|f| f.id == "gait"));

    let en_silla = recomendar_con(senales, MobilityAid::Wheelchair, &[]);
    assert!(!en_silla.factors.iter().any(|f| f.id == "gait"));
}

#[test]
fn las_bandas_toman_la_mas_exigente_que_se_cumple() {
    let dos_puntos = recomendar_con(
        Senales {
            bed_exits_per_night: Some(3.2),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(dos_puntos.score, 2);
    assert_eq!(dos_puntos.factors[0].detail, "3.2 por noche");

    let un_punto = recomendar_con(
        Senales {
            bed_exits_per_night: Some(1.6),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(un_punto.score, 1);

    // Debajo de la banda mas baja la señal **se evaluo** y no sumo. No es lo
    // mismo que no haberla mirado.
    let ninguno = recomendar_con(
        Senales {
            bed_exits_per_night: Some(0.5),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(ninguno.score, 0);
    assert_eq!(ninguno.signals_evaluated, 1);
}

#[test]
fn un_mismo_factor_que_llega_por_dos_vias_se_acumula_en_una_entrada() {
    // Una caida registrada y el riesgo declarado en el perfil son el mismo
    // factor por dos caminos: suman juntos y muestran un solo chip con las dos
    // razones. Dos chips iguales en el panel serian ruido.
    let r = recomendar_con(
        Senales {
            falls: 2,
            ..Default::default()
        },
        MobilityAid::None,
        &["fall_risk"],
    );

    let historial: Vec<_> = r
        .factors
        .iter()
        .filter(|f| f.id == "fall_history")
        .collect();
    assert_eq!(historial.len(), 1, "un solo chip");
    assert_eq!(historial[0].weight, 3, "2 por la caida + 1 por el perfil");
    assert!(historial[0].detail.contains("2 eventos en 90 dias"));
    assert!(historial[0].detail.contains("riesgo de caida declarado"));
}

#[test]
fn una_caida_grave_suma_encima() {
    let leve = recomendar_con(
        Senales {
            falls: 1,
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    let grave = recomendar_con(
        Senales {
            falls: 1,
            severe_falls: 1,
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(grave.score, leve.score + 1);
}

#[test]
fn un_incidente_de_transferencia_pesa_menos_que_una_caida() {
    let transferencia = recomendar_con(
        Senales {
            transfer_incidents: 1,
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    let caida = recomendar_con(
        Senales {
            falls: 1,
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(transferencia.score, 1);
    assert_eq!(caida.score, 2);
}

#[test]
fn los_cortes_de_nivel_salen_del_catalogo() {
    // 3 y 6 son dato, no constantes de codigo.
    let bajo = recomendar_con(
        Senales {
            bed_exits_per_night: Some(1.6),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(bajo.score, 1, "1.6 cae en la banda de 1.5, que vale 1");
    assert_eq!(bajo.level, RiskLevel::Low);

    // 2 por las salidas de cama + 2 por la deambulacion declarada.
    let medio = recomendar_con(
        Senales {
            bed_exits_per_night: Some(3.2),
            ..Default::default()
        },
        MobilityAid::None,
        &["wandering"],
    );
    assert_eq!(medio.score, 4);
    assert_eq!(medio.level, RiskLevel::Medium);

    let alto = recomendar_con(
        Senales {
            falls: 1,
            severe_falls: 1,
            bed_exits_per_night: Some(3.2),
            wakes_per_night: Some(6.5),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert!(alto.score >= 6, "puntaje {}", alto.score);
    assert_eq!(alto.level, RiskLevel::High);
}

#[test]
fn las_alertas_ya_emitidas_son_una_senal() {
    // Es la señal que Node no tenia: el hub emite alertas desde F11.1, y cuantas
    // veces sono la cama de alguien es evidencia de como le fue, no de como lo
    // configuraron.
    let r = recomendar_con(
        Senales {
            alerts_per_day: Some(4.5),
            ..Default::default()
        },
        MobilityAid::None,
        &[],
    );
    assert_eq!(r.score, 2);
    assert!(r
        .factors
        .iter()
        .any(|f| f.detail.contains("alertas por dia")));
}

#[test]
fn el_accesorio_declarado_suma_con_su_propio_factor() {
    let r = recomendar_con(Senales::default(), MobilityAid::Wheelchair, &[]);
    assert_eq!(r.score, 1);
    assert_eq!(r.factors[0].id, "wheelchair");

    let sin = recomendar_con(Senales::default(), MobilityAid::None, &[]);
    assert!(sin.factors.is_empty());
}

#[test]
fn la_plantilla_sugerida_sale_del_perfil_declarado() {
    let catalogo = catalogo();
    assert_eq!(
        plantilla_sugerida(&["wandering".to_owned()], &catalogo.templates),
        "night_wandering"
    );
    assert_eq!(
        plantilla_sugerida(&["fall_risk".to_owned()], &catalogo.templates),
        "post_fall"
    );
    // Sin nada declarado, la que solo aplica el preset del nivel.
    assert_eq!(plantilla_sugerida(&[], &catalogo.templates), "balanced");
}

#[test]
fn una_senal_que_la_politica_nombra_y_el_motor_no_conoce_no_vale_cero() {
    // Si alguien agrega una regla al catalogo con una señal que Rust todavia no
    // calcula, la regla no se evalua. Tratarla como cero seria puntuar sobre
    // algo que nadie midio.
    let politica = PoliticaDeRecomendacion {
        medium_at: 1,
        high_at: 2,
        reglas: vec![ReglaDeRiesgo::Banda {
            factor: "gait".to_owned(),
            senal: "senal_del_futuro".to_owned(),
            direccion: Direccion::AlMenos,
            unidad: String::new(),
            decimales: 1,
            sin_accesorio: Vec::new(),
            bandas: vec![Banda {
                umbral: 0.0,
                puntos: 5,
            }],
        }],
        ..Default::default()
    };

    let r = recomendar(
        &Senales::default(),
        MobilityAid::None,
        &[],
        &politica,
        &catalogo().templates,
    );
    assert_eq!(r.score, 0);
    assert_eq!(r.signals_evaluated, 0);
}
