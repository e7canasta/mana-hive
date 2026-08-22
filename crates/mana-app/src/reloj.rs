//! Hora local de la residencia.
//!
//! Dos cosas del producto se deciden por la hora del reloj de pared: que turno
//! de la grilla rige, y si el residente esta en su dia o en su noche. Las dos
//! son **hora local de la residencia**, no del server: una alarma nocturna
//! configurada en Buenos Aires no puede depender de UTC, y una grilla que
//! arranca a las 8 arranca a las 8 ahi.

use chrono::{DateTime, Timelike};
use chrono_tz::Tz;
use mana_kernel::Instante;

/// El instante en la zona de la residencia.
///
/// Una zona ausente o desconocida cae a UTC **y lo dice**: es preferible una
/// hora probablemente correcta y ruidosa a una silenciosamente equivocada.
pub(crate) fn en_zona(at: &Instante, timezone: Option<&str>) -> DateTime<Tz> {
    let tz = match timezone {
        Some(nombre) => match nombre.parse::<Tz>() {
            Ok(tz) => tz,
            Err(_) => {
                tracing::warn!(timezone = %nombre, "zona horaria desconocida: se decide en UTC");
                Tz::UTC
            }
        },
        None => Tz::UTC,
    };
    at.as_datetime().with_timezone(&tz)
}

/// Minuto del dia local, que es la unidad en la que la grilla de turnos declara
/// su comienzo.
pub(crate) fn minuto_del_dia(at: &Instante, timezone: Option<&str>) -> i32 {
    let local = en_zona(at, timezone);
    (local.hour() * 60 + local.minute()) as i32
}

/// Hora del dia local.
///
/// Solo la usa el motor de alarmas, que hoy vive en `mana-engine` con su propia
/// copia; aca queda por el test de zona.
#[allow(dead_code)]
pub(crate) fn hora_del_dia(at: &Instante, timezone: Option<&str>) -> u32 {
    en_zona(at, timezone).hour()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn t(valor: &str) -> Instante {
        valor.parse().unwrap()
    }

    #[test]
    fn el_minuto_del_dia_es_local_y_no_del_server() {
        // 14:20 UTC son las 11:20 en Buenos Aires. Con la grilla declarando
        // `morning` a las 8 y `afternoon` a las 14, la diferencia decide que
        // turno rige — y decidirla en UTC era mandar al turno equivocado.
        let at = t("2026-08-19T14:20:00.000Z");
        assert_eq!(
            minuto_del_dia(&at, Some("America/Argentina/Buenos_Aires")),
            11 * 60 + 20
        );
        assert_eq!(minuto_del_dia(&at, None), 14 * 60 + 20);
    }

    #[test]
    fn una_zona_desconocida_cae_a_utc() {
        let at = t("2026-08-19T14:20:00.000Z");
        assert_eq!(hora_del_dia(&at, Some("Marte/Olympus")), 14);
    }

    #[test]
    fn la_hora_local_cruza_el_dia() {
        // 02:00 UTC son las 23:00 del dia anterior en Buenos Aires.
        let at = t("2026-08-17T02:00:00.000Z");
        assert_eq!(
            hora_del_dia(&at, Some("America/Argentina/Buenos_Aires")),
            23
        );
    }
}
