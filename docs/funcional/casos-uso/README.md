# Casos de uso por bounded context

Esta es la carpeta funcional del Registro. Un caso de uso describe una
capacidad de negocio y no una tabla, un endpoint ni una funcion Rust.

Cada documento responde:

- que objetivo tiene el actor;
- que estado debe existir antes;
- que reglas decide el dominio;
- que ocurre en el flujo normal y en los alternos;
- que queda garantizado despues;
- que parte lo realiza `mana-app` y que parte solo lo transporta.

## Casos activos

- [`ctx-identidad.md`](ctx-identidad.md): autenticacion, sesiones y cuentas de
  acceso.
- [`ctx-auditoria.md`](ctx-auditoria.md): registro y consulta de hechos
  auditables.
- [`ctx-residencia.md`](ctx-residencia.md): definicion de la estructura fisica.
- [`ctx-poblacion.md`](ctx-poblacion.md): residentes, atributos, asignaciones y
  egreso.
- [`ctx-cobertura.md`](ctx-cobertura.md): grilla laboral, grupos y coberturas.
- [`ctx-cuidado.md`](ctx-cuidado.md): rondas, tareas y notas de continuidad.
- [`ctx-historia.md`](ctx-historia.md): detecciones de incidentes y revisiones.
- [`ctx-politica.md`](ctx-politica.md): catalogo, perfiles y recomendaciones.
- [`ctx-vigilancia.md`](ctx-vigilancia.md): alertas, entregas y escalamiento.
- [`observacion.md`](observacion.md): evidencia del detector, estado actual y
  resumenes diarios. **No es un `ctx-*`**, es un subsistema de ciclo de vida de
  datos.

Con Observacion cerrada, el Registro completo se sirve desde Rust: la unica
entrada `node` que queda en `rutas.toml` es el comodin `OPTIONS *`.

## Regla de ownership

El caso de uso vive en el contexto que posee la decision de negocio. Si necesita
coordinar dos contextos, la coordinacion vive en `mana-app`, pero ningun contexto
importa al otro.

```text
caso de uso
  -> mana-app: actor, autorizacion, transaccion y cruces
  -> bounded context: reglas e invariantes propias
  -> store: persistencia del owner
  -> auditoria: hecho de mutacion, cuando corresponde
```

Los adaptadores tecnicos estan documentados en
[`../modulos/README.md`](../modulos/README.md). No tienen casos de uso de
negocio propios.
