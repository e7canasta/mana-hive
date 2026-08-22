# Funcion: `mana-motores`

## Proposito

Contener decisiones puras del producto: recibe una entrada completa y devuelve
una destilacion. No consulta datos ni persiste decisiones.

## Motores

- `catalogo`: resuelve preset, plantilla y overrides en reglas efectivas.
- `recomendacion`: propone nivel, puntaje, factores y plantilla.
- `alarmas`: decide qué alerta corresponde a una observacion o un barrido.
- `autopilot`: decide si una recomendacion se conserva, aplica, propone o salta.

El scheduler del reloj sigue pendiente; no forma parte de este crate porque
dispara el motor desde infraestructura durable.

## Dependencias

`mana-motores` depende de `mana-kernel`, serializacion y parseo del catálogo. No
depende de Diesel, SQLite, `mana-storage`, `mana-observation` ni ningún `ctx-*`.

## Entrada y salida

```text
mana-app
  -> entrada hidratada
  -> mana-motores
  -> decision o destilacion
  -> mana-app persiste en el contexto dueño
```

Los tipos de Vigilancia no salen del crate. `mana-app` traduce los niveles y tipos
de evidencia al persistir una alerta.

## Tests y demos

- Tests puros: `cargo test -p mana-motores`.
- Demo de recomendación: `cargo run -p mana-motores --example recomendacion`.
- Demo de alarmas: `cargo run -p mana-motores --example alarmas`.
- Demo de autopilot: `cargo run -p mana-motores --example autopilot`.
- Escena de integración del lazo: `motores-alarmas-blueprint.json`.
- Escena de envelope y autopilot: `politica-blueprint.json`.
