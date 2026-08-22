# Funcion: `mana-app`

## Proposito

Coordinar los casos de uso, aplicar autorizacion y cruzar contextos dentro de
transacciones SQLite. Es la unica capa que puede importar varios `ctx-*`.

Los motores puros no viven aquí: `mana-app` hidrata sus entradas, invoca
`mana-motores` y persiste la destilación en el contexto propietario.

## Entrada y salida

`mana-app` no conoce JSON ni Axum. Recibe commands y devuelve views o
`AppFailure`.

```text
mana-http
  -> Command
  -> mana-app
  -> View o AppFailure
```

## Casos coordinados

| Caso                               | Contextos cruzados                             |
| ---------------------------------- | ---------------------------------------------- |
| Login, me y logout                 | identidad                                      |
| Listar usuarios                    | identidad                                      |
| Crear usuario                      | identidad + auditoria                          |
| Actualizar usuario                 | identidad + auditoria                          |
| Consultar auditoria                | identidad + auditoria                          |
| Crear o actualizar residencia      | identidad + residencia + auditoria             |
| Leer residencia                    | identidad + residencia                         |
| Crear/actualizar/egresar residente | identidad + poblacion + auditoria              |
| Asignar cama                       | identidad + poblacion + residencia + auditoria |
| Liberar cama                       | identidad + poblacion + auditoria              |
| Grilla de turnos                   | identidad + cobertura                          |
| Grupos de staff y miembros         | identidad + cobertura                          |
| Cobertura de alas                  | identidad + cobertura                          |
| Rondas y tareas                    | identidad + cuidado + poblacion + residencia   |
| Notas de cuidado                   | identidad + cuidado                            |
| Ingestar incidente                 | historia (clinical secret)                     |
| Incidentes de un residente         | identidad + historia                           |
| Revisar incidente                  | identidad + historia + auditoria               |
| Secuencia de incidente             | identidad + historia                           |
| Catalogo de alarmas                | politica (TOML)                                |
| Busqueda de presets                | identidad + politica                           |
| Perfil de alarmas                  | identidad + politica                           |
| Actualizar perfil                  | identidad + politica + auditoria               |
| Recomendaciones                    | identidad + politica                           |
| Autopilot                          | identidad + politica + poblacion               |
| Crear alerta                       | identidad + vigilancia                         |
| Listar alertas                     | identidad + vigilancia                         |
| Transicionar alerta                | identidad + vigilancia                         |
| Auditar acceso a alerta            | identidad + vigilancia + auditoria             |
| Crear entrega de notificacion      | identidad + vigilancia                         |
| Agregar evento a entrega           | identidad + vigilancia                         |
| Listar entregas                    | identidad + vigilancia                         |

## Transaccion de mutacion

Para una mutacion protegida:

1. Resuelve el bearer dentro de la conexion de la transaccion.
2. Comprueba la capability requerida.
3. Ejecuta el comando del contexto propietario.
4. Crea la entrada de auditoria cuando corresponde.
5. Confirma todo junto o revierte todo.

El cruce identidad + auditoria + residencia + poblacion no se implementa como
dependencia Cargo entre contextos. Se coordina desde este modulo.

## Autorizacion actual

- lectura de estructura: `master.structure.read`;
- escritura de estructura: `master.structure.write`;
- lectura de auditoria: `audit.read`.

La capability no viene del body del cliente. Se deriva del actor autenticado y
de la configuracion activa.

## Frontera sync/async

Diesel es sync. `mana-app` usa `spawn_blocking` una vez para ejecutar las
operaciones SQLite y no bloquea el runtime async con consultas directas.

## No hace

- Parsear requests HTTP.
- Serializar respuestas wire.
- Exponer filas Diesel.
- Permitir que un `ctx-*` importe otro `ctx-*`.

## Los cruces, que son la razon de existir de este crate

`AppState::transaction` recibe `(&mut SqliteConnection, &Stores)`. `Stores`
agrupa los nueve stores de contexto mas Observacion: **agregar un contexto es
agregar un campo**, y ningun caso de uso existente se entera. La firma anterior
tomaba cinco stores posicionales, la mayoria de los call sites ignoraba alguno,
y los contextos incorporados despues de F3 no podian participar de una
transaccion.

| Caso coordinado | Contextos que cruza |
| --- | --- |
| Asignar y liberar cama | Poblacion + Residencia + Auditoria |
| Egreso | Poblacion + Auditoria |
| Ingerir un evento del detector | Observacion + Residencia + Poblacion |
| Board del ala | Observacion + Residencia + Poblacion |
| Estado actual y eventos del residente | Observacion + Poblacion |
| Habitaciones del companion | Residencia + Poblacion |
| Autorizar mirada al stream | Residencia + Auditoria |
| Resumen de residencia | Poblacion + Residencia + Observacion |

La resolucion `monitor_key -> cama -> residente` ocurre **dentro** de la
transaccion que escribe el evento: si pasara afuera, dos eventos concurrentes
podrian proyectar con ocupantes distintos.

## Verificacion

Los tests de aplicacion cubren login, autorizacion, alta y actualizacion de
usuarios, auditoria, rollback, el flujo de residencia y el flujo de poblacion
(residentes, asignaciones transaccionales, egreso).
