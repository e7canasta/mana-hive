# Funcion: `mana-storage`

## Proposito

Centralizar la infraestructura SQLite compartida sin apropiarse de las tablas ni
del dominio de ningun contexto.

## Responsabilidades

- Construir el pool Diesel `r2d2`.
- Proveer conexiones y errores de infraestructura.
- Aplicar PRAGMAs al adquirir cada conexion.
- Ejecutar migraciones embebidas de cada contexto.
- Usar un solo connection slot para `:memory:` en tests.

## Configuracion SQLite

Al adquirir una conexion se aplican:

- `foreign_keys = ON`;
- `journal_mode = WAL`;
- `busy_timeout = 5000`;
- `synchronous = NORMAL`.

El pool persistente usa hasta ocho conexiones. El pool en memoria usa una para
que las migraciones y los tests compartan la misma base.

## Migraciones

Cada contexto conserva sus migraciones, pero la base comparte la tabla global de
versiones de Diesel. Las versiones actuales son:

| Version           | Owner            | Contenido                       |
| ----------------- | ---------------- | ------------------------------- |
| `0001_identity`   | `ctx-identidad`  | users y auth_sessions           |
| `0002_audit`      | `ctx-auditoria`  | audit_log                       |
| `0003_residencia` | `ctx-residencia` | facilities, wings, rooms y beds |

`mana-app` ejecuta las migraciones en el arranque mediante `AppState::migrate`.

## No hace

- Definir columnas de negocio.
- Mapear DTOs HTTP.
- Abrir transacciones de casos de uso por su cuenta.
- Importar un contexto dentro de otro.

## Fallos funcionales visibles

Si no puede obtener una conexion o ejecutar una migracion, el proceso no se
considera correctamente inicializado. La aplicacion debe fallar temprano en vez
de arrancar con un esquema incompleto.

## Verificacion

El test propio comprueba el pool en memoria y el uso de una conexion unica. Los
tests de cada contexto comprueban sus migraciones sobre este modulo.
