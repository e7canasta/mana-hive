# Memoria de diseno del system of record

Estado: base de implementacion para el workspace `hub`.

Esta memoria define la forma del codigo y de los datos. No intenta convertir el
servidor Node en una especificacion. El Node, el cliente TypeScript, las escenas
y los seeds son evidencia de comportamiento y de contrato; las decisiones de
este documento gobiernan el diseno nuevo.

## 1. Objetivo y no objetivos

### Objetivo

Construir un Registro modular, transaccional y verificable que pueda crecer en
funcionalidad sin que las pantallas, las filas de SQLite y las invariantes del
dominio sean el mismo tipo.

### No objetivos

- Convertir cada bounded context en un microservicio.
- Hacer event sourcing de todo el negocio.
- Copiar el modelo actual por compatibilidad de datos.
- Generar el contrato HTTP desde Rust.
- Poner reglas clinicas en handlers o queries de la UI.

## 2. Forma general

```text
                         Edge (IA Server)
                              |
                              | HTTP POST /internal/v1/events
                              v
                        mana-hub (HTTP API)
                         |    |    |
            evt_perception|    |    |evt_policy
                         v    |    v
                    mana-engine|  mana-sentinel
                         |    |    |
                         v    |    v
                      evt_scene  evt_notif
                         |    |    |
                         v    v    v
                    mana-hub (persist)
                         |
                         v
                    mana-vigilancia
                         |
                         v
                    alerts via Hub API
```

```text
                    mana-hub
                       |
        +--------------+--------------+
        | mana-http      | mana-app        |
        | transporte     | casos de uso    |
        +--------------+--------------+
                       |
        +--------------+--------------+
        | ctx-identidad  | ctx-residencia  |
        | ctx-poblacion  | ctx-cobertura   |
        | ctx-cuidado    | ctx-historia    |
        | ctx-politica   | ctx-vigilancia  |
        | ctx-auditoria  | ctx-evidence    |
        | ctx-streams    |                 |
        +--------------+--------------+
                       |
              SQLite (sole SoR)
```

La observacion de sensores y la percepcion analitica tienen otro ciclo de vida.
Al principio pueden tener un adapter dentro del despliegue del hub. Cuando el
bus, Parquet o DuckDB tengan una razon operativa para separarse, seran procesos
separados con contratos explicitos. No se separan por tabla ni por moda.

## 3. Bounded contexts

Un bounded context no es una carpeta de endpoints. Es una frontera de lenguaje,
ownership e invariantes:

| Contexto | Pregunta | Clase |
| --- | --- | --- |
| Identidad | Quien puede entrar y que puede hacer? | generico |
| Auditoria | Que cambio, quien lo hizo y cuando? | generico |
| Residencia | Como esta compuesto el hogar y que dispositivos tiene? | soporte |
| Poblacion | Que personas estan en el hogar y donde estuvieron asignadas? | soporte |
| Cobertura | Quien trabaja y cubre cada unidad en un instante? | soporte |
| Cuidado | Que rondas, tareas y notas se registraron? | soporte |
| Historia | Que evidencia clinica hubo y que reviso un humano? | soporte |
| Politica | Que reglas y configuracion aplican a cada residente? | nucleo |
| Vigilancia | Que alertas existen, como se atienden y a quien se avisa? | nucleo |

Las superficies React combinan varios contextos porque resuelven tareas de una
persona. Esa composicion es un read model de aplicacion, no una razon para
fusionar los contextos.

## 4. Modular monolith primero

Los nueve contextos viven en crates distintos pero dentro de `mana-hub`.

Esto conserva:

- una transaccion SQLite para una operacion que cruza dos contextos;
- despliegue simple en el edge;
- debugging local directo;
- ownership y dependencias verificables por Cargo;
- la posibilidad de extraer un proceso cuando aparezca una frontera de ciclo de
  vida real.

Un proceso separado se justifica si cambia al menos una de estas condiciones:

- necesita escalar con una cadencia diferente;
- tiene una tecnologia o runtime diferente;
- tolera consistencia eventual y reintentos;
- tiene una politica de retencion o fallo independiente;
- consume recursos que no deben competir con el Registro.

Por eso observacion, percepcion, bus de eventos y workers pueden separarse. Un
`ctx-residencia` y un `ctx-poblacion` no deben separarse mientras una asignacion
necesite una sola transaccion local.

## 5. Dependencias de crates

```text
mana-hub
  -> mana-http -> mana-app -> ctx-*
                         -> mana-kernel
  -> mana-wire

ctx-* -> mana-kernel
ctx-* no puede depender de otro ctx-*
```

Reglas:

1. Ningun `ctx-*` declara otro `ctx-*` en `Cargo.toml`.
2. `mana-app` es el unico lugar donde se importa mas de un contexto.
3. `mana-kernel` contiene IDs, tiempo, errores tecnicos y capacidades
   transversales pequenas. No contiene Resident, Alert, Bed ni vocabulario de
   negocio.
4. `mana-wire` contiene formas de transporte, no tipos de dominio ni filas.
5. Un crate no publica su modulo `store` ni sus filas Diesel.

El chequeo de Cargo no es una convencion: `xtask` debe fallar si una dependencia
prohibida aparece.

## 6. Capas

```text
axum handler
    |
    v
mana-http
    |  DTO OpenAPI <-> comando/consulta
    v
mana-app
    |  autorizacion, transaccion y composicion
    v
ctx-*/lib.rs
    |------------------+
    v                  v
domain              store
tipos puros         Diesel, filas y mapeos
```

### `mana-http`

Un handler extrae parametros, decodifica el DTO, llama un caso de uso y mapea
el resultado al wire. No hace joins, no abre conexiones y no decide reglas.

### `mana-app`

Es el Application Service de Fowler. Coordina permisos resueltos, transacciones,
relojes, auditoria y cruces. No debe convertirse en un deposito de reglas:

- una regla sobre un agregado pertenece al dominio de ese agregado;
- una secuencia de un contexto pertenece a su caso de uso;
- una operacion que coordina dos contextos pertenece a `mana-app`.

### `ctx-*/domain`

Contiene entidades, value objects, estados y metodos que hacen cumplir
invariantes. No conoce Diesel, HTTP, serde, Tokio ni otro contexto.

### `ctx-*/store`

Contiene `schema.rs`, structs `Row`, queries y conversiones. El repositorio
recibe y devuelve agregados del dominio. Una fila nunca sale del crate.

## 7. Las cuatro poblaciones de tipos

No hay un tipo compartido que intente servir para todo.

| Poblacion | Vive en | Proposito |
| --- | --- | --- |
| Dominio | `ctx-*/domain` | Invariantes y comportamiento |
| Wire | `mana-wire` | Protocolo HTTP definido por OpenAPI |
| Fila | `ctx-*/store` | Persistencia y mapeo Diesel |
| Read model | `mana-app` o crate de consulta | Composicion para una pantalla |

El contrato es independiente:

- OpenAPI se mantiene a mano y versionado.
- Los DTOs Rust se escriben a mano con `serde`.
- Los schemas Zod del cliente se mantienen como validacion de runtime del
  cliente.
- No se agregan `utoipa`, `ts-rs` o `schemars` para generar el contrato.

La duplicacion entre wire y dominio es intencional. Hace visible cuando el
contrato cambia sin obligar a migrar el dominio, y viceversa.

## 8. Agregados y servicios

Un agregado es una frontera de consistencia, no una tabla y no necesariamente
una jerarquia completa.

Reglas de modelado:

- El agregado protege invariantes que deben cambiar juntos.
- El repositorio carga y guarda el agregado, no una fila arbitraria.
- Una consulta que no necesita invariantes puede devolver un read model.
- Un Domain Service solo aparece cuando la regla es de dominio pero no tiene una
  entidad natural.
- Un Application Service coordina; no reemplaza al dominio.
- Los traits se reservan para puertos reales: reloj, generador de IDs,
  auditoria, envio de notificaciones o un repositorio que deba ser sustituido
  en un test.

### Agregados iniciales

| Contexto | Agregados |
| --- | --- |
| Identidad | `User`, `Session` |
| Auditoria | `AuditEntry` |
| Residencia | `Facility`, `Wing`, `Room`, `Bed`, `Planogram` |
| Poblacion | `Resident`, `BedAssignment`, `ResidentAttribute` |
| Cobertura | `StaffGroup`, `ShiftGrid`, `WingCoverage` |
| Cuidado | `Round`, `CareNote` |
| Historia | `IncidentDetection`, `IncidentReview` |
| Politica | `AlarmProfileVersion`, `AlarmCatalog` |
| Vigilancia | `Alert`, `NotificationDelivery` |

No se modela `Facility` como un agregado gigante que cargue todo el hogar. Una
residencia contiene IDs de alas; las operaciones de cada agregado son pequenas
y el caso de uso compone cuando hace falta.

## 9. Cruces entre contextos

Los contextos se relacionan por IDs tipados y puertos definidos por el
consumidor, no por imports ni joins privados.

```text
Identidad ------ actor/capability ------> mana-app
Residencia ----- BedId/FacilityId -------> mana-app
Poblacion ------ ResidentId/Assignment -> mana-app
Observacion ---- estado por BedId -------> mana-app
Politica ------- perfil por ResidentId -> mana-app
Cobertura ------ cobertura por WingId ---> mana-app
Vigilancia ----- alerta compuesta --------> mana-app
```

Las referencias a otro contexto son IDs opacos. Las migraciones solo declaran
foreign keys hacia tablas que pertenecen al mismo contexto. La existencia de un
ID externo se verifica en el Application Service dentro de la misma transaccion.

Esto evita que una migration de Poblacion dependa del schema privado de
Residencia y mantiene la regla de que el contexto consumidor no hace un join
contra tablas ajenas.

## 10. Transacciones y Diesel

SQLite es una base unica para el Registro. Cada contexto es dueño de sus
migraciones, pero `mana-hub` las registra y ejecuta en un orden conocido al
arrancar.

Diesel es sincrono. La frontera async/sync se cruza una sola vez:

```text
axum async
    -> spawn_blocking
        -> pool r2d2
            -> transaccion Diesel
                -> repositorios de contextos
```

Los puertos de repositorio son sincronos. No se agrega `async-trait` a una capa
que solo habla con SQLite.

Pragmas obligatorios:

- `foreign_keys = ON`
- `journal_mode = WAL`
- `busy_timeout` configurado
- `synchronous = NORMAL`

Una transaccion que cruza contextos se abre en `mana-app` y recibe la misma
conexion. Ningun contexto inicia una transaccion anidada ni decide el orden de
otro contexto.

## 11. Convenciones de persistencia

- IDs publicos: `TEXT`, opacos, estables en seeds y tipados en Rust.
- Timestamps: `TEXT` RFC3339 UTC con milisegundos.
- Fechas civiles: `TEXT` `YYYY-MM-DD` y tipo distinto en el dominio.
- Booleanos SQLite: `INTEGER NOT NULL CHECK (value IN (0, 1))`.
- Estados de negocio: enum cerrado en dominio y `CHECK` en SQLite cuando el
  vocabulario sea estable.
- Valores clinicos y reglas: newtypes y constructores con limites.
- JSON solo para payload crudo, metadata de auditoria o parametros
  catalog-driven que no admiten una columna estable. Nunca para el nucleo de
  residentes, asignaciones o estados.
- El borrado logico estructural usa `retired_at` y `retired_by`.
- El egreso de un residente es un hecho de poblacion, no un retiro tecnico.
- Indices parciales expresan unicidad de registros activos o vigentes.

## 12. Autorizacion y auditoria

La identidad resuelve el actor y sus capabilities en el borde HTTP. Los
contextos reciben una capacidad ya resuelta, no un objeto de sesion ni una
dependencia a `ctx-identidad`.

La capability es autoridad de API. La UI puede ocultar una accion, pero no la
autoriza.

La auditoria se inyecta mediante un puerto. Por ejemplo, un caso de uso declara
la necesidad de registrar una mutacion, y `mana-app` conecta ese puerto con
`ctx-auditoria`. Ningun contexto de negocio importa `ctx-auditoria`.

## 13. Observacion no es Registro

`sensor_events` es evidencia externa append-only. `current_bed_states` es una
proyeccion reconstruible. Ninguna de las dos es el agregado de una alerta.

El Registro consume estados de observacion para decidir o componer vistas, pero
no modifica la evidencia original. La misma regla aplica a resúmenes de sueño,
movilidad y bano: son datos observados o derivados, no una edicion manual del
historial de negocio.

No se adopta event sourcing global. Las alertas, rondas y asignaciones siguen
siendo agregados mutables; solo se hacen append-only las cosas cuya naturaleza
es evidencia, revision, auditoria o entrega.

## 14. Pruebas

Cada contexto tiene tres niveles:

1. Tests de dominio sin SQLite ni HTTP.
2. Tests de store contra una base temporal con sus migraciones.
3. Tests de caso de uso y contrato desde el handler o cliente real.

Ademas:

- `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings` y
  `cargo test --workspace`.
- CI verifica el grafo de crates y el ownership de tablas.
- OpenAPI se valida y sus ejemplos se ejecutan contra el hub.
- Las escenas prueban recorridos, no structs internas.
- Las propiedades se reservan para maquinas de estado, transiciones y
  composicion de politica donde generan valor.

## 15. Event-Driven Architecture (ADR-001 to ADR-004)

### Decision: NATS JetStream as Communication Backbone

The system uses NATS JetStream for inter-binary communication. This replaced the initial monolithic approach where all components lived in a single process.

**Rationale:**
- Decouples binaries: each can be deployed, scaled, and restarted independently
- JetStream provides durable message delivery (no event loss on restart)
- Natural fit for the perception → scene → notification pipeline
- Workers remain stateless (no DB), persisting via Hub HTTP API

### Decision: 4 Binary Architecture

| Binary | NATS Subscriptions | NATS Publications | Purpose |
|--------|-------------------|-------------------|---------|
| mana-hub | evt_scene, evt_notif, evt_policy | evt_perception, evt_policy | HTTP API + SQLite persistence |
| mana-engine | evt_perception, evt_policy | evt_scene | DigitalTwin FSM engine |
| mana-sentinel | evt_scene, evt_policy | evt_notif | Rule evaluation + incident mgmt |
| mana-vigilancia | evt_notif | — | Notification processing |

### Decision: Hub as Sole System of Record

Hub owns SQLite and serves all 96 HTTP endpoints. Workers are stateless and communicate with Hub via HTTP API for persistence. This eliminates dual-writes and ensures a single source of truth.

### Decision: Event Store in Hub

All events (sensor_events, scene_events, notification_events) are persisted by Hub. This provides:
- Complete audit trail
- Ability to rebuild worker state from events
- Single point for event querying

## 16. Decisiones abiertas controladas

Estas preguntas no se esconden en implementaciones:

1. Si un despliegue puede atender mas de una residencia, hay que definir el
   alcance de capabilities antes de implementar autorizacion sobre estructura.
   Mientras no se decida, ninguna query puede asumir una residencia global.
2. El archivo OpenAPI manual se incorpora en F0 antes de publicar el primer
   handler Rust.
3. La observacion puede empezar como adapter del hub, pero su extraccion a
   Parquet/DuckDB debe conservar el mismo contrato de eventos.
4. Para una base nueva se puede usar Argon2id para password hashes. Si se
   necesita interoperar durante una convivencia, se documenta un periodo
   transitorio en vez de ocultarlo en el dominio.
