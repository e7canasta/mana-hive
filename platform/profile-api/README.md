# profile-api

**El contrato de entrada del Politica Engine.**

Este modulo define, en tipos, el perfil completo de un residente tal como lo
tiene que entregar el sistema de registro. No depende de nada mas que la stdlib
de Kotlin: el equipo que implemente el sistema de registro compila contra este
jar y no tiene que deducir la estructura de un ejemplo.

```
com.manahive:profile-api:1.0.0-SNAPSHOT
```

## Las dos vias, ambas obligatorias

| Via | Para que |
|---|---|
| **Evento** `ResidentProfileChanged` | novedad cuando el perfil cambia — trae el perfil entero |
| **API** `ProfileEndpoints` | consulta para el arranque en frio y para auditoria |

Sin la segunda no hay recuperacion despues de un reinicio.

## La regla que gobierna todo

> Cada version del perfil es **completa e inmutable**. No hay deltas, no hay
> parches, no hay capas. Llega un perfil, se pisa el anterior, se reinterpreta
> todo. La version anterior sigue existiendo y sigue siendo consultable.

Ver `docs/roadmap/SPEC-02-perfil-del-residente.md` para el diseño completo.
