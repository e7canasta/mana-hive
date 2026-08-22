# Modelo de dominio: Observacion

Vive en `crates/mana-observation`. **No es un `ctx-*`**: es un subsistema de
ciclo de vida de datos. Ver la frontera en
[`../casos-uso/observacion.md`](../casos-uso/observacion.md).

## Objetos

### `SensorEvent` — evidencia

Lo que informo el detector. **Inmutable una vez aceptado**: no hay metodo de
actualizacion en el repositorio, asi que corregir un evento no es algo que se
pueda hacer sin querer.

| Campo | Tipo | Nota |
| --- | --- | --- |
| `id` | `SensorEventId` | |
| `source_event_id` | `String` | `UNIQUE`. Es la clave de idempotencia |
| `monitor_key` | `String` | Lo que el detector conoce. Siempre presente |
| `resolution` | `Resolution` | A que cama y residente corresponde |
| `kind`, `room_state`, `substate`, `zone`, `state` | `Option<String>` | Vocabulario del detector |
| `sleeping` | `Option<bool>` | `None` es "no informo", **nunca** `false` |
| `occurred_at` | `Instante` | De la fuente |
| `received_at` | `Instante` | Lo pone el hub |
| `payload_json` | `String` | Copia de borde para campos nuevos |

### `Resolution` — el caso que no se puede ignorar

```rust
pub enum Resolution {
    Resolved { bed_id: String, resident_id: Option<String> },
    Unresolved,
}
```

El detector manda `monitor_key`, no camas. Traducirlo cruza Residencia y
Poblacion y **puede fallar** cuando una camara todavia no esta vinculada.

Es un enum y no un `Option<String>` a proposito: obliga a cada consumidor a
tratar el caso sin resolver en vez de encontrarse un `None` y seguir de largo.
Que una camara este mirando algo que el sistema no sabe atribuir es exactamente
la falla silenciosa que este proyecto existe para eliminar, y por eso tambien es
un contador visible (`unresolved_events`) y no un residuo.

### `BedState` — proyeccion

El ultimo evento por cama. **Reemplazable y reconstruible, nunca fuente de
verdad.**

Dos ausencias deliberadas respecto del modelo anterior:

- **No tiene `alert_level`.** Es un veredicto de politica; el detector informa
  observaciones y la politica decide. Persistirlo aca reimportaria el defecto
  que el rewrite existe para sacar.
- **No tiene `freshness`.** Se deriva de `updated_at` en cada lectura. Una
  columna quedaria vieja sola: diria `live` sobre una cama que dejo de informar
  hace una hora.

`state_since` solo se mueve cuando el estado **cambia**. Si se reiniciara con
cada evento repetido, una alarma de "cuarenta minutos fuera de la cama" no
venceria nunca mientras el monitor siguiera hablando.

### `Freshness` — derivada, no persistida

```rust
pub enum Freshness { NotObserved, Live, Stale, Offline }
```

`NotObserved` no es `Offline`. Una cama que nunca hablo no es una cama caida:
la primera suele ser una cama sin `monitor_key`, y la segunda una camara que se
cayo. Confundirlas oculta el problema mas comun de instalacion.

Los umbrales (`FreshnessThresholds`) son parametros de plataforma, no constantes
del codigo.

### Resumenes diarios

`SleepSummary`, `MobilitySummary`, `BathroomSummary`. Comparten `Provenance`:
`source`, `model_version`, `confidence`, `provenance_json`. Sin eso un numero
clinico no se puede auditar — no se sabe que modelo lo produjo.

Son **evidencia, no registro**: nadie los corrige a mano, se reingieren. Es la
unica escritura de observacion que no es append-only, y lo es porque la fuente
puede recalcular un dia.

Los derivados no se persisten y se calculan al leer:

| Derivado | Regla |
| --- | --- |
| `in_bed_minutes` | `calm + restless + awake` |
| `efficiency` | `calm / in_bed`, **`None` sin tiempo en cama** |
| `average_visit_minutes` | `total / visitas`, `None` sin visitas |

Persistir una suma de columnas es una via para que quede inconsistente; y
dividir por cero no es cero, es "no se puede decir".

## Invariantes y como se hacen cumplir

| # | Invariante | Mecanismo |
| --- | --- | --- |
| 1 | La ingesta es idempotente | `source_event_id UNIQUE` + lectura previa en la transaccion |
| 2 | Un evento es inmutable | No existe metodo de actualizacion en el repositorio |
| 3 | `received_at` lo pone el hub | `EventInput` no tiene ese campo |
| 4 | Unknown no es false ni cero | `Option<bool>`, columna `NULL`-able sin default, mapeo sin `unwrap_or_default()` |
| 5 | El estado actual es reconstruible | La proyeccion se borra y se rearma desde el stream |
| 6 | Cambiar ocupante limpia la proyeccion | `clear_projection_in_transaction`, en la transaccion de la asignacion |
| 7 | La frescura se deriva | No hay columna; `Freshness::derive` toma el reloj |
| 8 | El detector observa, la politica decide | `mana-observation` no depende de `ctx-politica`, y `xtask` lo verifica |
| 9 | Un resumen por residente y dia | Indice unico `(resident_id, observed_on)` |
| 10 | Los rangos clinicos son coherentes | Validacion en el constructor de cada `*Input` |

## Tablas

| Tabla | Migracion | Nota |
| --- | --- | --- |
| `sensor_events` | `0012_observation` | `bed_id` **nullable**: un evento sin resolver se conserva |
| `current_bed_states` | `0012_observation` | Sin `alert_level`, `sleeping` nullable sin default |
| `sleep_summaries` | `0013_observation_summaries` | |
| `mobility_summaries` | `0013_observation_summaries` | |
| `bathroom_summaries` | `0013_observation_summaries` | |

Indice parcial `idx_sensor_events_unresolved` sobre `monitor_key WHERE bed_id IS
NULL`: contar la evidencia huerfana tiene que ser trivial, porque es una
superficie que alguien tiene que mirar.

## Cruces, todos en `mana-app`

| Necesita | De | Para que |
| --- | --- | --- |
| `monitor_key` -> cama | Residencia | Resolver la ingesta |
| cama -> residente | Poblacion | Atribuir el evento y la proyeccion |
| habitaciones, camas, planograma | Residencia | Componer el board |
| nombres de residentes | Poblacion | Componer el board y el companion |
| escribir auditoria | Auditoria | Traza de `room.peeked` |

Ninguno es un `use ctx_*` dentro de `mana-observation`.
