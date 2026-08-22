# Guia de onboarding: implementar un sprint de contexto

> Playbook definitivo basado en F1-F6. Sirve como skill para las sesiones de
> implementacion de F7 (vigilancia), F8 (observacion) y F9 (plataforma).

## 0. Resumen ejecutivo

Cada sprint sigue el mismo patron de 7 fases:

```text
Fase 1: ctx-* crate        (dominio + persistencia + tests)
Fase 2: mana-app           (commands + views + state + error)
Fase 3: mana-http          (handlers + rutas.toml + main.rs)
Fase 4: SDK + CLI + scene  (cliente + comandos + blueprint.json)
Fase 5: Regression         (build release, hub fresh, 7+ scenes, CLI)
Fase 6: Docs               (contrato YAML, casos-uso, modelo-dominio, modulos)
Fase 7: Commit             (git add -A, commit, verify)
```

**Regla de oro**: No pasar de fase N a N+1 hasta que la fase N compile y pase
tests. Cada fase es incremental y verificable.

## 1. Antes de empezar: leer el spec

### Fuentes normativas (en orden)

1. `docs/contextos/ctx-*.md` - Modelo de dominio, invariantes, tablas, API
2. `docs/reference/data-model.md` - DDL completo del contexto
3. `rutas.toml` - Rutas existentes (las que `sirve = "node"` son candidatas)
4. `docs/contrato/openapi.yaml` - Contrato HTTP actual

### Preguntas a responder antes de codear

- [ ] Cuantos agregados tiene el contexto?
- [ ] Cuantas tablas necesito?
- [ ] Cuantas rutas voy a flippear de node a rust?
- [ ] Hay endpoints internos (requieren auth especial)?
- [ ] Necesito coordinacion cross-context en mana-app?
- [ ] Que read models compongo desde mana-app?

## 2. Fase 1: ctx-* crate

### Crear estructura

```bash
mkdir -p crates/ctx-*/src/subdominio crates/ctx-*/migrations/NNNN_nombre
```

### Archivos a crear (en orden)

#### 2.1 Cargo.toml

```toml
[package]
name = "ctx-*"
version.workspace = true
edition.workspace = true
rust-version.workspace = true

[dependencies]
# Tipicos (verificar cuales necesita):
base64.workspace = true
chrono.workspace = true
diesel.workspace = true
diesel_migrations.workspace = true
mana-kernel.workspace = true
mana-storage.workspace = true
rand.workspace = true
serde.workspace = true
serde_json.workspace = true
thiserror.workspace = true
# Si necesita TOML:
toml.workspace = true
```

#### 2.2 Migrations

`up.sql`:
```sql
CREATE TABLE nombre (
    id               TEXT PRIMARY KEY NOT NULL,
    -- campos...
    created_at       TEXT NOT NULL
);
-- Indices parciales para queries comunes
CREATE INDEX idx_nombre_field ON tabla(field) WHERE condition;
```

`down.sql`:
```sql
DROP INDEX IF EXISTS idx_nombre_field;
DROP TABLE IF EXISTS nombre;
```

#### 2.3 schema.rs

```rust
diesel::table! {
    nombre (id) {
        id -> Text,
        campo -> Text,
        created_at -> Text,
    }
}
```

#### 2.4 error.rs

```rust
use mana_storage::StorageError;

#[derive(Debug, thiserror::Error)]
pub enum ContextoError {
    #[error("conflicto: {0}")]
    Conflict(String),
    #[error("no encontrado: {0}")]
    NotFound(String),
    #[error("error de validacion: {0}")]
    Validation(String),
    #[error(transparent)]
    Storage(#[from] StorageError),
    #[error(transparent)]
    Diesel(#[from] diesel::result::Error),
}
```

#### 2.5 Dominio (mod.rs)

```rust
#[derive(Debug, Clone)]
pub struct Agregado {
    pub id: Id<Agregado>,
    // campos...
    pub created_at: Instante,
}

// Enums con parse/str
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Status { Open, Closed }

impl Status {
    pub fn as_str(&self) -> &'static str { ... }
    pub fn parse(value: &str) -> Result<Self, Error> { ... }
}
```

#### 2.6 Repo trait

```rust
pub trait AgregadoRepo {
    fn get(&mut self, id: &str) -> Result<Agregado, Error>;
    fn create_in_transaction(&mut self, input: Input) -> Result<Agregado, Error>;
    fn list_by_resident(&mut self, resident_id: &str) -> Result<Vec<Agregado>, Error>;
}
```

#### 2.7 SQLite impl

```rust
impl AgregadoRepo for SqliteConnection {
    fn get(&mut self, id: &str) -> Result<Agregado, Error> {
        let row: Row = tabla::table.find(id).first(self)?;
        row_to_agregado(row)
    }
    // ...
}
```

#### 2.8 lib.rs

```rust
mod schema;
mod error;
mod subdominio;

pub use error::ContextoError;
pub use subdominio::{Agregado, Input, Status};
pub use mana_storage::DbPool;

use diesel::prelude::*;
use diesel_migrations::{embed_migrations, EmbeddedMigrations};

pub const MIGRATIONS: EmbeddedMigrations = embed_migrations!();

#[derive(Clone)]
pub struct ContextStore { pool: DbPool }

impl ContextStore {
    pub fn new(pool: DbPool) -> Self { Self { pool } }
    fn connection(&self) -> Result<DbConnection, ContextoError> { ... }
    // Metodos publicos que delegan al repo
}

// Tests
#[cfg(test)]
mod tests {
    use super::*;
    // ~6-12 tests de dominio
}
```

### Verificacion fase 1

```bash
cargo test -p ctx-*           # Todos los tests del crate
cargo clippy -p ctx-*         # 0 warnings
```

## 3. Fase 2: mana-app integration

### 3.1 Agregar dependencia

En `crates/mana-app/Cargo.toml`:
```toml
ctx-* = { path = "../ctx-*" }
```

### 3.2 Crear src/contexto.rs

```rust
use ctx_*::{Agregado, ContextStore, Input};

use crate::{error::AppFailure, identidad::required_token, state::AppState};

// Commands (lo que recibe el handler)
#[derive(Clone, Debug)]
pub struct CreateAgregadoCommand { ... }

// Views (lo que devuelve el handler)
#[derive(Clone, Debug, serde::Serialize)]
pub struct AgregadoView { ... }

fn view(agregado: Agregado) -> AgregadoView { ... }

impl AppState {
    pub async fn create_agregado(
        &self, token: &str, command: CreateAgregadoCommand
    ) -> Result<AgregadoView, AppFailure> {
        let actor = required_token(token)?;
        let agregado = self.context.create(...)?;
        Ok(view(agregado))
    }
}
```

### 3.3 Actualizar state.rs

```rust
use ctx_*::{run_migrations as run_*_migrations, *Error, *Store};

// En AppInitError:
#[error(transparent)]
*(#[from] *Error),

// En AppState:
pub(crate) context: *Store,

// En from_pool:
context: *Store::new(pool.clone()),

// En migrate:
run_*_migrations(self.identity.pool()).map_err(AppInitError::from)?;
```

### 3.4 Actualizar error.rs

```rust
use ctx_*::*Error as Ctx*Error;

impl From<Ctx*Error> for AppFailure {
    fn from(error: Ctx*Error) -> Self {
        match error {
            Ctx*Error::Conflict(msg) => Self::new(Fallo::Conflict, msg),
            Ctx*Error::NotFound(msg) => Self::new(Fallo::NotFound, msg),
            Ctx*Error::Validation(msg) => Self::validation(msg, None),
            other => {
                tracing::error!(error = %other, "fallo en el contexto de *");
                Self::new(Fallo::InternalError, "No se pudo completar la operacion")
            }
        }
    }
}
```

### 3.5 Actualizar lib.rs

```rust
pub mod contexto;
pub use contexto::{CreateCommand, View, ...};
```

### Verificacion fase 2

```bash
cargo check -p mana-app
```

## 4. Fase 3: mana-http handlers

### 4.1 Crear src/contexto.rs

```rust
use std::{collections::HashMap, sync::Arc};
use axum::{body::Body, http::{Request, StatusCode}, response::Response};
use mana_app::AppState;
use crate::{response::{failure_response, json_body, json_value}, rust_handler, RustHandler};

pub fn contexto_handlers(app: Arc<AppState>) -> HashMap<String, RustHandler> {
    let mut handlers = HashMap::new();
    register(&mut handlers, "ruta.id", app.clone(), handler_fn);
    handlers
}

fn register(handlers: &mut HashMap<String, RustHandler>, id: &str,
    app: Arc<AppState>,
    handler: fn(Arc<AppState>, Request<Body>) -> HandlerFuture) {
    handlers.insert(id.to_owned(),
        rust_handler(move |request| handler(app.clone(), request)));
}

type HandlerFuture = std::pin::Pin<Box<dyn std::future::Future<Output = Response> + Send>>;

fn path_part(request: &Request<Body>, index: usize) -> &str {
    request.uri().path().split('/').nth(index).unwrap_or("")
}

fn authorization_token(headers: &axum::http::HeaderMap) -> String {
    headers.get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or("")
        .to_owned()
}

// GET /api/v1/recurso
fn list_handler(app: Arc<AppState>, request: Request<Body>) -> HandlerFuture {
    let token = authorization_token(request.headers());
    Box::pin(async move {
        match app.list(&token).await {
            Ok(data) => json_value(StatusCode::OK, &serde_json::json!({ "items": data })),
            Err(failure) => failure_response(failure),
        }
    })
}
```

### 4.2 Actualizar lib.rs

```rust
mod contexto;  // agregar mod
pub use contexto::contexto_handlers;  // agregar pub use
```

### 4.3 Actualizar main.rs

```rust
use mana_http::{..., contexto_handlers, ...};

// En main():
handlers.extend(contexto_handlers(state));
```

### 4.4 Actualizar rutas.toml

```toml
[[ruta]]
id = "recurso.list.get"
metodo = "GET"
patron = "/api/v1/recurso"
contexto = "contexto"
sirve = "rust"   # cambiar de "node"
```

### 4.5 Actualizar tests.rs

- Importar `contexto_handlers`
- Agregar `handlers.extend(contexto_handlers(state.clone()))` en los test routers
- Actualizar `routes.rust()` count

### Verificacion fase 3

```bash
cargo check --workspace
cargo test -p mana-http
```

## 5. Fase 4: SDK + CLI + scene

### 5.1 SDK (src/contexto.rs)

```rust
use reqwest::Method;
use serde::{Deserialize, Serialize};
use crate::transport::{ApiResponse, ManaClient, ManaError};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Item { pub id: String, ... }

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ItemsResponse { pub items: Vec<Item> }

impl ManaClient {
    pub async fn list_items(&self) -> Result<ApiResponse<ItemsResponse>, ManaError> {
        self.request(Method::GET, "/api/v1/recurso").await
    }
}
```

En `lib.rs`:
```rust
pub mod contexto;
pub use contexto::{Item, ItemsResponse, ...};
```

### 5.2 CLI (commands/contexto.rs)

```rust
use crate::{cli::CliError, output};

pub async fn dispatch(options: &Options) -> Result<(), CliError> {
    match options.verb() {
        "listar" => {
            let client = crate::authenticated_client(options)?;
            let response = client.list_items().await?;
            let data = crate::response_data(response)?;
            let rows: Vec<Vec<String>> = data.items.iter()
                .map(|i| vec![i.id.clone(), i.name.clone()])
                .collect();
            output::print_table(&["id", "nombre"], &rows);
            Ok(())
        }
        _ => Err(CliError::Usage(format!(
            "verbo desconocido para contexto: {}", options.verb()
        ))),
    }
}
```

En `commands/mod.rs`:
```rust
pub mod contexto;
// En dispatch():
"contexto" => contexto::dispatch(&options).await,
```

En `cli.rs`:
```rust
("contexto", &[
    CommandSpec { verb: "listar", description: "listar recursos", options: &["base-url", "token"] },
]),
```

### 5.3 Blueprint scene

```json
{
  "meta": { "id": "contexto-blueprint", "title": "...", "description": "..." },
  "context": "contexto",
  "commands": [
    { "name": "login", "action": "login", "args": { "username": "gaston", "password_default": "gaston-demo" }, "capture": "login" },
    { "name": "setup", "action": "...", ... },
    { "name": "CTX-01 crear", "action": "custom", "args": { "method": "POST", "path": "/api/v1/...", "body": { ... } }, "capture": "item", "status": 201 },
    { "name": "CTX-02 listar", "action": "custom", "args": { "method": "GET", "path": "/api/v1/..." }, "assert": { "items.length": 1 } },
    { "name": "logout", "action": "logout", "args": {} }
  ]
}
```

### Verificacion fase 4

```bash
cargo build -p mana-cli --release
mana scene validate --file crates/mana-sdk/scenes/contexto-blueprint.json
```

## 6. Fase 5: Regression

### 6.1 Build release

```bash
cargo build --release
```

### 6.2 Hub fresh

```bash
ps aux | grep mana-hub | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null
rm -f /tmp/hub-N.sqlite
MANA_HUB_DATABASE_URL=/tmp/hub-N.sqlite MANA_HUB_SEED_DEMO=1 \
    ~/.cache/cargo-target/release/mana-hub > /tmp/hub-N.log 2>&1 &
sleep 3
curl -s http://127.0.0.1:8780/health
curl -s http://127.0.0.1:8780/__hub/rutas  # verificar rust count
```

### 6.3 Scenes (TODAS, no solo la nueva)

```bash
for scene in identidad-smoke residencia-blueprint poblacion-blueprint \
    cobertura-blueprint cuidado-blueprint historia-blueprint \
    politica-blueprint contexto-blueprint; do
    result=$(~/.cache/cargo-target/release/mana scene load \
        --file "crates/mana-sdk/scenes/${scene}.json" \
        --base-url http://127.0.0.1:8780 2>&1)
    if echo "$result" | grep -q '"Error"'; then
        echo "FAIL $scene: $(echo "$result" | grep 'Error:' | head -1)"
    else
        echo "PASS $scene"
    fi
done
```

### 6.4 CLI smoke

```bash
~/.cache/cargo-target/release/mana identidad login --username gaston --password gaston-demo
~/.cache/cargo-target/release/mana contexto listar
```

### 6.5 Tests + clippy + fmt

```bash
cargo test --workspace 2>&1 | grep 'test result' | awk '{sum += $4} END {print sum " tests"}'
cargo clippy --workspace --all-targets  # 0 warnings
cargo fmt --all --check                 # 0 diffs
```

### Verificacion fase 5

```bash
# Todo debe pasar:
# - N tests (esperados)
# - 0 clippy warnings
# - 0 fmt diffs
# - Todas las escenas PASS
# - CLI funciona
```

## 7. Fase 6: Docs

### 7.1 Contrato YAML

Crear `docs/contrato/modulos/contexto.yaml` con paths y schemas.

Actualizar `docs/contrato/openapi.yaml`:
- Tag
- Path refs
- Schema refs

### 7.2 Docs funcionales

- `docs/funcional/casos-uso/ctx-contexto.md` (CASOS con precondiciones, flujo, postcondiciones)
- `docs/funcional/modelo-dominio/ctx-contexto.md` (objetos, invariantes, tablas)
- Actualizar `docs/funcional/modulos/mana-app.md` (tabla de casos coordinados)
- Actualizar `docs/funcional/modulos/mana-sdk.md` (cliente + escenas)
- Actualizar `docs/funcional/modulos/mana-cli.md` (comandos)
- Actualizar `docs/funcional/README.md` (tabla de estado)
- Actualizar `docs/contrato/README.md` (lista de modulos)

### 7.3 Proyecto

- Actualizar `docs/reference/data-model.md` (agregar tablas del contexto)
- Actualizar `HANDOFF.md` (tests, siguiente fase)

## 8. Fase 7: Commit

```bash
git add -A
git diff --cached --stat  # verificar archivos
git commit -m "feat(hub): F* contexto - resumen corto

Detalle de:
- Dominio: agregados, invariantes, tests
- Persistencia: migraciones, repo
- Integracion: app, http, rutas
- SDK + CLI + scene
- Contrato + docs

Tests: N/N workspace, clippy 0, fmt OK"
```

## 9. Errores comunes y soluciones

| Error | Causa | Solucion |
|---|---|---|
| `routes.rust() == N` | se agrego una ruta | Actualizar el assert en tests.rs |
| `database is locked` | hub anterior no murio | `kill -9` antes de restart |
| `502 Bad Gateway` | ruta sigue en Node | Verificar `sirve = "rust"` en rutas.toml |
| `unresolved import base64` | falta dep en Cargo.toml | Agregar `base64.workspace = true` |
| `? could not convert error` | falta `From<Error>` | Agregar impl en error.rs |
| `cannot borrow as mutable` | diesel `&mut` | Usar `&mut self` en repo trait |
| `Serialize not implemented` | falta derive | Agregar `#[derive(serde::Serialize)]` |
| `referencia no capturada` | template mal formado | Usar `{{capture.field}}` |
| hub binary viejo | rutas.toml no recompilado | `cargo build -p mana-hub --release` |
| `0 clippy` pero hay warnings | rebuild cache | `cargo clean -p crate && cargo clippy` |

## 10. Estado actual y siguientes pasos

### Completados

| Fase | Contexto | Tests | Rutas | Commit |
|---|---|---|---|---|
| F1 | identidad + auditoria | 22 | 7 | ... |
| F2 | residencia | 17 | 18 | ... |
| F3 | poblacion | 25 | 8 | ... |
| F4 | cobertura + cuidado | 35 | 18 | ... |
| F5 | historia | 20 | 4 | `cfbe466` |
| F6 | politica | 20 | 7 | `ba47856` |
| **Total** | | **125** | **62/81** | |

### Pendientes

| Fase | Contexto | Rutas estimadas | Complejidad |
|---|---|---|---|
| F7 | vigilancia | ~8 (alerts, deliveries, board, escalations) | Alta (state machine, notifications) |
| F8 | observacion | ~5 (events, current-state, sleep, mobility, bathroom) | Media (idempotencia, proyeccion) |
| F9 | plataforma | ~0 (config, no rutas nuevas) | Baja (config structs, validacion) |

### F7 ctx-vigilancia: notas

- **Agregados**: `Alert` (state machine), `NotificationDelivery` (append-only)
- **Tablas**: `alerts`, `alert_transitions`, `notification_deliveries`, `notification_delivery_events`, `alert_escalations`
- **Rutas**: GET/POST alerts, PATCH alerts/{id}, POST alerts/{id}/view, GET deliveries, GET board, GET current-state
- **Complejidad**: State machine `open->acknowledged->attending->resolved`, escalations, notification delivery
- **Dependencias**: Politica (para evaluar reglas), Poblacion (para resident), Observacion (para evidencia)

### F8 observacion: notas

- **No es ctx-***: es subsistema de datos, no bounded context de negocio
- **Tablas**: `sensor_events`, `current_bed_states`, resumenes diarios
- **Rutas**: POST /internal/v1/events, GET events, GET current-state, GET sleep/mobility/bathroom
- **Complejidad**: Idempotencia via source_event_id, proyeccion actualizable

### F9 plataforma: notas

- **No tiene tablas**: es configuracion TOML + env vars
- **Structs**: MonitoringConfig, HttpConfig, IngestConfig
- **Validacion**: fail-fast al arrancar si config invalida
- **Tests**: config invalida impide arrancar, defaults documentados

## 11. Metricas de referencia

| Metrica | F5 historia | F6 politica | Promedio |
|---|---|---|---|
| Archivos nuevos | ~25 | ~25 | ~25 |
| Lineas agregadas | ~2700 | ~3350 | ~3000 |
| Tests nuevos | 20 | 20 | ~20 |
| Rutas flippeadas | 4 | 7 | ~5 |
| Tiempo de sesion | ~45min | ~60min | ~50min |

## 12. Convenciones rapidas

- **Codigo en ingles** (variables, funciones, structs)
- **Documentacion en español** (comments, docs, commit messages)
- **IDs**: `base64(rand::random::<[u8;16]>())` via `mana_kernel::Id`
- **Timestamps**: `Instante` (RFC3339 millis)
- **Dates**: `NaiveDate` (YYYY-MM-DD)
- **JSON fields**: `serde_json::Value` para overrides/payloads
- **Build cache**: `~/.cache/cargo-target/{release,debug}/`
- **Hub port**: 8780
- **Hub log**: `/tmp/hub-N.log`
