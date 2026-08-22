use std::{collections::BTreeSet, sync::Arc};

use ctx_auditoria::{run_migrations as run_audit_migrations, AuditError, AuditStore};
use ctx_cobertura::{run_migrations as run_cobertura_migrations, CoberturaError, CoverageStore};
use ctx_cuidado::{run_migrations as run_cuidado_migrations, CareStore, CuidadoError};
use ctx_evidence::{run_migrations as run_evidence_migrations, EvidenceError, EvidenceStore};
use ctx_historia::{run_migrations as run_historia_migrations, HistoriaError, HistoryStore};
use ctx_identidad::{run_migrations, IdentityError, IdentityStore};
use ctx_poblacion::{run_migrations as run_poblacion_migrations, PoblacionError, PopulationStore};
use ctx_politica::{
    run_migrations as run_politica_migrations, AlarmCatalog, PolicyStore, PoliticaError,
};
use mana_engine_v2::DigitalTwin;
use ctx_residencia::{run_migrations as run_residence_migrations, ResidenceError, ResidenceStore};
use ctx_streams::{run_migrations as run_streams_migrations, StreamsError, StreamsStore};
use ctx_vigilancia::{
    run_migrations as run_vigilancia_migrations, VigilanciaError, VigilanciaStore,
};
use diesel::{Connection, SqliteConnection};
use mana_nats::{NatsError, SharedBroker, create_broker};
use mana_observation::{
    run_migrations as run_observation_migrations, ObservationError, ObservationStore,
};
use mana_storage::{build_pool, DbPool, StorageError};
use thiserror::Error;
use tokio::sync::RwLock;

#[derive(Debug, Error)]
pub enum AppInitError {
    #[error(transparent)]
    Identity(#[from] IdentityError),
    #[error(transparent)]
    Audit(#[from] AuditError),
    #[error(transparent)]
    Residence(#[from] ResidenceError),
    #[error(transparent)]
    Poblacion(#[from] PoblacionError),
    #[error(transparent)]
    Cobertura(#[from] CoberturaError),
    #[error(transparent)]
    Cuidado(#[from] CuidadoError),
    #[error(transparent)]
    Historia(#[from] HistoriaError),
    #[error(transparent)]
    Politica(#[from] PoliticaError),
    #[error(transparent)]
    Vigilancia(#[from] VigilanciaError),
    #[error(transparent)]
    Observation(#[from] ObservationError),
    #[error(transparent)]
    Streams(#[from] StreamsError),
    #[error(transparent)]
    Evidence(#[from] EvidenceError),
    #[error(transparent)]
    Storage(#[from] StorageError),
    #[error("no se pudo cargar el catalogo de alarmas: {0}")]
    Catalog(String),
    #[error("NATS connection failed: {0}")]
    Nats(#[from] NatsError),
}

/// Los stores de todos los contextos, agrupados para cruzar una transaccion.
///
/// Existe para que la firma de [`AppState::transaction`] no sea una tupla fija:
/// agregar un contexto es agregar un campo, y ningun caso de uso existente se
/// entera. La version anterior tomaba cinco stores posicionales, de los que la
/// mayoria de los call sites ignoraba al menos uno, y dejaba fuera de toda
/// transaccion a los cinco contextos incorporados despues de F3.
// Los cinco ultimos todavia no tienen caso de uso transaccional: existen para
// que incorporar uno no vuelva a cambiar la firma de `transaction`. El primer
// consumidor sera Observacion, que necesita limpiar la proyeccion de una cama
// en la misma transaccion que cambia su ocupante.
#[allow(dead_code)]
#[derive(Clone)]
pub(crate) struct Stores {
    pub(crate) identity: IdentityStore,
    pub(crate) audit: AuditStore,
    pub(crate) residence: ResidenceStore,
    pub(crate) poblacion: PopulationStore,
    pub(crate) cobertura: CoverageStore,
    pub(crate) cuidado: CareStore,
    pub(crate) history: HistoryStore,
    pub(crate) policy: PolicyStore,
    pub(crate) vigilancia: VigilanciaStore,
    pub(crate) observation: ObservationStore,
    pub(crate) streams: StreamsStore,
    pub(crate) evidence: EvidenceStore,
    pub(crate) engine_v2: Arc<RwLock<DigitalTwin>>,
}

#[derive(Clone)]
pub struct AppState {
    pub(crate) identity: IdentityStore,
    pub(crate) audit: AuditStore,
    pub(crate) residence: ResidenceStore,
    pub(crate) poblacion: PopulationStore,
    pub(crate) cobertura: CoverageStore,
    pub(crate) cuidado: CareStore,
    pub(crate) history: HistoryStore,
    pub(crate) policy: PolicyStore,
    pub(crate) vigilancia: VigilanciaStore,
    pub observation: ObservationStore,
    pub(crate) streams: StreamsStore,
    pub evidence_store: EvidenceStore,
    pub(crate) catalog: Arc<AlarmCatalog>,
    pub(crate) enabled_capabilities: Arc<BTreeSet<String>>,
    pub engine_v2: Arc<RwLock<DigitalTwin>>,
    /// NATS JetStream broker for event-driven communication (optional)
    pub nats: Option<SharedBroker>,
}

impl AppState {
    /// Carga el catalogo del disco y **falla si no esta**. Es el constructor de
    /// produccion.
    pub fn new(database_url: &str) -> Result<Self, AppInitError> {
        Self::with_catalog(database_url, load_catalog()?)
    }

    /// Igual que [`AppState::new`] pero con el catalogo dado. Para tests que no
    /// ejercitan politica de alarmas y quieren decirlo en el call site.
    pub fn with_catalog(database_url: &str, catalog: AlarmCatalog) -> Result<Self, AppInitError> {
        let pool = build_pool(database_url)?;
        let state = Self::from_pool(pool, catalog);
        state.migrate()?;
        Ok(state)
    }

    /// El catalogo se recibe, no se carga: que este vacio tiene que ser una
    /// decision visible en el call site. `new` lo carga del disco y falla si no
    /// esta; los tests pasan `AlarmCatalog::empty()` a proposito.
    pub fn from_pool(pool: DbPool, catalog: AlarmCatalog) -> Self {
        let engine_v2 = Arc::new(RwLock::new(DigitalTwin::new()));
        Self {
            identity: IdentityStore::new(pool.clone()),
            audit: AuditStore::new(pool.clone()),
            residence: ResidenceStore::new(pool.clone()),
            poblacion: PopulationStore::new(pool.clone()),
            cobertura: CoverageStore::new(pool.clone()),
            cuidado: CareStore::new(pool.clone()),
            history: HistoryStore::new(pool.clone()),
            policy: PolicyStore::new(pool.clone()),
            vigilancia: VigilanciaStore::new(pool.clone()),
            observation: ObservationStore::new(pool.clone()),
            streams: StreamsStore::new(pool.clone()),
            evidence_store: EvidenceStore::new(pool),
            catalog: Arc::new(catalog),
            enabled_capabilities: Arc::new(crate::identidad::enabled_capabilities_from_env()),
            engine_v2,
            nats: None,
        }
    }

    /// Create AppState with NATS connection
    pub async fn with_nats(
        database_url: &str,
        nats_url: &str,
    ) -> Result<Self, AppInitError> {
        let catalog = load_catalog()?;
        let pool = build_pool(database_url)?;
        let engine_v2 = Arc::new(RwLock::new(DigitalTwin::new()));
        let nats = create_broker(nats_url).await?;
        
        let state = Self {
            identity: IdentityStore::new(pool.clone()),
            audit: AuditStore::new(pool.clone()),
            residence: ResidenceStore::new(pool.clone()),
            poblacion: PopulationStore::new(pool.clone()),
            cobertura: CoverageStore::new(pool.clone()),
            cuidado: CareStore::new(pool.clone()),
            history: HistoryStore::new(pool.clone()),
            policy: PolicyStore::new(pool.clone()),
            vigilancia: VigilanciaStore::new(pool.clone()),
            observation: ObservationStore::new(pool.clone()),
            streams: StreamsStore::new(pool.clone()),
            evidence_store: EvidenceStore::new(pool),
            catalog: Arc::new(catalog),
            enabled_capabilities: Arc::new(crate::identidad::enabled_capabilities_from_env()),
            engine_v2,
            nats: Some(nats),
        };
        state.migrate()?;
        Ok(state)
    }

    /// El pool del SoR: quien necesita abrir su propia conexión (p.ej. el
    /// barrido del engine) lo toma de acá.
    pub fn pool(&self) -> DbPool {
        self.identity.pool().clone()
    }

    /// El engine v2 (FSM puro + Digital Twin).
    pub fn engine_v2(&self) -> Arc<RwLock<DigitalTwin>> {
        self.engine_v2.clone()
    }

    /// Get the NATS broker (if connected)
    pub fn nats(&self) -> Option<&SharedBroker> {
        self.nats.as_ref()
    }

    pub(crate) fn stores(&self) -> Stores {
        Stores {
            identity: self.identity.clone(),
            audit: self.audit.clone(),
            residence: self.residence.clone(),
            poblacion: self.poblacion.clone(),
            cobertura: self.cobertura.clone(),
            cuidado: self.cuidado.clone(),
            history: self.history.clone(),
            policy: self.policy.clone(),
            vigilancia: self.vigilancia.clone(),
            observation: self.observation.clone(),
            streams: self.streams.clone(),
            evidence: self.evidence_store.clone(),
            engine_v2: self.engine_v2.clone(),
        }
    }

    pub fn migrate(&self) -> Result<(), AppInitError> {
        run_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_audit_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_residence_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_poblacion_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_cobertura_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_cuidado_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_historia_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_politica_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_vigilancia_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_observation_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_streams_migrations(self.identity.pool()).map_err(AppInitError::from)?;
        run_evidence_migrations(self.identity.pool()).map_err(AppInitError::from)
    }

    pub(crate) async fn transaction<T, F>(&self, operation: F) -> Result<T, crate::AppFailure>
    where
        T: Send + 'static,
        F: FnOnce(&mut SqliteConnection, &Stores) -> Result<T, crate::AppFailure> + Send + 'static,
    {
        let pool = self.identity.pool().clone();
        let stores = self.stores();
        tokio::task::spawn_blocking(move || {
            let mut connection = pool.get().map_err(|error| {
                crate::AppFailure::new(
                    mana_kernel::Fallo::InternalError,
                    format!("No se pudo obtener una conexion SQLite: {error}"),
                )
            })?;
            connection.transaction(|connection| operation(connection, &stores))
        })
        .await
        .map_err(|error| {
            tracing::error!(error = %error, "transaccion SQLite abortada");
            crate::AppFailure::new(
                mana_kernel::Fallo::InternalError,
                "No se pudo completar la operacion",
            )
        })?
    }
}

/// Un catalogo ausente o ilegible **detiene el arranque**.
///
/// La version anterior logueaba y seguia con un catalogo vacio. Para un
/// contexto de nucleo que decide que observacion genera una alarma, eso
/// significa que no suena nada y nadie se entera — exactamente la clase de
/// falla silenciosa que este sistema existe para eliminar.
fn load_catalog() -> Result<AlarmCatalog, AppInitError> {
    let catalog = AlarmCatalog::load()
        .map_err(|error| AppInitError::Catalog(error.to_string()))?;
    tracing::info!(version = %catalog.version, "alarm catalog loaded");
    Ok(catalog)
}
