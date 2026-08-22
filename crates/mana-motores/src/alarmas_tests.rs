//! Los siete primeros espejan `tests/alarm-engine.test.mjs`, que es la spec
//! ejecutable que dejo el motor de Node: mismos umbrales, misma calibracion,
//! mismos resultados. Los que siguen son invariantes que F11 agrega y que aquel
//! motor no tenia.

use std::collections::BTreeSet;

use mana_kernel::Instante;

use super::alarmas::*;

fn instante(valor: &str) -> Instante {
    valor.parse().expect("instante valido")
}

/// La calibracion sale de `CALIBRATION` en `api/alarm-presets.js`:
/// `[dia, noche, minutos, sensibilidad]` por nivel de riesgo.
fn regla(
    id: &str,
    clase: Clase,
    dia: Accion,
    noche: Accion,
    minutos: i64,
    sensibilidad: Sensibilidad,
) -> ReglaEfectiva {
    ReglaEfectiva {
        id: id.to_owned(),
        clase,
        dia,
        noche,
        minutos,
        sensibilidad,
        bloqueada: false,
        etiqueta: id.to_owned(),
    }
}

/// `out_of_bed_dwell` en nivel alto: notifica de dia, alarma de noche, tolera
/// 30 minutos, y su sensibilidad alta acorta la espera a la mitad.
fn fuera_de_cama_alto() -> ReglaEfectiva {
    regla(
        "out_of_bed_dwell",
        Clase::Permanencia,
        Accion::Notify,
        Accion::Alarm,
        30,
        Sensibilidad::Alta,
    )
}

fn contexto(estado: &str, previo: &str, desde: &str, hasta: &str, turno: Turno) -> Contexto {
    Contexto {
        bed_id: "118-0".to_owned(),
        resident_id: Some("resident-demo-118-0".to_owned()),
        estado: EstadoObservado::parse(estado),
        estado_previo: EstadoObservado::parse(previo),
        room_state: Some("occupied".to_owned()),
        state_since: instante(desde),
        occurred_at: instante(hasta),
        turno,
        evidencia_ref: Some("evt-1".to_owned()),
    }
}

fn ninguna() -> BTreeSet<String> {
    BTreeSet::new()
}

#[test]
fn una_permanencia_vence_por_el_paso_del_tiempo_sin_evento_nuevo() {
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);

    // El umbral efectivo son 15 minutos: 30 tolerados por la mitad.
    let temprano = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:10:00Z",
        Turno::Dia,
    );
    assert!(
        evaluar(&temprano, &perfil, &ninguna(), Disparo::Barrido).is_empty(),
        "a los 10 minutos todavia no vencio"
    );

    let vencido = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:20:00Z",
        Turno::Dia,
    );
    let alertas = evaluar(&vencido, &perfil, &ninguna(), Disparo::Barrido);
    assert_eq!(alertas.len(), 1);
    assert_eq!(alertas[0].rule_id, "out_of_bed_dwell");
    assert_eq!(
        alertas[0].nivel,
        NivelAlerta::Medium,
        "de dia el nivel alto notifica"
    );
}

#[test]
fn el_barrido_no_reinterpreta_transiciones_viejas() {
    let perfil = PerfilEfectivo::new([
        fuera_de_cama_alto(),
        regla(
            "bed_exit",
            Clase::Transicion,
            Accion::Alarm,
            Accion::Alarm,
            0,
            Sensibilidad::Alta,
        ),
    ]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:40:00Z",
        Turno::Dia,
    );

    let alertas = evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido);
    assert!(
        !alertas.iter().any(|a| a.rule_id == "bed_exit"),
        "levantarse de la cama se avisa cuando ocurre, no una hora despues"
    );

    let alertas = evaluar(&contexto, &perfil, &ninguna(), Disparo::Evento);
    assert!(
        alertas.iter().any(|a| a.rule_id == "bed_exit"),
        "y cuando llega el evento si se reconoce"
    );
}

#[test]
fn un_episodio_no_repite_el_aviso_aunque_el_barrido_siga_corriendo() {
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:40:00Z",
        Turno::Dia,
    );

    assert_eq!(
        evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido).len(),
        1
    );

    let ya = BTreeSet::from(["out_of_bed_dwell".to_owned()]);
    assert!(
        evaluar(&contexto, &perfil, &ya, Disparo::Barrido).is_empty(),
        "ya se aviso en este episodio"
    );
}

#[test]
fn el_turno_de_la_residencia_decide_el_nivel_del_aviso() {
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-17T00:00:00Z",
        "2026-08-17T01:00:00Z",
        Turno::Noche,
    );

    let alertas = evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido);
    assert_eq!(
        alertas[0].nivel,
        NivelAlerta::High,
        "de noche el nivel alto alarma"
    );
}

#[test]
fn la_permanencia_en_la_cama_se_vigila_de_dia_y_no_de_noche() {
    // Nivel alto tolera 240 minutos en la cama de dia; de noche esta apagada
    // porque dormir la noche entera es el resultado buscado, no un aviso.
    let perfil = PerfilEfectivo::new([regla(
        "in_bed_dwell",
        Clase::Permanencia,
        Accion::Notify,
        Accion::Off,
        240,
        Sensibilidad::Estandar,
    )]);

    let temprano = contexto(
        "laying_in_bed",
        "standing",
        "2026-08-16T12:00:00Z",
        "2026-08-16T15:00:00Z",
        Turno::Dia,
    );
    assert!(
        evaluar(&temprano, &perfil, &ninguna(), Disparo::Barrido).is_empty(),
        "a las tres horas todavia no vencio"
    );

    let vencido = contexto(
        "laying_in_bed",
        "standing",
        "2026-08-16T12:00:00Z",
        "2026-08-16T16:30:00Z",
        Turno::Dia,
    );
    let alertas = evaluar(&vencido, &perfil, &ninguna(), Disparo::Barrido);
    assert_eq!(alertas.len(), 1);
    assert_eq!(alertas[0].rule_id, "in_bed_dwell");

    let de_noche = contexto(
        "laying_in_bed",
        "standing",
        "2026-08-16T20:00:00Z",
        "2026-08-17T03:00:00Z",
        Turno::Noche,
    );
    assert!(
        evaluar(&de_noche, &perfil, &ninguna(), Disparo::Barrido).is_empty(),
        "de noche la cama no avisa"
    );
}

#[test]
fn acostarse_cierra_el_episodio_y_se_reconoce_como_transicion_propia() {
    let estado = EstadoObservado::parse;

    assert_eq!(
        transicion_entre(&estado("standing"), &estado("laying_in_bed")),
        Some("bed_entry")
    );
    assert_eq!(
        transicion_entre(&estado("sitting_on_bed_edge"), &estado("laying_in_bed")),
        Some("bed_entry")
    );
    assert_eq!(
        transicion_entre(&estado("unoccupied"), &estado("laying_in_bed")),
        Some("bed_entry")
    );
    // Sin estado previo conocido no hay transicion que reconstruir.
    assert_eq!(
        transicion_entre(&estado("unknown"), &estado("laying_in_bed")),
        None
    );
    // Y no se confunde con la contraria, que sigue siendo la de mas riesgo.
    assert_eq!(
        transicion_entre(&estado("laying_in_bed"), &estado("standing")),
        Some("bed_exit")
    );
    // Repetir el estado no es una transicion: es el detector insistiendo.
    assert_eq!(
        transicion_entre(&estado("standing"), &estado("standing")),
        None
    );
}

#[test]
fn una_configuracion_apagada_no_dispara_aunque_venza_el_tiempo() {
    // Nivel bajo de dia: la permanencia fuera de la cama no avisa.
    let perfil = PerfilEfectivo::new([regla(
        "out_of_bed_dwell",
        Clase::Permanencia,
        Accion::Off,
        Accion::Notify,
        60,
        Sensibilidad::Baja,
    )]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T14:00:00Z",
        "2026-08-16T16:00:00Z",
        Turno::Dia,
    );

    assert!(evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido).is_empty());
}

// --- Lo que F11 agrega y el motor de Node no tenia ---------------------------

#[test]
fn una_cama_sin_residente_se_sigue_vigilando() {
    // Node cortaba sin `residentId` y la cama dejaba de vigilarse. La politica
    // fija de la cama es un perfil como cualquier otro: quien compone decide
    // cual, el motor evalua igual.
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);
    let mut sin_residente = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:20:00Z",
        Turno::Dia,
    );
    sin_residente.resident_id = None;

    let alertas = evaluar(&sin_residente, &perfil, &ninguna(), Disparo::Barrido);
    assert_eq!(alertas.len(), 1);
    assert_eq!(alertas[0].resident_id, None);
}

#[test]
fn una_regla_que_el_detector_todavia_no_permite_no_dispara() {
    // `bathroom_dwell` se configura y no puede sonar: no hay evento que la
    // resuelva. Que este en el perfil no la vuelve operativa.
    let perfil = PerfilEfectivo::new([regla(
        "bathroom_dwell",
        Clase::Permanencia,
        Accion::Alarm,
        Accion::Alarm,
        1,
        Sensibilidad::Alta,
    )]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T16:00:00Z",
        Turno::Dia,
    );

    assert!(evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido).is_empty());
    assert_eq!(
        reglas_pendientes(["bed_exit", "bathroom_dwell", "on_floor"]),
        vec!["bathroom_dwell".to_owned(), "on_floor".to_owned()],
        "y el motor puede decir cuales son"
    );
}

#[test]
fn una_permanencia_no_se_ata_al_evento_que_no_la_disparo() {
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:20:00Z",
        Turno::Dia,
    );

    let alertas = evaluar(&contexto, &perfil, &ninguna(), Disparo::Barrido);
    assert_eq!(alertas[0].evidence_kind, TipoEvidencia::DwellWindow);
    assert_eq!(
        alertas[0].evidence_ref, None,
        "no la trajo ningun evento: eso es lo que la hace una permanencia"
    );
}

#[test]
fn una_transicion_conserva_el_evento_que_la_trajo() {
    let perfil = PerfilEfectivo::new([regla(
        "bed_exit",
        Clase::Transicion,
        Accion::Alarm,
        Accion::Alarm,
        0,
        Sensibilidad::Alta,
    )]);
    let contexto = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:00:00Z",
        Turno::Dia,
    );

    let alertas = evaluar(&contexto, &perfil, &ninguna(), Disparo::Evento);
    assert_eq!(alertas[0].evidence_kind, TipoEvidencia::SensorEvent);
    assert_eq!(alertas[0].evidence_ref.as_deref(), Some("evt-1"));
}

#[test]
fn con_el_temporizador_en_cero_igual_hay_un_piso() {
    // Sin piso, cualquier parpadeo del detector se convierte en una alarma.
    let perfil = PerfilEfectivo::new([regla(
        "bed_exit",
        Clase::Transicion,
        Accion::Alarm,
        Accion::Alarm,
        0,
        Sensibilidad::Baja,
    )]);

    let parpadeo = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:00:30Z",
        Turno::Dia,
    );
    assert!(
        evaluar(&parpadeo, &perfil, &ninguna(), Disparo::Evento).is_empty(),
        "a los 30 segundos la sensibilidad baja todavia no confirma"
    );

    let sostenido = contexto(
        "standing",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T15:01:00Z",
        Turno::Dia,
    );
    assert_eq!(
        evaluar(&sostenido, &perfil, &ninguna(), Disparo::Evento).len(),
        1
    );
}

#[test]
fn un_estado_desconocido_no_es_estar_fuera_de_la_cama() {
    let perfil = PerfilEfectivo::new([fuera_de_cama_alto()]);

    let desconocido = contexto(
        "unknown",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T16:00:00Z",
        Turno::Dia,
    );
    assert!(
        evaluar(&desconocido, &perfil, &ninguna(), Disparo::Barrido).is_empty(),
        "ausencia de observacion no es una ubicacion"
    );

    // Un estado que el vocabulario no nombra si cuenta: el residente esta en
    // algun lado y no es la cama.
    let no_nombrado = contexto(
        "in_bathroom",
        "laying_in_bed",
        "2026-08-16T15:00:00Z",
        "2026-08-16T16:00:00Z",
        Turno::Dia,
    );
    assert_eq!(
        evaluar(&no_nombrado, &perfil, &ninguna(), Disparo::Barrido).len(),
        1
    );
}

#[test]
fn una_regla_bloqueada_que_llega_apagada_se_denuncia() {
    // `fall` no se puede desactivar en ninguna capa. Si el perfil efectivo la
    // trae apagada, el que resuelve las capas tiene un bug: el motor no lo
    // corrige en silencio, lo expone.
    let mut caida = regla(
        "fall",
        Clase::Transicion,
        Accion::Off,
        Accion::Alarm,
        2,
        Sensibilidad::Alta,
    );
    caida.bloqueada = true;
    let perfil = PerfilEfectivo::new([caida, fuera_de_cama_alto()]);

    assert_eq!(perfil.bloqueadas_apagadas(), vec!["fall"]);
}

#[test]
fn el_turno_se_corta_a_las_siete_y_a_las_diecinueve() {
    assert_eq!(Turno::desde_hora_local(6), Turno::Noche);
    assert_eq!(Turno::desde_hora_local(7), Turno::Dia);
    assert_eq!(Turno::desde_hora_local(18), Turno::Dia);
    assert_eq!(Turno::desde_hora_local(19), Turno::Noche);
    assert_eq!(Turno::desde_hora_local(0), Turno::Noche);
}

#[test]
fn la_sensibilidad_calibra_la_espera() {
    let base = |sensibilidad| {
        regla(
            "out_of_bed_dwell",
            Clase::Permanencia,
            Accion::Notify,
            Accion::Notify,
            30,
            sensibilidad,
        )
        .espera_segundos()
    };

    assert_eq!(base(Sensibilidad::Alta), 900, "la mitad de 30 minutos");
    assert_eq!(base(Sensibilidad::Estandar), 1800);
    assert_eq!(base(Sensibilidad::Baja), 2700, "una vez y media");
}
