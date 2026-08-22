# Design: Engine Ports (Traits)

## Propósito

Definir los puertos (traits) que el engine usa para comunicarse con el mundo exterior.

## Concepto

El engine es puro — no tiene IO directo. Se comunica con el mundo a través de puertos (traits) que são implementados por capas externas.

## Puertos

### `EngineInput` (接收 eventos)

```rust
#[async_trait]
pub trait EngineInput: Send + Sync {
    /// Recibe un perception event del hub
    async fn push_perception_event(
        &self,
        event: PerceptionEvent,
    ) -> Result<Vec<SceneEvent>, EngineError>;
    
    /// Tick del super loop
    async fn tick(&self) -> Result<Vec<SceneEvent>, EngineError>;
}
```

### `EngineOutput` ( emit scene events)

```rust
#[async_trait]
pub trait EngineOutput: Send + Sync {
    /// Emite un scene event al hub
    async fn emit(&self, event: SceneEvent) -> Result<(), EngineError>;
}
```

### `TwinStore` (persistencia del twin)

```rust
#[async_trait]
pub trait TwinStore: Send + Sync {
    /// Cargar twin desde persistencia
    async fn load(&self) -> Result<DigitalTwin, EngineError>;
    
    /// Guardar twin en persistencia
    async fn save(&self, twin: &DigitalTwin) -> Result<(), EngineError>;
    
    /// Guardar un bed twin específico
    async fn save_bed(&self, bed: &BedTwin) -> Result<(), EngineError>;
}
```

### `CatalogStore` (catálogo de alarmas)

```rust
#[async_trait]
pub trait CatalogStore: Send + Sync {
    /// Obtener catálogo de alarmas
    async fn get_catalog(&self) -> Result<AlarmCatalog, EngineError>;
    
    /// Obtener reglas para un estado específico
    async fn rules_for_state(
        &self,
        state: &PersonState,
    ) -> Result<Vec<AlarmRule>, EngineError>;
}
```

### `ProfileStore` (perfiles de residentes)

```rust
#[async_trait]
pub trait ProfileStore: Send + Sync {
    /// Obtener perfil de alarma para un residente
    async fn profile_for_resident(
        &self,
        resident_id: &str,
    ) -> Result<Option<AlarmProfile>, EngineError>;
}
```

## Implementación In-Process

```rust
pub struct InProcessEngineInput {
    pool: DbPool,
}

pub struct InProcessEngineOutput {
    pool: DbPool,
}

pub struct InProcessTwinStore {
    pool: DbPool,
}

pub struct InProcessCatalogStore {
    catalog: Arc<AlarmCatalog>,
}

pub struct InProcessProfileStore {
    pool: DbPool,
}

#[async_trait]
impl EngineInput for InProcessEngineInput {
    async fn push_perception_event(
        &self,
        event: PerceptionEvent,
    ) -> Result<Vec<SceneEvent>, EngineError> {
        // Abrir conexión, procesar, retornar
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... procesar evento
        Ok(vec![])
    }
    
    async fn tick(&self) -> Result<Vec<SceneEvent>, EngineError> {
        // Abrir conexión, procesar tick, retornar
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... procesar tick
        Ok(vec![])
    }
}

#[async_trait]
impl EngineOutput for InProcessEngineOutput {
    async fn emit(&self, event: SceneEvent) -> Result<(), EngineError> {
        // Persistir scene event
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... persistir
        Ok(())
    }
}

#[async_trait]
impl TwinStore for InProcessTwinStore {
    async fn load(&self) -> Result<DigitalTwin, EngineError> {
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... cargar desde scene_states
        Ok(DigitalTwin::new())
    }
    
    async fn save(&self, twin: &DigitalTwin) -> Result<(), EngineError> {
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... guardar en scene_states
        Ok(())
    }
    
    async fn save_bed(&self, bed: &BedTwin) -> Result<(), EngineError> {
        let conn = self.pool.get().map_err(|e| EngineError::Pool(e.to_string()))?;
        // ... guardar bed específico
        Ok(())
    }
}
```

## Wiring en AppState

```rust
pub struct AppState {
    pub engine: Arc<Engine<InProcessEngineInput, InProcessEngineOutput>>,
    pub twin_store: Arc<InProcessTwinStore>,
    // ... otros stores
}

impl AppState {
    pub async fn new(pool: DbPool) -> Result<Self, AppInitError> {
        let catalog = load_alarm_catalog()?;
        
        let input = Arc::new(InProcessEngineInput::new(pool.clone()));
        let output = Arc::new(InProcessEngineOutput::new(pool.clone()));
        let twin_store = Arc::new(InProcessTwinStore::new(pool.clone()));
        let catalog_store = Arc::new(InProcessCatalogStore::new(catalog.clone()));
        let profile_store = Arc::new(InProcessProfileStore::new(pool.clone()));
        
        let engine = Arc::new(Engine::new(
            input,
            output,
            twin_store.clone(),
            catalog_store,
            profile_store,
        ));
        
        // Cargar twin desde persistencia
        engine.load_twin().await?;
        
        Ok(Self { engine, twin_store })
    }
}
```

## Invariantes

1. Los puertos son traits (no concreciones)
2. El engine no tiene IO directo
3. Las implementaciones son testables (mocks)
4. El engine puede funcionar con diferentes backends (SQLite, PostgreSQL, etc.)
5. Los puertos son async (para soportar IO)
