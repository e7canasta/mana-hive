# mana-engine · Servicio headless de motores y digital twin

Documento de arquitectura. No toca código: es el plano que el implementador
ejecuta en una sesión posterior. Complementa
[`../contextos/f11-plan.md`](../contextos/f11-plan.md), que queda en
re-planificación.

---

## 1 · Big picture

Dos sistemas con frontera por la red, no por `use`:

```text
                          ┌──────────────────────────────┐
                          │  HUB  (System of Record)     │
                          │  eventos · observaciones ·    │
                          │  políticas · alertas ·         │
                          │  entregas · panel /api/v1/*   │
                          └──┬────────────────┬────────┬──┘
        evento/tick (input)  │                │ salida │  request/response
        Hub ───────────────▶ │                │ ◀──────│  Hub pregunta
                             ▼                ▼        ▼
                          ┌──────────────────────────────┐
                          │  mana-engine (headless)       │
                          │  twin vivo + canales (in/out) │
                          │  engines:                     │
                          │   · alarma/twin  → eventos   │
                          │   · autopilot/recom → req/res│
                          │   · reloj (scan loop)         │
                          └──────────────────────────────┘
                               se HIDRATA desde Hub
                               (HydrationPort), no es SoR
```

- **Hub** guarda y sirve. Es la fuente de verdad para el panel y para auditoría.
- **mana-engine** decide. Mantiene el twin vivo, corre los engines y el reloj.
  Tiene **dos caras** (ver §3): canales de evento (in/out) para el motor de
  alarma/twin, y request/response para los motores de consulta/comando.

Por qué esto y no dejarlo en `mana-app`: el lazo "no era de ningún contexto"
(`lazo-operativo.md:24`); esconderlo en un crate de la app lo volvía frágil. Como
servicio propio, la frontera es explícita y cada parte se despliega y falla
independiente.

---

## 2 · El digital twin

Proyección **viva** por cama/residente que el engine mantiene en memoria y
snapshot duradero.

Estado de cada nodo del twin:

| Campo | De dónde viene |
| --- | --- |
| `bed_id`, `resident_id` | Hub (residencia / asignaciones) |
| `estado`, `state_since`, `prev_distinct`, `room_state` | Hub `current_bed_states` |
| `perfil_efectivo` vigente en el instante | Hub `ctx-politica` (resuelto) |
| `cobertura_vigente` | Hub `ctx-cobertura::get_coverage(wing, at)` |
| `alertas_abiertas` de la cama | Hub `ctx-vigilancia` |
| `timers_armados` (dwell / confirm / escalate) | **derivados del estado**, no fila aparte |

Invariantes del twin:

1. **No es SoR.** Se reconstruye 100% desde Hub (snapshot + replay de eventos
   desde el último watermark). Un reinicio del engine no pierde lógica, solo
   re-hidrata.
2. **Orden determinista.** El twin aplica eventos por `rowid`/secuencia monotónica,
   nunca por `created_at` (lección F8). Fuera de orden se encola, no se aplica.
3. **Los timers son estado derivado.** Permanencia y confirmación se calculan
   como `now - state_since >= umbral`; se cancelan solos cuando cambia el estado.
   Solo el escalamiento (anclado a la alerta, no a la cama) se materializa como
   vista derivada de la fila de alerta abierta.

---

## 3 · Los tres puertos (diseño, no implementación)

El engine **no importa `ctx-*` ni `mana-app`**. Habla por traits. Las impl
arrancan in-process (lee los `Stores` de Hub) y luego pasan a red / log de
eventos sin tocar la lógica del engine.

El engine tiene **dos caras** (acordado con producto):

1. **API request/response** — para los motores de consulta/comando (autopilot,
   recomendación, y la evaluación on-demand que Hub llama durante la ingesta).
   Hub pregunta, el engine responde. Esto es claro y se queda.
2. **Canales de evento** — para el motor de alarma / twin, que vive de eventos
   y timers. El engine que *digiere* eventos no "responde" sobre el evento
   recibido: lo ingesta, actualiza el twin y **emite eventos de cambio de
   estado en la escena**. Esos eventos de salida están **desacoplados de la
   entrada**: pueden nacer de un evento del detector O del disparo de un timer
   (el reloj / scan loop). Por eso hay dos canales, no uno.

```rust
/// Lo que el engine necesita LEER del SoR para reconstruir el twin.
trait HydrationPort {
    fn hydrate_snapshot(&self) -> Result<TwinSnapshot, EngineError>;
    fn replay_from(&self, after_seq: Seq) -> Result<Vec<EngineEvent>, EngineError>;
}

/// Canal de ENTRADA: el engine RECIBE eventos.
/// Fuente: Hub (eventos del detector) y el reloj del propio engine (ticks).
trait EngineInput {
    fn push(&self, evento: EngineEvent) -> Result<(), EngineError>;
}

/// Canal de SALIDA: el engine EMITE cambios de estado del twin.
/// Hub se suscribe y DECIDE (persistir, alertar, escalar, entregar).
trait EngineOutput {
    fn emit(&self, evento: SceneEvent) -> Result<(), EngineError>;
}

/// API request/response para motores de consulta/comando.
trait EngineRpc {
    fn evaluate_bed(&self, ...) -> Result<Vec<AlertaNueva>, EngineError>; // usado por ingest
    fn run_autopilot(&self, ...) -> Result<...>;
    fn recommend(&self, ...) -> Result<...>;
}
```

`EngineEvent = DetectorEvent(monitor_key, kind, state, occurred_at, ...) |
Tick(at)`. `SceneEvent` = transición de estado del twin (p.ej. `laying_in_bed
→ standing` en cama X, o `exit_sin_retorno` detectado por timer). El `seq`
monótono del input garantiza el orden del twin (lección F8).

**Por ahora** los canales de evento son **webhooks** a cada lado: Hub postea a
la entrada del engine y el engine postea a la salida de Hub. No es una API
HTTP estricta donde cada uno hace POST al otro; es un canal de eventos con un
handler en cada punta. La API request/response (§2) sí es un llamado directo.

Esto reemplaza el `DecisionSink` original: el engine ya no escribe la alerta
directo, emite un `SceneEvent` y **Hub decide** qué materializar. La
evaluación on-demand de la ingesta queda como request/response para no romper
el camino actual; el camino de timers (scan loop) usa el canal de evento y
emite `SceneEvent`s que Hub materializa.

---

## 4 · Engines (ya existentes, a mover u orquestar)

- **Alarma (núcleo puro):** `mana-motores/src/alarmas.rs` (`evaluar`,
  `considerar`, `Disparo`, `Clase`, `ReglaEfectiva`). Ya cumple "sin IO". El
  engine lo usa como core; **no se toca su lógica**.
- **Recomendación y autopilot:** ya tienen decisión pura (ver
  `docs/arquitectura/motores.md`). El engine los orquesta y programa su
  ejecución.
- **Reloj / scan loop:** vive en el engine (reemplaza la necesidad de scheduler
  en Hub). En cada tick y en cada evento, el engine re-evalúa el twin con
  `Disparo::Barrido` / `Disparo::Evento`.

---

## 5 · Refactoring estilo Martin Fowler

Principio rector: **introducir el seam, mover detrás del seam, cambiar la impl,
borrar lo viejo** — nunca mover y cambiar a la vez. Cada paso compila y pasa las
escenas existentes (comportamiento observable idéntico).

> **Estado (commit `93d456a`):** los pasos 0–7 están hechos y validados
> (build, clippy `-D warnings`, `verificar-contextos`, y escena
> `motores-alarmas-blueprint` end-to-end). El crate `mana-engine` existe; el
> lazo y el scan loop viven ahí; `observation.rs::ingest_event` delega en
> `Engine::evaluate_bed`; `lazo.rs` fue borrado. El diseño de puertos se refinó
> (ver §3): en vez de `EventPort` + `DecisionSink`, el engine tiene **API
> request/response** + **canales de evento `EngineInput`/`EngineOutput`**. El
> paso 8 (split a proceso) y el Movimiento 3 (materializar esos canales) son lo
> próximo.

| Paso | Qué se hace | Dónde queda la responsabilidad |
| --- | --- | --- |
| 0 · estado hoy | `lazo.rs` (en `mana-app`) hace IO + `evaluar`; `mana-motores` tiene lo puro | Hub |
| 1 · aislar core | `mana-motores::alarmas` ya es puro y no depende de `ctx-*` | engine (core) |
| 2 · definir puertos | traits `HydrationPort` / `EventPort` / `DecisionSink` en `mana-engine` (nuevo crate) | engine |
| 3 · impl in-process | las impl leen los `Stores` de Hub vía los traits de repo (sin `use ctx-*`) | engine ↔ Hub en-proc |
| 4 · mover scan loop | el loop + `Disparo::Barrido` corren en el engine; llama a `evaluar` | engine |
| 5 · write-back | el engine llama a `create_alert` / `escalate` / `create_delivery` de Hub vía `DecisionSink` | engine → Hub |
| 6 · desenganchar Hub | `observation.rs::ingest_event` deja de llamar `evaluar_cama`; solo persiste + publica evento | Hub (solo SoR) |
| 7 · borrar seam viejo | `lazo.rs` se elimina o queda como adaptador legacy muerto | Hub |
| 8 · split a proceso | impl de puertos pasa de in-process a red / log de eventos | engine aparte |
| 9 · event sourcing fuera | `EventPort` apunta al log propio; Hub deja de ser dueño de la cola | engine |

Reglas de oro: (a) ningún paso avanza si el anterior no compila y pasa tests;
(b) las escenas `motores-alarmas-blueprint`, `politica-blueprint`, etc. deben
seguir pasando en cada paso; (c) el twin derivado debe producir las mismas
alertas que `lazo.rs` hoy.

---

## 6 · Impacto en F11

- **F11.0 · Política clínica** — completo, se conserva.
- **F11.1 · Motor evento→alerta** — completo, se conserva (es el core de Paso 1).
- **F11.2 · Reloj / entrega / escalamiento** — se entrega **dentro de
  mana-engine** (Pasos 4–5), no en Hub. Por eso F11 tal como estaba queda
  pausado/re-planificado.
- **F11.3 · NATS** — se convierte en la implementación de `EventPort` +
  `DecisionSink` (Paso 8).
- **F11.4 · Parquet/DuckDB** — sin cambios.

---

## 7 · Riesgos y invariantes

- **Orden (F8):** el twin aplica por `seq`/`rowid`, no por timestamp. Fuera de
  orden se encola.
- **Idempotencia:** el write-back de alerta dedupe por `(cama, regla, episodio)`
  (`ya_avisadas` desde `state_since`); ya existe en `alarmas.rs`.
- **El twin no es fuente para el panel:** el panel lee de Hub. El engine puede
  exponer su propio estado solo para debug, nunca como SoR.
- **Determinismo del reloj:** el scan es puro sobre el twin; el retraso se mide,
  no se esconde.

---

## 8 · Qué NO hacer (acordado)

- No investigar más el dominio: lo ya definido es base.
- No reescribir la lógica de `mana-motores::alarmas`.
- No crear un mundo in-memory que compita con el SoR: el twin es **derivado** y
  re-hidratable.
- No meter el scheduler en Hub: el reloj es del engine.

---

## 9 · Movimiento 3: canales de evento `EngineInput` / `EngineOutput`

**Hecho y validado (commit tras `93d456a`).** Materializa el contrato de §3:
el engine **emite** `SceneEvent`s y **Hub los materializa**; ya no escribe la
alerta directo. Se conserva la cara request/response (`evaluate_bed`) para la
ingesta on-demand. La escena `motores-alarmas-blueprint` pasa idéntica
(en cama → 0; salida → 1 `bed_exit`/`high`; repetido → 0).

Decisión de implementación: para preservar la invariante "el evento y su alerta
se escriben en la misma transacción", `EngineOutput::emit` recibe la conexión en
curso. El engine expone dos entradas de canal:
- `push_in(conn, evento)`: corre dentro de una transacción ya abierta (lo usa la
  ingesta del evento en `observation.rs`).
- `push(evento)`: abre su propia conexión + transacción (lo usa el scan loop /
  reloj en `spawn_scan_loop`).

`EngineEvent` = `Detector(detenector ya resuelto a cama)` | `Tick(instante)`.
`SceneEvent` = transición del twin + `alerts: Vec<AlertaNueva>` (vacío = sin
novedad). `HubEngineOutput::emit` mapea cada alerta a `AlertInput` y la persiste
vía `ctx_vigilancia::create_alert_in_transaction`.

Entregables (implementados):

1. **Tipos en `mana-engine`:**
   - `EngineEvent { Detector(DetectorEventInput), Tick(Instante) }`
   - `SceneEvent { bed_id, resident_id, from_state, to_state, occurred_at,
     alerts: Vec<AlertaEmitida>, source: EngineEvent }`
2. **Traits (reemplazan `DecisionSink`):**
   - `EngineInput { fn push(&self, ev: EngineEvent) -> Result<Vec<SceneEvent>, EngineError>; }`
     — el engine hidrata la cama, evalúa y **devuelve** los `SceneEvent`
     emitidos (para que la cara req/res los reporte en el response).
   - `EngineOutput { fn emit(&self, ev: SceneEvent) -> Result<(), EngineError>; }`
     — Hub materializa (create_alert / escalate / delivery).
3. **`Engine<H, O>`** genérico sobre `Hydration` + `EngineOutput`. `EngineInput`
   se implementa para `Engine`.
4. `evaluate_bed` emite vía `EngineOutput` (ya no `Decisions::create_alert`);
   `scan_all` itera camas y emite.
5. `spawn_scan_loop` llama `engine.push(EngineEvent::Tick(ahora))` (en vez de
   `scan_all` directo). `observation.rs::ingest_event` llama
   `engine.push(EngineEvent::Detector(...))` y usa los `SceneEvent` devueltos
   para poblar `alerts` del response.
6. **Impl in-process:** `HubEngineOutput::emit` llama
   `ctx-vigilancia::create_alert_in_transaction` (mismo write-back de hoy).
   `hub_engine(pool, catalog)` arma `Engine<HubHydration, HubEngineOutput>`.
 7. Borrar `Decisions` / `DecisionSink` (reemplazados).

### Log de eventos (observabilidad + sustrato DuckDB/Parquet)

El canal `EngineInput`/`EngineOutput` es en proceso: Hub es el único
suscriptor de los `SceneEvent` (vía `HubEngineOutput`) y el único editor de
`EngineEvent`. No hay pub/sub todavía. Para hacer visible y durable el
intercambio (y poder auditar / reconstruir el lazo), `engine.rs` escribe un
**append-only JSONL** de ambos sentidos cuando existe la env
`MANA_ENGINE_EVENT_LOG=<path>`:

- `{"dir":"in","kind":"detector"|"tick","payload":<EngineEvent>}` — lo que Hub
  empuja al engine.
- `{"dir":"out","kind":"scene","payload":<SceneEvent>}` — lo que el engine
  emite y Hub materializa.

En producción la env va vacía y no se escribe nada (comportamiento neutro; los
errores de escritura se ignoran). Este log es el sustrato para
event-sourcing: se compacta a Parquet y se consulta con DuckDB. El `ts` es
epoch-ms; los `Instante` se serializan como `Instante(<iso>)` (el ISO interno
es válido para parsear luego).

### Compactado a Parquet / DuckDB

`scripts/compactar_eventos.sql` es el job de compactado: lee el JSONL con
`read_json_auto`, lo vuelca a `engine-events.parquet` (zstd) y lo registra como
tabla consultable en una base DuckDB, con analítica de ejemplo (volumen por
`dir`/`kind`, alertas emitidas por regla/nivel, permanencias por reloj). Se
corre donde DuckDB esté disponible (CI/prod):

```
duckdb mana_engine_events.db < scripts/compactar_eventos.sql
```

**Verificado** con DuckDB 1.2.2 (CLI): el job genera `engine-events.parquet`
(zstd) y produce, sobre el JSONL real de la escena de reloj,
`out/scene` = `bed_exit/high` (evento) + `out_of_bed_dwell/high` (barrido), y 1
permanencia detectada por el reloj. El `payload` se lee como **STRUCT** (no como
JSON), así que las consultas acceden por campo (`payload.disparo`) y desanidan
`payload.alerts` con `UNNEST(...) AS t(a)`. Trae todo lo necesario
(`rule_id`, `level`, `disparo`, `from_state`, `to_state`) para reconstruir el
lazo y auditarlo.

Los dos sentidos se proyectan en streams separados (la decisión de diseño es
auditar las **salidas** del engine — los cambios de escena — aparte de las
entradas):
- `eventos_in` — lo que Hub empuja (`detector` lleva `resident_id`/`bed_id` y el
  `state`; `tick` es un pulso de barrido del reloj y **no** lleva escena).
- `cambios_escena` — lo que el engine emite (`SceneEvent`): los cambios de
  escena con `disparo` (evento/barrido), `from_state → to_state` y `alerts`.
- `alertas` — tabla aplanada, un hecho por alerta emitida (`rule_id`, `level`,
  `evidence_kind`, escena, disparo, desde/hasta). Es el hecho de event-sourcing.
- `timeline_escena` — **vista** con la secuencia cronológica por **cama**
  (`bed_id`, la identidad de la escena: el engine evalúa por cama en
  `evaluate_bed`), ordenada por `ts`. El `resident_id` se conserva como
  **atributo** (columna `residente`), no como clave: el corte por residente se
  hace después en DuckDB (`GROUP BY resident_id`). Une entradas (`detector`) y
  cambios (`scene`) y muestra `desde → hasta` y las `reglas` disparadas. Los
  `tick` quedan fuera porque no llevan cama (son scan global).

Sobre el JSONL real de la escena de reloj la timeline de la cama queda:
`detector ×2 → scene(evento, laying_in_bed→standing, [bed_exit]) →
scene(barrido, laying_in_bed→standing, [out_of_bed_dwell])`.

La escena `motores-alarmas-blueprint` sumó un paso `MOT-AL-02b` que lee
`GET /api/v1/alerts` y afirma `status:"open"`: prueba independiente de que Hub
es System-of-Record (la alerta no solo se "decidió", sino que persiste y es
consultable).

Criterio de aceptación:

- `cargo build`, `clippy --workspace --all-targets -- -D warnings`,
  `verificar-contextos`.
- Escena `motores-alarmas-blueprint` sigue pasando (en cama → 0; salida →
  1 `bed_exit`/`high`; repetido → 0).
- Nueva escena de reloj (base F11.2): `motores-alarmas-reloj.json` avanza el
  tiempo **sin eventos** y el engine emite el `SceneEvent` de permanencia
  (`out_of_bed_dwell`, `evidence_kind: dwell_window`) por `Disparo::Barrido`.
  Para que sea repetible usa dos ayudas de test en `mana-sdk`:
  - Resolver `{{now}}` / `{{now-600}}` (ISO-8601 UTC real) en args y asserts,
    porque el perfil tiene `valid_from` = ahora y los eventos con fecha fija
    en el pasado caen fuera de su ventana.
  - Acción `sleep` en el runner y env `MANA_ENGINE_SCAN_PERIOD_SECS` (default 30)
    para acortar el barrido durante la prueba.

Al terminar, volvés para revisión.
