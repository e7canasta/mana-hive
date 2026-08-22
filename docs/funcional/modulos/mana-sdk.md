# Funcion: `mana-sdk`

## Proposito

Ofrecer un cliente Rust para consumir el contrato del hub y ejecutar escenas
reproducibles de aceptacion.

## Funciones implementadas

- Transporte HTTP con base URL y bearer.
- Cliente de identidad.
- Cliente de auditoria.
- Cliente de residencia F2.1.
- Cliente de poblacion F3.
- Cliente de cobertura F4.
- Cliente de cuidado F4.
- Cliente de historia F5.
- Cliente de politica F6.
- Cliente de vigilancia F7.
- Errores de transporte y API.
- Runner declarativo de escenas JSON (con acciones de identidad, residencia,
  poblacion, cobertura, cuidado y HTTP raw).

## Cliente de residencia

Expone operaciones para:

- listar y consultar facilities;
- crear y actualizar facilities;
- listar, crear y actualizar wings;
- listar, crear y actualizar rooms;
- listar, crear y actualizar beds.

Las funciones construyen paths con IDs escapados y rechazan IDs vacios o con
separadores de path.

## Cliente de poblacion

Expone operaciones para:

- listar y consultar residentes (con filtro `q`);
- crear y actualizar residentes;
- egresar residentes;
- listar y crear asignaciones residente-cama;
- liberar camas.

## Cliente de politica

Expone operaciones para:

- consultar el catalogo de alarmas;
- buscar presets por filtros;
- obtener un preset por ID o en una fecha concreta;
- listar el historial de versiones de un preset;
- actualizar un preset;
- aplicar recomendaciones o activar autopilot.

## Cliente de historia

Expone operaciones para:

- ingerir detecciones de incidentes (con `x-clinical-secret`);
- listar incidentes de un residente;
- obtener la secuencia completa de un incidente;
- crear revisiones sobre un incidente.

## Escenas

Una escena define:

- metadata e ID;
- contexto;
- comandos HTTP canonicos;
- status esperado;
- assertions sobre la respuesta.

El runner valida la escena antes de ejecutarla y no imprime tokens en reportes.

Escenas publicas:

- `identidad-smoke`: identidad y auditoria.
- `residencia-blueprint`: cubre RES-01..RES-08 end-to-end contra el hub Rust
  (facilities, wings, rooms, beds, planograma, privacidad y auditoria),
  incluyendo caminos de error (409 duplicados, 404 de otro ala, 422 region
  invalida). Requiere base fresca: los casos de duplicado siembran datos.
- `poblacion-blueprint`: cubre POB-01..POB-08 end-to-end (residentes,
  asignaciones, egreso, liberacion y auditoria), incluyendo caminos de error
  (404 inexistente, 409 cama libre, 409 ya egresado, 422 fecha invalida,
  422 egreso antes del alta). Requiere base fresca; siembra su propia
  estructura via acciones de residencia.

- `politica-blueprint`: cubre catalogo, presets, perfiles, versiones y autopilot.

- `vigilancia-blueprint`: cubre alertas, transiciones, entregas y auditoria de acceso.

Los comandos pueden capturar respuestas (`capture`) y referenciarlas en
comandos posteriores como `{{ nombre.ruta }}`; los errores HTTP esperados se
declaran con `status` y no abortan la escena. El CLI `mana` vive en
`mana-cli` y consume este cliente.

## No hace

- Conocer filas de SQLite.
- Repetir reglas de dominio del servidor.
- Generar DTOs desde Rust automaticamente.
- Decidir si una ruta debe migrarse.

## Observacion

`ManaClient` expone board, estado actual, eventos, resumenes, linea de tiempo,
reporte, companion y peek, mas la ingesta.

Las rutas `/internal/` **no usan sesion**: autentican al bridge con
`x-clinical-secret`. Por eso van por `request_json_with_headers` y no por el
camino con bearer — es un canal distinto y conviene que se note en el tipo.

Escena: `scenes/observacion-blueprint.json`, 27 pasos, incluye idempotencia del
reintento, `monitor_key` sin vincular, y los rechazos de rango de los resumenes.

## Verificacion

Los tests cubren base path, redaccion de tokens, validacion de acciones,
assertions por path y errores de transporte.
