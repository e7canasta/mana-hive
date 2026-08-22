# Funcion: `ctx-auditoria`

## Proposito

Conservar una explicacion inmutable de las mutaciones relevantes: que cambio,
quien lo hizo, sobre que entidad y en que instante.

Auditoria no decide si una operacion esta permitida ni posee las tablas de
negocio que originan los cambios.

## Funciones implementadas

- Registrar una entrada append-only.
- Validar accion, tipo, entidad y metadata.
- Limitar el tamano de metadata.
- Consultar por entidad, accion y limite.
- Enriquecer la lectura con el nombre actual del actor.
- Revertir la entrada automaticamente si la transaccion de negocio hace
  rollback.

## Entrada funcional

```text
actor_id opcional
action estable
entity_type
entity_id
metadata JSON
```

El timestamp lo asigna el servidor. No se acepta que el cliente decida el
instante de la auditoria.

## Acciones actuales

| Accion             | Origen               |
| ------------------ | -------------------- |
| `user.created`     | alta de usuario      |
| `user.updated`     | cambio de usuario    |
| `facility.created` | alta de residencia   |
| `facility.updated` | cambio de residencia |
| `wing.created`     | alta de ala          |
| `wing.updated`     | cambio de ala        |
| `room.created`     | alta de habitacion   |
| `room.updated`     | cambio de habitacion |
| `bed.created`      | alta de cama         |
| `bed.updated`      | cambio de cama       |

## Consulta

`GET /api/v1/audit-log` requiere `audit.read`.

Filtros actuales:

- `limit`, siempre acotado por servidor;
- `entity_type`;
- `entity_id`;
- `action`.

La respuesta conserva el actor como ID opaco y agrega `actor_name` como dato de
lectura. Si el usuario fue retirado, la auditoria sigue existiendo.

## Garantia transaccional

```text
BEGIN
  validar actor
  mutar contexto
  insertar audit_log
COMMIT
```

No debe existir una mutacion confirmada sin su entrada de auditoria cuando el
caso de uso la declara auditable.

## Endpoint

`GET /api/v1/audit-log` ya es publico desde Rust. La escritura no es un endpoint
independiente: ocurre desde `mana-app` como parte de los casos de uso.

## No es responsabilidad de auditoria

- Autenticar tokens.
- Autorizar operaciones.
- Reemplazar tablas de negocio.
- Servir analytics o un bus de eventos generico.

## Verificacion

Se prueban append-only, filtros, metadata invalida, limite de metadata y
rollback conjunto con la mutacion.
