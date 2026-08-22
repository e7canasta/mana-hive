# Casos de uso: `ctx-politica`

## Frontera funcional

`ctx-politica` es propietario de la politica de alarmas del residente, las
versiones temporales de perfiles, plantillas, overrides y catalogo de reglas.
El catalogo es dato de politica, no codigo Rust. Nunca importa otros `ctx-*`;
los IDs de residente son opacos y la validacion de existencia vive en
`mana-app`.

## Reglas del contexto

- Un perfil esta ligado a `ResidentId`, nunca a `BedId`.
- Como maximo una version es valida para un residente en un instante.
- Un dia sin observacion no es un dia con observacion cero.
- Una regla `fall` bloqueada no puede desactivarse por preset, plantilla u override.
- Un override debe referir a una regla de catalogo y un parametro declarado.
- Parametros enum, multivalor y numericos se validan contra el catalogo.
- `day` y `night` son momentos fisiologicos del residente, no turnos laborales.
- Aplicar un perfil nuevo conserva la version anterior y su version efectiva de
  catalog version.
- Una recomendacion puede aplicarse como una version nueva, pero nunca se guarda
  como si fuera observacion cruda.

## POL-01 - Obtener catalogo de alarmas

**Objetivo:** Consultar el catalogo de reglas, grupos, presets y plantillas
disponibles.

**Actor primario:** Cualquier cliente (sin autenticacion).

**Disparador:** HTTP GET `/api/v1/alarm-presets/catalog`.

**Flujo principal:**

1. El contexto lee el catalogo TOML cargado al arrancar.
2. Devuelve la version, grupos, reglas, presets y plantillas.

**Postcondiciones:** El catalogo es read-only y no se modifica.

## POL-02 - Buscar presets

**Objetivo:** Buscar reglas y presets por texto libre.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP GET `/api/v1/alarm-presets?q={query}`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El cliente envia una query de busqueda.
2. El contexto filtra reglas por ID, descripcion o grupo.
3. Devuelve el catalogo filtrado.

## POL-03 - Obtener perfil actual

**Objetivo:** Consultar la version vigente de la politica de alarmas de un
residente.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP GET `/api/v1/alarm-presets/:residentId`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El contexto busca la version con `valid_to IS NULL`.
2. Si existe, devuelve el perfil; si no, devuelve `null`.

## POL-04 - Actualizar perfil

**Objetivo:** Crear una nueva version de la politica de alarmas de un residente.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP PATCH `/api/v1/alarm-presets/:residentId`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El cliente envia los campos a actualizar (mobility_aid, autopilot, mode,
   template_id, overrides_json, catalog_version).
2. El contexto cierra la version actual (si existe) seteando `valid_to`.
3. Crea una nueva version con `valid_to = NULL`.
4. Devuelve la nueva version.

**Postcondiciones:** La version anterior queda preservada con su `valid_to`.

## POL-05 - Historial de perfiles

**Objetivo:** Consultar todas las versiones de la politica de alarmas de un
residente.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP GET `/api/v1/alarm-presets/:residentId/history`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El contexto lista todas las versiones ordenadas por `valid_from`.
2. Devuelve la lista completa con sus vigencias.

## POL-06 - Aplicar recomendacion individual

**Objetivo:** Aplicar una recomendacion de plantilla o overrides a un residente
especifico.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP POST `/api/v1/alarm-presets/:residentId/apply-recommendation`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El cliente envia template_id, overrides_json y catalog_version opcionales.
2. El contexto cierra la version actual y crea una nueva con los parametros
   proporcionados.
3. Devuelve la nueva version.

## POL-07 - Aplicar recomendaciones bulk

**Objetivo:** Aplicar recomendaciones a multiples residentes en una sola
operacion.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP POST `/api/v1/alarm-presets/apply-recommendations`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El cliente envia una lista de recomendaciones.
2. El contexto procesa cada una como POL-06.
3. Devuelve la lista de perfiles actualizados.

## POL-08 - Autopilot

**Objetivo:** Renovar perfiles de residentes que tienen autopilot activado.

**Actor primario:** Staff autenticado.

**Disparador:** HTTP POST `/api/v1/alarm-presets/autopilot`.

**Precondiciones:** Sesion autenticada.

**Flujo principal:**

1. El contexto busca residentes activos con `autopilot = true`.
2. `mana-app` hidrata las senales y calcula la recomendacion del dia.
3. `mana-motores::decidir` aplica solo una subida con evidencia suficiente y
   fuera del cooldown.
4. Una bajada queda como propuesta y una ventana sin evidencia se salta; ninguna
   de las dos crea una version automaticamente.
5. Devuelve la lista de perfiles aplicados.
