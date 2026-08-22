# Casos de uso: Observacion

## Frontera funcional

Observacion **no es un `ctx-*`**: es un subsistema de ciclo de vida de datos.
Vive en `mana-observation` y posee `sensor_events`, `current_bed_states` y los
tres resumenes diarios. No decide que significa una alarma ni administra
residentes; su retencion, volumen y transporte pueden cambiar sin tocar el
modelo del Registro, y su destino es Parquet.

La regla que lo protege se verifica en CI: **ningun `ctx-*` puede declarar
`mana-observation`**. Sin eso el subsistema seria la puerta trasera por la que
dos contextos vuelven a tocarse a traves de la proyeccion.

Los IDs de cama, habitacion y residente son opacos. Resolverlos cruza contextos
y por eso vive en `mana-app`.

## Reglas del subsistema

- `source_event_id` hace idempotente la ingesta: un reintento devuelve `200` con
  `duplicate: true` y **no vuelve a proyectar**.
- Un evento es inmutable despues de aceptarse. No hay camino de `UPDATE`.
- `received_at` lo pone el hub; `occurred_at` viene de la fuente.
- **Unknown no es false ni cero.** `sleeping` ausente es `null`, nunca `false`.
- **Una `monitor_key` sin vincular no descarta el evento.** Se guarda con
  `resolved: false` y queda contable en `unresolved_events`.
- `state_since` solo se mueve cuando el estado **cambia**; un evento que repite
  el estado no reinicia el reloj de una permanencia.
- La frescura se **deriva** de `updated_at`; no existe columna `freshness`.
- **No existe `alert_level` en la proyeccion.** Es un veredicto de politica y el
  detector solo informa observaciones.
- La proyeccion es reemplazable: cambiar el ocupante de una cama la descarta en
  la misma transaccion que la asignacion.
- Un residente tiene un resumen por dia y por tipo. Reingerir el mismo dia
  reemplaza y devuelve `replaced: true`.

## OBS-01 - Ingerir un evento del detector

**Objetivo:** Conservar evidencia y actualizar el estado proyectado de la cama.

**Actor primario:** El bridge, con `x-clinical-secret`.

**Disparador:** HTTP POST `/internal/v1/events`.

**Precondiciones:** ninguna. El evento no exige que la cama exista.

**Flujo:**

1. Se valida el envelope y que `payload_json` sea JSON.
2. Dentro de una transaccion, se resuelve `monitor_key` -> cama (Residencia) y
   cama -> residente (Poblacion).
3. Si `source_event_id` ya existe, se devuelve el evento guardado con
   `duplicate: true` y termina.
4. Se inserta el evento con `received_at` del hub.
5. Si resolvio, se actualiza `current_bed_states`, moviendo `state_since` solo
   si el estado cambio.

**Postcondiciones:** el evento existe y es inmutable; la proyeccion refleja el
ultimo evento resuelto de esa cama.

**Alternativos:**

- **`monitor_key` sin vincular:** `201` con `resolved: false`. El evento queda
  guardado y sumado a `unresolved_events`. **El bridge no debe reintentar.**
- **Reintento:** `200` con `duplicate: true`, sin reproyectar.
- **`payload_json` invalido:** `422`, sin tocar la base.

## OBS-02 - Consultar el estado actual de un residente

**Objetivo:** Responder que esta pasando ahora con una persona.

**Capability:** `residents.live.read`.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/current-state`.

**Flujo:** se busca la asignacion abierta del residente y, si tiene cama, su
proyeccion. La frescura se calcula en la lectura.

**Postcondiciones:** ninguna; es una consulta.

**Alternativos:** residente sin cama asignada devuelve `state: null`, que es
distinto de una cama que nunca informo.

## OBS-03 - Board del ala

**Objetivo:** La vista operativa de la que cuelga el producto.

**Capability:** `monitoring.board.read`.

**Disparador:** HTTP GET `/api/v1/wings/{wingId}/board`.

**Flujo:** compone Residencia (habitaciones y camas), Poblacion (quien ocupa
cada cama) y Observacion (estado y frescura). Los estados de todas las camas se
piden en **una** consulta, no una por cama.

**Postcondiciones:** ninguna.

**Notas:** el board expone dos cosas que hoy son fallas silenciosas: una cama
sin `monitor_key` (no genera un solo aviso) y `unresolved_events` (una camara
informando sobre una cama que el sistema no sabe atribuir).

## OBS-04 - Eventos recientes de un residente

**Capability:** `residents.live.read`.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/events`.

**Flujo:** ultimos 100 eventos de la cama que ocupa, por `occurred_at`
descendente. Sin cama asignada, lista vacia.

## OBS-05 - Ingerir un resumen diario

**Objetivo:** Incorporar lo que la fuente analitica calculo sobre un dia.

**Actor primario:** La fuente de percepcion, con `x-clinical-secret`.

**Disparador:** HTTP POST `/internal/v1/clinical/{sleep,mobility,bathroom}-summaries`.

**Flujo:** se validan los invariantes de rango, se busca el resumen del
`(residente, dia)` y se reemplaza si existe, conservando `created_at`.

**Postcondiciones:** hay exactamente un resumen por residente, dia y tipo.

**Alternativos — todos `422`:**

- `wake_count < bed_exit_count`: salir de la cama implica haberse despertado.
- `night_visit_count > visit_count` o `assisted_count > visit_count`.
- `longest_visit_minutes > total_minutes`.
- `walking_minutes > out_of_bed_minutes`: caminar es parte de estar fuera de la
  cama, no un sumando aparte.
- Minutos que suman mas de 1440.
- `confidence` fuera de `[0, 1]`.

## OBS-06 - Leer resumenes de un residente

**Capabilities:** `sleep.read`, `mobility.read`, `bathroom.read`. La linea de
tiempo pide las tres.

**Disparador:** HTTP GET `/api/v1/residents/{residentId}/{sleep,mobility,bathroom,timeline}`.

**Flujo:** ultimos `limit` dias (por defecto 30, recortado a `[1, 365]`).

**Notas:** los derivados —minutos en cama, eficiencia de sueno, promedio por
visita— **los calcula la API**. El cliente no recalcula metricas clinicas desde
filas parciales. La eficiencia es `null` sin tiempo en cama: dividir por cero no
es cero.

## OBS-07 - Autorizar mirada al stream de una habitacion

**Objetivo:** Decir quien puede mirar y dejar la traza.

**Capability:** `monitoring.live.read`.

**Disparador:** HTTP POST `/api/v1/rooms/{roomId}/peek`.

**Flujo:** se valida la habitacion, se cuentan sus regiones de privacidad y se
escribe `room.peeked` en auditoria dentro de la misma transaccion.

**Postcondiciones:** existe una entrada de auditoria con actor y momento.

**Notas:** **no devuelve video.** El stream va directo de las IA cells a los
paneles; el hub autoriza y audita.

## OBS-08 - Resumen de residencia

**Capability:** `analytics.read`.

**Disparador:** HTTP GET `/api/v1/reports/summary`.

**Flujo:** cuenta residentes, camas, camas ocupadas, camas observadas y eventos
sin resolver.

**Notas:** la diferencia entre camas y camas observadas es la medida directa de
cuanta de la residencia esta efectivamente vigilada.
