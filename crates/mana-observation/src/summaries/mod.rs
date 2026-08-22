//! Resumenes diarios producidos por una fuente analitica.
//!
//! Son evidencia, no registro: nadie los corrige a mano, se reingieren. Por eso
//! llevan el mismo sobre que una deteccion —`source_record_id`, `source`,
//! `model_version`, `confidence`, `provenance_json`— y no un actor humano.

pub(crate) mod sqlite;

use mana_kernel::{define_kinds, Id, Instante};

use crate::error::ObservationError;

define_kinds!(SummaryKind);

pub type SummaryId = Id<SummaryKind>;

pub fn new_summary_id() -> SummaryId {
    Id::new(crate::common::random_id("sum"))
}

const MINUTES_IN_A_DAY: i32 = 1440;

/// Procedencia comun a todo resumen. Sin esto un numero clinico no se puede
/// auditar: no se sabe que modelo lo produjo ni con cuanta confianza.
#[derive(Clone, Debug)]
pub struct Provenance {
    pub source: String,
    pub model_version: String,
    pub confidence: Option<f64>,
    pub provenance_json: String,
}

impl Provenance {
    pub fn validate(&self) -> Result<(), ObservationError> {
        if self.source.trim().is_empty() {
            return Err(ObservationError::Validation(
                "source es obligatorio".to_owned(),
            ));
        }
        if self.model_version.trim().is_empty() {
            return Err(ObservationError::Validation(
                "model_version es obligatorio".to_owned(),
            ));
        }
        if let Some(confidence) = self.confidence {
            if !(0.0..=1.0).contains(&confidence) {
                return Err(ObservationError::Validation(format!(
                    "confidence fuera de rango: {confidence}"
                )));
            }
        }
        serde_json::from_str::<serde_json::Value>(&self.provenance_json)
            .map_err(|error| ObservationError::Validation(format!("provenance_json: {error}")))?;
        Ok(())
    }
}

fn check_day(label: &str, observed_on: &str) -> Result<(), ObservationError> {
    chrono::NaiveDate::parse_from_str(observed_on, "%Y-%m-%d")
        .map(|_| ())
        .map_err(|_| {
            ObservationError::Validation(format!("{label}: fecha invalida '{observed_on}'"))
        })
}

fn check_non_negative(label: &str, value: i32) -> Result<(), ObservationError> {
    if value < 0 {
        return Err(ObservationError::Validation(format!(
            "{label} no puede ser negativo: {value}"
        )));
    }
    Ok(())
}

#[derive(Clone, Debug)]
pub struct SleepSummaryInput {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub calm_minutes: i32,
    pub restless_minutes: i32,
    pub awake_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub bed_exit_count: i32,
    pub wake_count: i32,
    pub provenance: Provenance,
}

impl SleepSummaryInput {
    pub fn validate(&self) -> Result<(), ObservationError> {
        check_day("observed_on", &self.observed_on)?;
        self.provenance.validate()?;
        for (label, value) in [
            ("calm_minutes", self.calm_minutes),
            ("restless_minutes", self.restless_minutes),
            ("awake_minutes", self.awake_minutes),
            ("out_of_bed_minutes", self.out_of_bed_minutes),
            ("bed_exit_count", self.bed_exit_count),
            ("wake_count", self.wake_count),
        ] {
            check_non_negative(label, value)?;
        }
        // Salir de la cama implica haberse despertado. Al reves no: uno puede
        // despertarse y quedarse.
        if self.wake_count < self.bed_exit_count {
            return Err(ObservationError::Validation(format!(
                "wake_count ({}) no puede ser menor que bed_exit_count ({})",
                self.wake_count, self.bed_exit_count
            )));
        }
        let total = self.calm_minutes
            + self.restless_minutes
            + self.awake_minutes
            + self.out_of_bed_minutes;
        if total > MINUTES_IN_A_DAY {
            return Err(ObservationError::Validation(format!(
                "los minutos de sueno suman {total}, mas que un dia"
            )));
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct MobilitySummaryInput {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub in_bed_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub out_of_sight_minutes: i32,
    pub walking_minutes: i32,
    pub distance_meters: Option<f64>,
    pub transfer_count: i32,
    pub provenance: Provenance,
}

impl MobilitySummaryInput {
    pub fn validate(&self) -> Result<(), ObservationError> {
        check_day("observed_on", &self.observed_on)?;
        self.provenance.validate()?;
        for (label, value) in [
            ("in_bed_minutes", self.in_bed_minutes),
            ("out_of_bed_minutes", self.out_of_bed_minutes),
            ("out_of_sight_minutes", self.out_of_sight_minutes),
            ("walking_minutes", self.walking_minutes),
            ("transfer_count", self.transfer_count),
        ] {
            check_non_negative(label, value)?;
        }
        if let Some(distance) = self.distance_meters {
            if distance < 0.0 {
                return Err(ObservationError::Validation(
                    "distance_meters no puede ser negativo".to_owned(),
                ));
            }
        }
        // `walking_minutes` es un subconjunto de estar fuera de la cama, no un
        // sumando aparte.
        if self.walking_minutes > self.out_of_bed_minutes {
            return Err(ObservationError::Validation(format!(
                "walking_minutes ({}) no puede superar out_of_bed_minutes ({})",
                self.walking_minutes, self.out_of_bed_minutes
            )));
        }
        let total = self.in_bed_minutes + self.out_of_bed_minutes + self.out_of_sight_minutes;
        if total > MINUTES_IN_A_DAY {
            return Err(ObservationError::Validation(format!(
                "los minutos de movilidad suman {total}, mas que un dia"
            )));
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct BathroomSummaryInput {
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub visit_count: i32,
    pub night_visit_count: i32,
    pub assisted_count: i32,
    pub total_minutes: i32,
    pub longest_visit_minutes: i32,
    pub provenance: Provenance,
}

impl BathroomSummaryInput {
    pub fn validate(&self) -> Result<(), ObservationError> {
        check_day("observed_on", &self.observed_on)?;
        self.provenance.validate()?;
        for (label, value) in [
            ("visit_count", self.visit_count),
            ("night_visit_count", self.night_visit_count),
            ("assisted_count", self.assisted_count),
            ("total_minutes", self.total_minutes),
            ("longest_visit_minutes", self.longest_visit_minutes),
        ] {
            check_non_negative(label, value)?;
        }
        if self.night_visit_count > self.visit_count {
            return Err(ObservationError::Validation(format!(
                "night_visit_count ({}) no puede superar visit_count ({})",
                self.night_visit_count, self.visit_count
            )));
        }
        if self.assisted_count > self.visit_count {
            return Err(ObservationError::Validation(format!(
                "assisted_count ({}) no puede superar visit_count ({})",
                self.assisted_count, self.visit_count
            )));
        }
        if self.longest_visit_minutes > self.total_minutes {
            return Err(ObservationError::Validation(format!(
                "longest_visit_minutes ({}) no puede superar total_minutes ({})",
                self.longest_visit_minutes, self.total_minutes
            )));
        }
        if self.total_minutes > MINUTES_IN_A_DAY {
            return Err(ObservationError::Validation(format!(
                "total_minutes es {}, mas que un dia",
                self.total_minutes
            )));
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub struct SleepSummary {
    pub id: SummaryId,
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub calm_minutes: i32,
    pub restless_minutes: i32,
    pub awake_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub bed_exit_count: i32,
    pub wake_count: i32,
    pub provenance: Provenance,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl SleepSummary {
    /// Minutos en cama, calculados. No se persiste: es una suma de columnas y
    /// persistirla seria una via para que quede inconsistente.
    pub fn in_bed_minutes(&self) -> i32 {
        self.calm_minutes + self.restless_minutes + self.awake_minutes
    }

    /// Proporcion del tiempo en cama que fue sueno tranquilo. `None` cuando no
    /// hubo tiempo en cama: dividir por cero no es cero.
    pub fn efficiency(&self) -> Option<f64> {
        let in_bed = self.in_bed_minutes();
        if in_bed == 0 {
            return None;
        }
        Some(f64::from(self.calm_minutes) / f64::from(in_bed))
    }
}

#[derive(Clone, Debug)]
pub struct MobilitySummary {
    pub id: SummaryId,
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub in_bed_minutes: i32,
    pub out_of_bed_minutes: i32,
    pub out_of_sight_minutes: i32,
    pub walking_minutes: i32,
    pub distance_meters: Option<f64>,
    pub transfer_count: i32,
    pub provenance: Provenance,
    pub created_at: Instante,
    pub updated_at: Instante,
}

#[derive(Clone, Debug)]
pub struct BathroomSummary {
    pub id: SummaryId,
    pub source_record_id: String,
    pub resident_id: String,
    pub observed_on: String,
    pub visit_count: i32,
    pub night_visit_count: i32,
    pub assisted_count: i32,
    pub total_minutes: i32,
    pub longest_visit_minutes: i32,
    pub provenance: Provenance,
    pub created_at: Instante,
    pub updated_at: Instante,
}

impl BathroomSummary {
    /// Duracion promedio por visita. `None` sin visitas.
    pub fn average_visit_minutes(&self) -> Option<f64> {
        if self.visit_count == 0 {
            return None;
        }
        Some(f64::from(self.total_minutes) / f64::from(self.visit_count))
    }
}
