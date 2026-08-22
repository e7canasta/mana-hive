# Funcion: `mana-wire`

## Proposito

Definir manualmente los DTOs que cruzan HTTP. Es la implementacion del contrato
en Rust, no el dominio y no las filas de SQLite.

## DTOs activos

- login y usuario autenticado;
- alta y actualizacion de usuario;
- facilities, wings, rooms y beds;
- entradas y respuestas de auditoria;
- envelope de error.

## Reglas de compatibilidad

- IDs como strings opacos.
- Instantes como ISO-8601 UTC.
- `active` de usuario conserva la forma historica wire `0/1`.
- `room_type` se expone como `type`.
- Campos opcionales y nullable se distinguen durante deserializacion.
- Requests con `deny_unknown_fields` rechazan payloads no declarados.

## Nullabilidad

En un PATCH hay diferencia entre:

- campo ausente: conservar el valor;
- campo presente con `null`: limpiar el valor nullable.

Esto se aplica a `job_title`, `stream_key` y `monitor_key`.

## Envoltorios

Las colecciones usan respuestas como `{ users }`, `{ facilities }`, `{ wings }`,
`{ rooms }`, `{ beds }` y `{ audit }`. Las actualizaciones usan wrappers de
recurso como `{ user }`, `{ facility }`, `{ wing }`, `{ room }` y `{ bed }` cuando
el contrato lo define.

Los DTOs no contienen metodos de negocio ni tipos Diesel.

## No hace

- Derivar capabilities.
- Validar que un padre exista.
- Ejecutar migraciones.
- Generar OpenAPI o tipos TypeScript automaticamente.

## Verificacion

Hay tests de serializacion de IDs, fallos, timestamps y de la diferencia entre
campo nullable ausente y null explicito.
