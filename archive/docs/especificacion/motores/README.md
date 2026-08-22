# Especificacion de motores

Los motores son componentes de dominio que convierten evidencia ya hidratada en
una decision. No conocen HTTP, Diesel, SQLite ni los bounded contexts que
producen la evidencia.

## Regla de diseño

```text
contextos de datos
        -> mana-app: hidratacion y transaccion
        -> mana-motores: decision pura
        -> mana-app: persistencia de la destilacion
        -> transporte o UI
```

`mana-motores` no depende de `ctx-*`, `mana-observation`, `mana-storage` ni
`mana-app`. Cargo hace cumplir la frontera. `ctx-politica` reexporta los tipos de
valor del catalogo para conservar su API interna, pero la implementacion pura
vive en `mana-motores`.

## Motores

| Motor | API pura | Estado | Ejemplo de producto |
|---|---|---|---|
| Politica efectiva | `AlarmCatalog::resolve_rules` | Completo | Que reglas quedan activas para una persona |
| Recomendacion | `recomendar` | Completo | Sugerir nivel y explicar los factores |
| Alarmas | `evaluar` | Completo para eventos y barridos recibidos | Crear una alerta cuando sale de la cama |
| Autopilot | `decidir` | Completo en `mana-motores`; scheduler pendiente | Aplicar automaticamente una subida respaldada |
| Reloj | scheduler + evaluacion de permanencias | Especificado, pendiente | Avisar aunque no llegue otro evento |

## Demos locales

```bash
cargo run -p mana-motores --example recomendacion
cargo run -p mana-motores --example alarmas
cargo run -p mana-motores --example autopilot
```

Las demos no arrancan el hub ni consultan una base. Muestran el contrato de cada
motor con una entrada pequeña y una salida legible para producto.

## Escenas

- `crates/mana-sdk/scenes/politica-blueprint.json`: catalogo, perfil efectivo e historial.
- `crates/mana-sdk/scenes/observacion-blueprint.json`: evidencia que alimenta los motores.
- `crates/mana-sdk/scenes/motores-alarmas-blueprint.json`: evento de cama que produce una alerta.
- `crates/mana-sdk/scenes/politica-blueprint.json`: catalogo, envelope de perfiles,
  recomendacion, reglas efectivas y decision de autopilot.

## Regla de crecimiento

Cada motor se prueba dos veces:

1. Decisión pura con entrada escrita a mano.
2. Hidratación contra una base, sin repetir la lógica de decisión.

Si falla la primera, falla el motor. Si falla la segunda, falla la composición de
`mana-app`.
