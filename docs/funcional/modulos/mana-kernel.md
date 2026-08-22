# Funcion: `mana-kernel`

## Proposito

Proveer el vocabulario transversal minimo que todos los contextos pueden usar sin
introducir reglas de negocio.

## Responsabilidades

- IDs tipados y opacos.
- Instantes UTC.
- Actor tipado para auditoria y retiro.
- Errores publicos mediante `Fallo`.
- Utilidades transversales de retiro.

## Reglas

- Un `UserId` no se usa como `RoomId` aunque ambos se serialicen como strings.
- Los IDs publicos no exponen una estructura interna al cliente.
- Los errores HTTP usan los codigos de `Fallo`, no literales aislados por modulo.
- El kernel no conoce users, rooms, residents ni tablas Diesel.

## Flujo con los otros modulos

```text
ctx-* -> mana-kernel para IDs, tiempo y errores
mana-app -> mana-kernel para mapear fallos
mana-http -> mana-kernel para el envelope publico
```

## No es responsabilidad de kernel

- Autenticar.
- Autorizar capabilities.
- Abrir SQLite.
- Definir endpoints.
- Conocer cualquier agregado de negocio.
