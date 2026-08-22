# Modelo de dominio: `ctx-politica`

## Pregunta del contexto

Que reglas de alarma son efectivas para un residente y que configuracion estaba
vigente cuando ocurrio un evento?

## Objetos de dominio

```text
AlarmProfileVersion (agregado raiz, inmutable)
  id: ProfileId
  resident_id: String (opaco, sin FK)
  valid_from: Instante
  valid_to: Option<Instante>
  mobility_aid: MobilityAid (none | cane | walker | wheelchair)
  autopilot: bool
  mode: Mode (standard | enhanced | intensive)
  template_id: String
  overrides_json: String (JSON validado contra catalogo)
  catalog_version: String
  updated_by: Option<Id<Actor>>
  created_at: Instante

AlarmCatalog (value object, TOML)
  version: String
  groups: Vec<RuleGroup>
  rules: Vec<AlarmRule>
  presets: Vec<Preset>
  templates: Vec<Preset>
```

## Invariantes

| # | Invariante | Enforcement |
|---|-----------|-------------|
| 1 | Un perfil esta ligado a `ResidentId`, nunca a `BedId` | Dominio puro |
| 2 | Como maximo una version es valida para un residente en un instante | Indice UNIQUE parcial `WHERE valid_to IS NULL` |
| 3 | Un dia sin observacion no es un dia con observacion cero | Documentacion |
| 4 | Una regla `fall` bloqueada no puede desactivarse | Catalogo: `blocked = true` |
| 5 | Un override debe referir a una regla de catalogo y un parametro declarado | Validacion contra catalogo |
| 6 | Parametros enum, multivalor y numericos se validan contra el catalogo | Validacion contra catalogo |
| 7 | `day` y `night` son momentos fisiologicos, no turnos laborales | Documentacion |
| 8 | Aplicar un perfil nuevo conserva la version anterior | Transaccion: cierra anterior, crea nueva |
| 9 | Una recomendacion puede aplicarse como version nueva, nunca como observacion | Modelo: no hay prediccion persistida |

## Tablas

### `alarm_profile_versions`

```text
id               TEXT PRIMARY KEY
resident_id      TEXT NOT NULL
valid_from       TEXT NOT NULL
valid_to         TEXT NULL
mobility_aid     TEXT NOT NULL       -- none | cane | walker | wheelchair
autopilot        INTEGER NOT NULL    -- 0 | 1
mode             TEXT NOT NULL       -- standard | enhanced | intensive
template_id      TEXT NOT NULL
overrides_json   TEXT NOT NULL DEFAULT '{}'
catalog_version  TEXT NOT NULL
updated_by       TEXT NULL
created_at       TEXT NOT NULL
```

Indice unico parcial: `(resident_id) WHERE valid_to IS NULL`
Indice: `(resident_id, valid_from, valid_to)` para consultas `at`.

## Catalogo TOML

El catalogo se carga desde `config/alarm-catalog.toml` al arrancar. Contiene:

- `version`: version del catalogo (se guarda con cada perfil).
- `groups`: grupos de reglas (falls, movement, vitals).
- `rules`: reglas con parametros, acciones y metadata de calibracion.
  - `blocked: true` impide desactivar la regla via override.
  - `params`: tipo, rango, valores validos, default.
- `presets`: configuraciones predefinidas (standard, high_risk, low_risk).
- `templates`: plantillas para nuevos residentes (default, mobility_impaired,
  cognitive_impaired).

## Resolucion de politica

```text
catalog preset
    -> resident template
        -> manual override
            -> effective rules
```

Cada regla efectiva informa su capa de origen. El algoritmo que predice riesgo
desde 14 dias de resumenes observados pertenece a Percepcion; este contexto
consume el resultado mediante un puerto y no lo persiste como hecho de autoridad.

## Subdominios

- `perfiles`: `AlarmProfileVersion`, `PerfilesRepo` (apply_in_transaction,
  get_current, get_at, list_history).
- `catalogo`: `AlarmCatalog`, `AlarmRule`, `Preset`, `RuleGroup` (parse,
  validate_override, search_rules).

## Puertos entre contextos

- señales observadas del residente desde Percepcion;
- existencia del residente desde Poblacion;
- capacidad de auditoria.

## Tests

- La carga rechaza definiciones desconocidas o fuera de rango.
- Historia de versiones y consultas `at`.
- No hay versiones solapadas.
- Los tests de propiedades nunca desactivan `fall`.
- Todo parametro efectivo es valido para su regla de catalogo.
- La recomendacion ignora dias faltantes en vez de tratarlos como cero.
- Mudar un residente no altera la historia de politica.
- La salida sigue compatible donde la forma actual del cliente es clara.

## No posee

- implementacion de prediccion analitica;
- evidencia de sensores;
- alertas o registros de entrega;
- turnos laborales;
- diagnostico clinico.
