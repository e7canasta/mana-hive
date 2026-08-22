# Motor de autopilot

## Objetivo de producto

Revisar automáticamente las recomendaciones de residentes que tienen autopilot
activo y aplicar solo decisiones permitidas por la política clínica.

## Estado

El motor puro vive en `crates/mana-motores/src/autopilot.rs` y el seam de
composicion vive en `mana-app/src/politica.rs`. `mana-app` hidrata la evidencia,
calcula la recomendacion y persiste solo una decision `Apply`. El scheduler
diario que dispara la operacion sigue siendo F11.2.

## API objetivo

```text
decidir(input: &AutopilotInput, policy: &AutopilotPolicy) -> AutopilotDecision
```

La entrada tendrá:

- `current_profile`: nivel actual y flag de autopilot;
- `recommendation`: nivel, puntaje y señales ya calculados;
- `last_change_at`: inicio de la version vigente;
- `now`: instante de la evaluacion.

La politica es dato del catalogo:

```toml
[autopilot]
minimum_signals_for_raise = 1
minimum_days_between_changes = 7
```

La salida distingue:

- `Keep`: no hay cambio, porque el nivel ya coincide o sigue vigente el cooldown;
- `Apply`: la subida tiene evidencia suficiente y puede crear una nueva version;
- `Propose`: la recomendacion baja el nivel y requiere confirmacion humana;
- `Skip`: autopilot esta apagado o no hay evidencia suficiente.

La decision tambien conserva el nivel actual, el recomendado, el puntaje, la
cantidad de senales y un motivo estable (`increase_allowed`,
`decrease_requires_confirmation`, etc.).

## Política de seguridad prevista

- subir automaticamente solo con `minimum_signals_for_raise` señales;
- bajar nunca sin confirmacion humana;
- respetar `minimum_days_between_changes` entre versiones;
- persistir una aplicacion con el actor `autopilot`; el motivo queda en la
  decision pura para la auditoria de la composicion.

## Contrato ejecutable

La demo pura verifica los tres caminos principales:

```bash
cargo run -p mana-motores --example autopilot
```

La escena `politica-blueprint` expone el resultado del motor en
`profiles[].autopilot_decision` y verifica una ejecucion sin escritura durante
el cooldown, ademas de la activacion y desactivacion global.
