# Contrato HTTP

## Decision

El limite entre el hub y sus clientes es un contrato OpenAPI independiente de
los lenguajes. `openapi.yaml` es el documento agregador:

```text
hub/docs/contrato/openapi.yaml
```

Cada contexto tiene su especificacion normativa autocontenida en
`modulos/<contexto>.yaml`; actualmente son:

```text
hub/docs/contrato/modulos/plataforma.yaml
hub/docs/contrato/modulos/identidad.yaml
hub/docs/contrato/modulos/auditoria.yaml
hub/docs/contrato/modulos/residencia.yaml
hub/docs/contrato/modulos/poblacion.yaml
hub/docs/contrato/modulos/cobertura.yaml
hub/docs/contrato/modulos/cuidado.yaml
hub/docs/contrato/modulos/historia.yaml
hub/docs/contrato/modulos/politica.yaml
hub/docs/contrato/modulos/vigilancia.yaml
hub/docs/contrato/modulos/observacion.yaml
```

El agregador referencia los path items y componentes de esos modulos sin
duplicar schemas. Cubre las nueve fases: plataforma, identidad, auditoria, residencia, poblacion,
cobertura, cuidado, historia, politica, vigilancia y observacion. No se declaran
operaciones que el hub no implementa, y la inversa tambien vale: **cada entrada
`sirve = "rust"` de `rutas.toml` tiene su operacion en el contrato.** Si
divergen, alguien migro una ruta sin documentarla.

Se escribe y revisa como especificacion de protocolo. No se genera desde Rust,
no se importa desde TypeScript y no depende de derives compartidos.

No se usan `utoipa`, `ts-rs` ni `schemars` como mecanismo de contrato.
`mana-wire` contiene DTOs manuales que mapean la especificacion a tipos Rust;
`packages/api-client` contiene clientes manuales y
`packages/contracts` validadores de runtime del lado TypeScript. Ambos son
consumidores independientes del mismo contrato.

## Capas del contrato

```text
OpenAPI manual
    |
    +-- mana-wire: DTOs serde escritos a mano
    +-- packages/contracts: schemas runtime del cliente
    +-- packages/api-client: operaciones HTTP
    +-- bridge / workers: consumidores no TypeScript
```

El dominio y las filas de Diesel no aparecen en OpenAPI.

## Reglas

- Cada operacion tiene `operationId` estable.
- Cada recurso tiene un schema de respuesta explicito.
- Los errores usan `{ error: { code, message, fields? } }`.
- Las fechas publicas son ISO-8601 UTC; las fechas de calendario son
  `YYYY-MM-DD`.
- Los IDs son strings opacos; el cliente no conoce su algoritmo.
- `Authorization: Bearer <token>` es la autenticacion publica.
- Los endpoints internos usan secretos o credenciales de servicio distintos.
- El cambio de almacenamiento no obliga a cambiar el contrato.
- El cambio de contrato se acompana con el cliente, ejemplos y escenas en el
  mismo cambio.

## Compatibilidad

La compatibilidad se prueba en tres niveles:

1. El archivo OpenAPI valida y pasa lint.
2. Las respuestas reales del hub cumplen los schemas de la especificacion.
3. `packages/api-client` y las escenas ejecutan las operaciones criticas sobre
   una base nueva.

La API Node se usa para comparar comportamiento cuando convenga, pero una
respuesta rara de Node no obliga a conservar un defecto de modelo.

## Versionado

La ruta publica empieza en `/api/v1`. Cambios aditivos no requieren nueva
version. Cambios semanticos incompatibles requieren una nueva operacion,
version de API o migracion coordinada del cliente. No se agrega una capa de
compatibilidad interna indefinida.
