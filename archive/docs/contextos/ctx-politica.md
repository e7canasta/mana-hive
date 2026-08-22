# Contexto `ctx-politica`

Clase: nucleo.

## Pregunta

Que reglas de alarma son efectivas para un residente y que configuracion estaba
vigente cuando ocurrio un evento?

## Lenguaje y ownership

Este contexto posee la politica de alarmas del residente, las versiones
temporales de perfiles, plantillas, overrides y catalogo de reglas. El catalogo
es dato de politica, no codigo Rust.

La recomendacion de riesgo actual se deriva de señales observadas. Un nivel
predicho no se persiste como si fuera un hecho clinico.

El contexto no posee el evento de sensor, la alerta creada por una regla ni el
algoritmo analitico que calcula señales.

## Agregados

### `AlarmProfileVersion`

Version inmutable de un perfil de residente. Un cambio nuevo cierra la version
anterior y crea otra. El perfil actual es la version con
`valid_to IS NULL`.

### `AlarmCatalog`

Datos TOML cargados y validados. Son un artefacto de despliegue y un value
object para resolver politica, no un agregado de base de datos.

## Tablas

### `alarm_profile_versions`

```text
id               TEXT PRIMARY KEY
resident_id      TEXT NOT NULL
valid_from       TEXT NOT NULL
valid_to         TEXT NULL
mobility_aid     TEXT NOT NULL
autopilot        INTEGER NOT NULL
mode             TEXT NOT NULL
template_id      TEXT NOT NULL
overrides_json   TEXT NOT NULL DEFAULT '{}'
catalog_version  TEXT NOT NULL
updated_by       TEXT NULL
created_at       TEXT NOT NULL
```

El JSON se permite porque los parametros de overrides vienen del catalogo y
pueden variar por regla. Nunca se acepta sin validar: version de catalogo, rule
ID, accion, tipo y rango se verifican antes de persistir.

Hay como maximo una version valida para un residente en un instante. El
solapamiento se rechaza en el dominio y en la transaccion del repositorio.

### Catalog file

```text
hub/config/alarm-catalog.toml
```

Contiene grupos, IDs de reglas, schemas de parametros, plantillas, reglas
bloqueadas y metadata de calibracion. Se valida al arrancar y su version se
guarda con cada version de perfil.

## Resolucion de politica

```text
catalog preset
    -> resident template
        -> manual override
            -> effective rules
```

Cada regla efectiva informa su capa de origen. El algoritmo que predice riesgo
desde 14 dias de resumenes observados es un motor puro de `mana-motores`; la
hidratacion de señales pertenece a `mana-app`. Este contexto posee los datos de
politica y los perfiles, pero no consulta Observacion ni persiste la prediccion
como hecho de autoridad.

## Invariantes

1. Un perfil esta ligado a `ResidentId`, nunca a `BedId`.
2. Como maximo una version es valida para un residente en un instante.
3. Un dia sin observacion no es un dia con observacion cero.
4. Una regla `fall` bloqueada no puede desactivarse por preset, plantilla u override.
5. Un override debe referir a una regla de catalogo y un parametro declarado.
6. Parametros enum, multivalor y numericos se validan contra el catalogo.
7. `day` y `night` son momentos fisiologicos del residente, no turnos laborales.
8. Aplicar un perfil nuevo conserva la version anterior y su version efectiva de
   catalog version.
9. Una recomendacion puede aplicarse como una version nueva, pero nunca se guarda
   como si fuera observacion cruda.

## API

- `GET /api/v1/alarm-presets/catalog`
- `GET /api/v1/alarm-presets?q={query}`
- `GET /api/v1/alarm-presets/{residentId}`
- `GET /api/v1/alarm-presets/{residentId}?at={ISO instant}`
- `GET /api/v1/alarm-presets/{residentId}/history`
- `PATCH /api/v1/alarm-presets/{residentId}`
- `POST /api/v1/alarm-presets/apply-recommendations`
- `POST /api/v1/alarm-presets/autopilot`
- `POST /api/v1/alarm-presets/{residentId}/apply-recommendation`

La respuesta del perfil actual puede incluir `risk_level` y `recommendation`
derivados; son valores de read model. Un comando de escritura cambia entradas del
perfil, no una fila de prediccion inventada.

## Puertos entre contextos

- señales observadas del residente desde Percepcion;
- existencia del residente desde Poblacion;
- capacidad de auditoria;
- read model opcional de cobertura solo para decisiones de notificacion que no
  afectan la resolucion de politica `day`/`night`.

Vigilancia recibe reglas efectivas desde `mana-app` y no lee directamente la
tabla de perfiles.

## Tests

- la carga rechaza definiciones desconocidas o fuera de rango;
- historia de versiones y consultas `at`;
- no hay versiones solapadas;
- los tests de propiedades nunca desactivan `fall`;
- todo parametro efectivo es valido para su regla de catalogo;
- la recomendacion ignora dias faltantes en vez de tratarlos como cero;
- mudar un residente no altera la historia de politica;
- la salida sigue compatible donde la forma actual del cliente es clara.

## No posee

- hidratacion de señales de Observacion, Historia o Vigilancia;
- evidencia de sensores;
- alertas o registros de entrega;
- turnos laborales;
- diagnostico clinico.
