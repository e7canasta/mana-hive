# Modelos por contexto

Cada documento define una frontera de lenguaje, sus agregados, las tablas que
posee, invariantes, APIs y cruces permitidos. La tabla es exhaustiva para el
Registro nuevo.

El ownership tambien esta disponible para tooling en
[`ownership.toml`](ownership.toml). Una tabla sin owner o con dos owners debe
romper CI.

| Documento | Ownership principal |
| --- | --- |
| [`ctx-identidad.md`](ctx-identidad.md) | `users`, `auth_sessions` |
| [`ctx-auditoria.md`](ctx-auditoria.md) | `audit_log` |
| [`ctx-residencia.md`](ctx-residencia.md) | facilities, wings, rooms, beds, planogram, privacy |
| [`ctx-poblacion.md`](ctx-poblacion.md) | residents, assignments, resident attributes |
| [`ctx-cobertura.md`](ctx-cobertura.md) | staff groups, memberships, shifts, coverage |
| [`ctx-cuidado.md`](ctx-cuidado.md) | rounds, round tasks, care notes |
| [`ctx-historia.md`](ctx-historia.md) | incident detections, incident reviews |
| [`ctx-politica.md`](ctx-politica.md) | temporal alarm profiles, catalog |
| [`ctx-vigilancia.md`](ctx-vigilancia.md) | alerts, transitions, deliveries |
| [`observacion.md`](observacion.md) | sensor evidence, hot projections, daily summaries |
| [`plataforma.md`](plataforma.md) | process configuration, no generic business table |

Las vistas de board, timeline, Companion y detalle de residente son read models
compuestos en `mana-app`. No tienen ownership de una tabla de pantalla.
