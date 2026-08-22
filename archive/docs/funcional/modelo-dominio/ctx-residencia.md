# Modelo de dominio: `ctx-residencia`

## Pregunta del contexto

Como esta compuesto el hogar y que espacio, cama y dispositivo existe en cada
unidad?

## Entidades y fronteras

```text
Facility
  | 1
  +---- 0..* Wing
              | 1
              +---- 0..* Room
                            | 1
                            +---- 0..* Bed

WingPlanogram (raiz: WingId)
  +---- 0..* placement activo -> Room

RoomPrivacyConfig (raiz: RoomId)
  +---- 0..8 region activa
```

La jerarquia es una relacion de pertenencia, no un agregado gigante. Cada
entidad tiene su propia operacion de consistencia; el store valida el padre
activo al crear o leer hijos.

### `Facility`

Residencia operable. Su identidad funcional es nombre + timezone + ciclo de
retiro.

### `Wing`

Unidad ordenable dentro de una facility. Su identidad funcional agrega nombre,
piso y `sort_order`.

### `Room`

Espacio dentro de una wing. Tiene numero, tipo y una vinculacion opcional a
`StreamKey`.

### `Bed`

Cama fisica dentro de una room. Tiene label y una vinculacion opcional a
`MonitorKey`.

### `WingPlanogram`

Configuracion espacial de un ala: la posicion de cada habitacion en el plano
(agregado cuya raiz es `WingId`). Solo existe una version activa: guardar
desactiva la anterior e inserta la nueva, de modo que la lectura siempre ve el
ultimo envio completo. Una habitacion no puede aparecer dos veces en la version
activa.

### `RoomPrivacyConfig`

Configuracion de enmascaramiento de una habitacion (agregado cuya raiz es
`RoomId`): hasta `MAX_PRIVACY_REGIONS` (8) rectangulos normalizados sobre el
video. Tambien se guarda por reemplazo de la version activa; una version vacia
equivale a sin enmascaramiento.

### Value objects

| Tipo                                      | Significado                                |
| ----------------------------------------- | ------------------------------------------ |
| `FacilityId`, `WingId`, `RoomId`, `BedId` | IDs tipados y opacos                       |
| `StreamKey`                               | Identifica un stream de camara de una room |
| `MonitorKey`                              | Identifica un detector asociado a una bed  |
| `PlanogramPlacementInput`                 | Posicion `(x, y)` y `sort_order`           |
| `PrivacyRegionInput`                      | Rectangulo `(x, y, w, h)` normalizado      |
| `retired_at`, `retired_by`                | ciclo de vida tecnico, no ocupacion        |

`StreamKey` y `MonitorKey` no se pueden intercambiar. La ocupacion de una bed es
responsabilidad futura de `ctx-poblacion`.

## Invariantes

- nombres, pisos, numeros y labels no vacios;
- sort order no negativo;
- room number unico dentro de una wing activa;
- stream unico entre rooms activas;
- monitor unico entre beds activas;
- un hijo solo puede referenciar un padre activo;
- una lectura activa no devuelve una rama bajo un padre retirado;
- quitar un stream o monitor es explicito mediante `null`;
- el contexto no decide quien ocupa la cama;
- coordenadas del planograma finitas y `sort_order` no negativo;
- una habitacion no se repite en la version activa del planograma de su ala;
- regiones de privacidad finitas y normalizadas dentro de `0..1`
  (`x + w <= 1`, `y + h <= 1`, `w > 0`, `h > 0`);
- a lo sumo 8 regiones de privacidad por habitacion;
- planograma y regiones se guardan por reemplazo de la version activa.

## Mapeo a casos de uso

| Caso                        | Entidades                        | Regla principal                   | Servicio de aplicacion                    |
| --------------------------- | -------------------------------- | --------------------------------- | ----------------------------------------- |
| RES-01 Definir facility     | `Facility`                       | texto valido; padre de wings      | `create_facility`, `update_facility`      |
| RES-02 Organizar wing       | `Wing`, `FacilityId`             | facility activa; sort no negativo | `create_wing`, `update_wing`              |
| RES-03 Definir room/camara  | `Room`, `WingId`, `StreamKey`    | numero y stream unicos            | `create_room`, `update_room`              |
| RES-04 Definir bed/monitor  | `Bed`, `RoomId`, `MonitorKey`    | room activa; monitor unico        | `create_bed`, `update_bed`                |
| RES-05 Consultar estructura | todas las entidades              | solo rama activa                  | `list_*`, `facility_detail`               |
| RES-06 Planograma de un ala | `WingPlanogram`, `Room`          | version activa; room unica        | `planogram`, `save_planogram`             |
| RES-07 Privacidad de room   | `RoomPrivacyConfig`              | max 8 regiones normalizadas       | `privacy_regions`, `save_privacy_regions` |
| RES-08 Vista global         | proyecciones sobre Wing/Room/Bed | solo rama activa; `bed_count`     | `list_wings`, `list_residence_beds`       |

Cada escritura de RES-01 a RES-07 se cruza con auditoria en `mana-app`.

Detalle funcional: [`../casos-uso/ctx-residencia.md`](../casos-uso/ctx-residencia.md).

## Mapeo a modelo de datos

| Entidad             | Tabla                  | Columnas principales                                                     | Restricciones e indices                                               | Migracion             |
| ------------------- | ---------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------- | --------------------- |
| `Facility`          | `facilities`           | `id`, `name`, `timezone`, `retired_at`, `retired_by`, timestamps         | checks de texto                                                       | `0003_residencia`     |
| `Wing`              | `wings`                | `id`, `facility_id`, `name`, `floor`, `sort_order`, retiro, timestamps   | FK a facility; `sort_order >= 0`; indice facility/order               | `0003_residencia`     |
| `Room`              | `rooms`                | `id`, `wing_id`, `number`, `room_type`, `stream_key`, retiro, timestamps | FK a wing; unique parcial wing/number; unique parcial stream activo   | `0003_residencia`     |
| `Bed`               | `beds`                 | `id`, `room_id`, `label`, `monitor_key`, retiro, timestamps              | FK a room; unique parcial monitor activo                              | `0003_residencia`     |
| `StreamKey`         | `rooms.stream_key`     | texto nullable                                                           | unicidad parcial cuando room activa                                   | `0003_residencia`     |
| `MonitorKey`        | `beds.monitor_key`     | texto nullable                                                           | unicidad parcial cuando bed activa                                    | `0003_residencia`     |
| `WingPlanogram`     | `planogram_placements` | `id`, `wing_id`, `room_id`, `x`, `y`, `sort_order`, `active`, timestamps | FK a wing y room; una version activa por ala (guardado por reemplazo) | `0004_residencia_api` |
| `RoomPrivacyConfig` | `room_privacy_regions` | `id`, `room_id`, `x`, `y`, `w`, `h`, `active`, timestamps                | FK a room; una version activa por room; a lo sumo 8 activas           | `0004_residencia_api` |

La base usa foreign keys dentro del contexto para la jerarquia. La validacion de
activo no queda completa en la FK: la resuelven los comandos y las consultas del
store.

## Realizacion

- Dominio: `crates/ctx-residencia/src/estructura/` (agregados y repositorio de
  jerarquia), `planograma/` (WingPlanogram) y `privacidad/` (RoomPrivacyConfig);
  la fachada `ResidenceStore` compone los tres repositorios en `lib.rs`.
- Proyecciones: `crates/ctx-residencia/src/proyecciones/` (`ResidenceBed`,
  overview de alas con `bed_count`).
- Aplicacion: `crates/mana-app/src/residencia.rs`.
- Transporte: `crates/mana-http/src/residencia.rs`.
- Cliente: `crates/mana-sdk/src/residencia.rs`.
- Migraciones: `crates/ctx-residencia/migrations/0003_residencia/` (estructura)
  y `0004_residencia_api/` (planograma y privacidad).
