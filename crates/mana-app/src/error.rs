use std::{collections::BTreeMap, fmt};

use ctx_auditoria::{AuditDomainError, AuditError};
use ctx_cobertura::CoberturaError;
use ctx_cuidado::CuidadoError;
use ctx_historia::HistoriaError;
use ctx_poblacion::PoblacionError;
use ctx_politica::PoliticaError as CtxPoliticaError;
use ctx_residencia::ResidenceError;
use ctx_streams::StreamsError;
use ctx_vigilancia::VigilanciaError;
use diesel::result::Error as DieselError;
use mana_kernel::Fallo;

#[derive(Clone, Debug)]
pub struct AppFailure {
    pub fallo: Fallo,
    pub message: String,
    pub fields: Option<BTreeMap<String, String>>,
}

impl AppFailure {
    pub fn new(fallo: Fallo, message: impl Into<String>) -> Self {
        Self {
            fallo,
            message: message.into(),
            fields: None,
        }
    }

    pub fn validation(message: impl Into<String>, field: Option<&str>) -> Self {
        let mut failure = Self::new(Fallo::ValidationError, message);
        if let Some(field) = field {
            failure.fields = Some([(field.to_owned(), "invalid".to_owned())].into());
        }
        failure
    }

    pub fn with_fields(mut self, fields: impl IntoIterator<Item = (String, String)>) -> Self {
        self.fields = Some(fields.into_iter().collect());
        self
    }
}

impl fmt::Display for AppFailure {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}: {}", self.fallo, self.message)
    }
}

impl std::error::Error for AppFailure {}

impl From<AuditError> for AppFailure {
    fn from(error: AuditError) -> Self {
        match error {
            AuditError::Domain(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de auditoria");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<AuditDomainError> for AppFailure {
    fn from(error: AuditDomainError) -> Self {
        Self::from(AuditError::from(error))
    }
}

impl From<DieselError> for AppFailure {
    fn from(error: DieselError) -> Self {
        tracing::error!(error = %error, "fallo en la transaccion SQLite");
        Self::new(Fallo::InternalError, "No se pudo completar la operacion")
    }
}

impl From<ResidenceError> for AppFailure {
    fn from(error: ResidenceError) -> Self {
        match error {
            ResidenceError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            ResidenceError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            ResidenceError::RoomNotFound { room_id } => {
                Self::new(Fallo::NotFound, "Habitacion no encontrada")
                    .with_fields([(room_id.clone(), room_id)])
            }
            ResidenceError::DuplicatePlanogramRoom { room_id } => {
                Self::new(Fallo::Conflict, "Habitacion duplicada en el planograma")
                    .with_fields([(room_id.clone(), room_id)])
            }
            ResidenceError::Estructura(error) => Self::validation(error.to_string(), None),
            ResidenceError::Planograma(error) => Self::validation(error.to_string(), None),
            ResidenceError::Privacidad(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de residencia");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<PoblacionError> for AppFailure {
    fn from(error: PoblacionError) -> Self {
        use ctx_poblacion::AsignacionesError;
        match error {
            PoblacionError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            PoblacionError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            PoblacionError::Residentes(ctx_poblacion::ResidentesError::AlreadyDischarged) => {
                Self::new(Fallo::Conflict, "El residente ya esta egresado")
            }
            PoblacionError::Residentes(error) => Self::validation(error.to_string(), None),
            PoblacionError::Atributos(error) => Self::validation(error.to_string(), None),
            PoblacionError::Asignaciones(AsignacionesError::FreeBed) => {
                Self::new(Fallo::Conflict, "La cama no tiene una asignacion abierta")
            }
            PoblacionError::Asignaciones(AsignacionesError::AlreadyClosed) => {
                Self::new(Fallo::Conflict, "La asignacion ya esta cerrada")
            }
            PoblacionError::Asignaciones(AsignacionesError::OverlappingInterval { .. }) => {
                Self::validation(
                    "El intervalo de asignacion se solapa con el historial",
                    None,
                )
            }
            PoblacionError::Asignaciones(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de poblacion");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<CoberturaError> for AppFailure {
    fn from(error: CoberturaError) -> Self {
        match error {
            CoberturaError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            CoberturaError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            CoberturaError::CrossFacility => {
                Self::validation("El grupo pertenece a otra facility", None)
            }
            CoberturaError::NoShifts => {
                Self::validation("La facility no tiene turnos definidos", None)
            }
            CoberturaError::Grupos(error) => Self::validation(error.to_string(), None),
            CoberturaError::Turnos(error) => Self::validation(error.to_string(), None),
            CoberturaError::Cobertura(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de cobertura");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<CuidadoError> for AppFailure {
    fn from(error: CuidadoError) -> Self {
        match error {
            CuidadoError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            CuidadoError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            CuidadoError::Rondas(ctx_cuidado::RondasError::AlreadyInProgress) => Self::new(
                Fallo::Conflict,
                "Ya existe una ronda en progreso para este ala",
            ),
            CuidadoError::Rondas(ctx_cuidado::RondasError::EmptyRound) => {
                Self::validation("No se puede crear una ronda sin residentes", None)
            }
            CuidadoError::Rondas(ctx_cuidado::RondasError::PendingTasks) => Self::new(
                Fallo::Conflict,
                "No se puede completar una ronda con tareas pendientes",
            ),
            CuidadoError::Rondas(ctx_cuidado::RondasError::AlreadyCompleted) => {
                Self::new(Fallo::Conflict, "La ronda ya fue completada o cancelada")
            }
            CuidadoError::Rondas(error) => Self::validation(error.to_string(), None),
            CuidadoError::Notas(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de cuidado");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<HistoriaError> for AppFailure {
    fn from(error: HistoriaError) -> Self {
        match error {
            HistoriaError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            HistoriaError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            HistoriaError::Detecciones(error) => Self::validation(error.to_string(), None),
            HistoriaError::Revisiones(error) => Self::validation(error.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de historia");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<CtxPoliticaError> for AppFailure {
    fn from(error: CtxPoliticaError) -> Self {
        match error {
            CtxPoliticaError::Conflict(msg) => Self::new(Fallo::Conflict, msg),
            CtxPoliticaError::NotFound(msg) => Self::new(Fallo::NotFound, msg),
            CtxPoliticaError::Validation(msg) => Self::validation(msg, None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de politica");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<VigilanciaError> for AppFailure {
    fn from(error: VigilanciaError) -> Self {
        match error {
            VigilanciaError::Conflict(msg) => Self::new(Fallo::Conflict, msg),
            VigilanciaError::NotFound(msg) => Self::new(Fallo::NotFound, msg),
            VigilanciaError::Validation(msg) => Self::validation(msg, None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de vigilancia");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<mana_observation::ObservationError> for AppFailure {
    fn from(error: mana_observation::ObservationError) -> Self {
        use mana_observation::ObservationError as Error;
        match error {
            Error::Validation(message) => Self::validation(message, None),
            Error::NotFound => Self::new(Fallo::NotFound, "observacion no encontrada"),
            other => {
                tracing::error!(error = %other, "fallo en el subsistema de observacion");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}

impl From<StreamsError> for AppFailure {
    fn from(error: StreamsError) -> Self {
        match error {
            StreamsError::Conflict => Self::new(Fallo::Conflict, "El recurso ya existe"),
            StreamsError::NotFound => Self::new(Fallo::NotFound, "Recurso no encontrado"),
            StreamsError::Streams(e) => Self::validation(e.to_string(), None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de streams");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}
