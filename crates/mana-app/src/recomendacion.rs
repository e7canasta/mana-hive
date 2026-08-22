//! Las señales que alimentan la recomendacion de nivel.
//!
//! Aca vive el **mecanismo**: ir a buscar lo que el hub ya observo y ponerlo en
//! una forma. La **politica** —que umbral preocupa, cuanto pesa, donde corta un
//! nivel— es dato y vive en el bloque `[recomendacion]` del catalogo. Node las
//! tenia mezcladas dentro de `predictRiskLevel`, y por eso subir la exigencia
//! de una señal clinica era un deploy.
//!
//! Juntar las señales cruza cuatro contextos —Observacion, Historia, Vigilancia
//! y Poblacion— y por eso vive en `mana-app` y no en ninguno de ellos.

// La vista de perfil todavia no expone el envelope completo contra
// `packages/contracts`, pero autopilot ya consume estas funciones desde el seam
// de politica. Este `allow` se va cuando la recomendacion quede publica.
#![allow(dead_code)]

use chrono::Duration;
use ctx_politica::{MobilityAid, Recomendacion, Senales};
use mana_kernel::Instante;

use crate::{error::AppFailure, state::AppState};

/// Cuantos registros diarios traer para cubrir la ventana. Se pide de mas y se
/// filtra por fecha: un residente puede tener dias sin resumen, y cortar por
/// cantidad dejaria la ventana incompleta sin avisar.
fn tope(window_days: i64) -> i64 {
    (window_days * 4).max(60)
}

/// El promedio de lo observado. **Una lista vacia es `None`, no cero**: un
/// dominio que nadie observo no es un dominio en cero.
fn promedio(valores: &[f64]) -> Option<f64> {
    let usables: Vec<f64> = valores.iter().copied().filter(|v| v.is_finite()).collect();
    if usables.is_empty() {
        return None;
    }
    Some(usables.iter().sum::<f64>() / usables.len() as f64)
}

impl AppState {
    /// Los rasgos declarados y vigentes de un residente.
    ///
    /// El vocabulario esta validado en `ctx-poblacion` (`fall_risk`,
    /// `wandering`): no es un array libre como en Node. Un atributo con
    /// `valid_to` pasado no cuenta — una afirmacion clinica caduca.
    pub(crate) fn rasgos_de(
        &self,
        resident_id: &str,
        ahora: &Instante,
    ) -> Result<Vec<String>, AppFailure> {
        let hoy = ahora.as_datetime().date_naive();
        let atributos = self
            .poblacion
            .list_attributes(&ctx_poblacion::ResidentId::new(resident_id))?;
        Ok(atributos
            .into_iter()
            .filter(|a| a.valid_to.is_none_or(|hasta| hasta >= hoy))
            .filter(|a| !matches!(a.value.as_str(), "false" | "no" | "0" | ""))
            .map(|a| a.code.as_str().to_owned())
            .collect())
    }

    /// Las señales observadas de un residente en la ventana de la politica.
    pub(crate) fn senales_de(
        &self,
        resident_id: &str,
        ahora: &Instante,
    ) -> Result<Senales, AppFailure> {
        let politica = &self.catalog.recomendacion;
        let desde_dia = (*ahora.as_datetime() - Duration::days(politica.window_days))
            .format("%Y-%m-%d")
            .to_string();
        let desde_incidentes = *ahora.as_datetime() - Duration::days(politica.incident_window_days);
        let tope = tope(politica.window_days);

        let sueno: Vec<_> = self
            .observation
            .resident_sleep(resident_id, tope)?
            .into_iter()
            .filter(|s| s.observed_on >= desde_dia)
            .collect();

        let movilidad: Vec<_> = self
            .observation
            .resident_mobility(resident_id, tope)?
            .into_iter()
            .filter(|m| m.observed_on >= desde_dia)
            .collect();

        let bano: Vec<_> = self
            .observation
            .resident_bathroom(resident_id, tope)?
            .into_iter()
            .filter(|b| b.observed_on >= desde_dia)
            .collect();

        let incidentes: Vec<_> = self
            .history
            .list_by_resident(resident_id, tope)?
            .into_iter()
            .filter(|i| *i.occurred_at.as_datetime() >= desde_incidentes)
            .collect();

        // Las alertas ya emitidas son una señal que Node no tenia: hasta F11.1
        // el hub no emitia ninguna. Cuantas veces sono la cama de alguien habla
        // de como le fue, no de como lo configuraron.
        let desde_alertas =
            Instante::new(*ahora.as_datetime() - Duration::days(politica.window_days));
        let alertas = self
            .vigilancia
            .list_alerts(None, None, Some(resident_id))?
            .into_iter()
            .filter(|a| a.occurred_at >= desde_alertas)
            .count() as f64;

        // La velocidad de marcha **no esta almacenada**: se deriva de la
        // distancia y los minutos caminados. Un dia sin distancia informada, o
        // sin minutos caminados, queda fuera del promedio en vez de entrar como
        // cero y hacer parecer que el residente no puede caminar.
        let velocidades: Vec<f64> = movilidad
            .iter()
            .filter_map(|m| {
                let metros = m.distance_meters?;
                if m.walking_minutes <= 0 {
                    return None;
                }
                Some(metros / (m.walking_minutes as f64 * 60.0))
            })
            .collect();

        Ok(Senales {
            nights_observed: sueno.len() as i64,
            days_observed: movilidad.len() as i64,
            bathroom_days_observed: bano.len() as i64,
            bed_exits_per_night: promedio(
                &sueno
                    .iter()
                    .map(|s| s.bed_exit_count as f64)
                    .collect::<Vec<_>>(),
            ),
            wakes_per_night: promedio(
                &sueno
                    .iter()
                    .map(|s| s.wake_count as f64)
                    .collect::<Vec<_>>(),
            ),
            awake_minutes_per_night: promedio(
                &sueno
                    .iter()
                    .map(|s| (s.awake_minutes + s.restless_minutes) as f64)
                    .collect::<Vec<_>>(),
            ),
            bathroom_visits_per_day: promedio(
                &bano
                    .iter()
                    .map(|b| b.visit_count as f64)
                    .collect::<Vec<_>>(),
            ),
            walking_speed_mps: promedio(&velocidades),
            transfers_per_day: promedio(
                &movilidad
                    .iter()
                    .map(|m| m.transfer_count as f64)
                    .collect::<Vec<_>>(),
            ),
            // Sin ninguna alerta la señal es cero observado, no ausencia: el
            // periodo se vigilo y no sono nada.
            alerts_per_day: Some(alertas / politica.window_days.max(1) as f64),
            falls: incidentes
                .iter()
                .filter(|i| i.kind == ctx_historia::IncidentKind::Fall)
                .count() as i64,
            severe_falls: incidentes
                .iter()
                .filter(|i| {
                    i.kind == ctx_historia::IncidentKind::Fall
                        && matches!(
                            i.severity,
                            ctx_historia::Severity::High | ctx_historia::Severity::Critical
                        )
                })
                .count() as i64,
            transfer_incidents: incidentes
                .iter()
                .filter(|i| i.kind == ctx_historia::IncidentKind::Transfer)
                .count() as i64,
        })
    }

    /// La recomendacion de nivel para un residente.
    pub(crate) fn recomendacion_para(
        &self,
        resident_id: &str,
        accesorio: MobilityAid,
        ahora: &Instante,
    ) -> Result<Recomendacion, AppFailure> {
        let senales = self.senales_de(resident_id, ahora)?;
        let rasgos = self.rasgos_de(resident_id, ahora)?;
        Ok(ctx_politica::recomendar(
            &senales,
            accesorio,
            &rasgos,
            &self.catalog.recomendacion,
            &self.catalog.templates,
        ))
    }
}
