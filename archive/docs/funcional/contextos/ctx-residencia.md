# Funcion: `ctx-residencia`

## Proposito

Representar la estructura fisica en la que opera el sistema:

```text
Facility
  -> Wing
      -> Room
          -> Bed
```

Tambien reserva el ownership futuro de planograma, privacidad, streams de
camara y vinculaciones de monitores.

## Funciones implementadas en F2.1

- Crear y leer facilities.
- Consultar el detalle de una facility con sus wings activas.
- Crear, listar y actualizar wings.
- Crear, listar y actualizar rooms.
- Crear, listar y actualizar beds.
- Asignar un `stream_key` opcional a una room.
- Asignar un `monitor_key` opcional a una bed.
- Ocultar hijos de padres retirados en lecturas activas.
- Mantener cada mutacion con auditoria.

## Reglas de negocio

| Regla                        | Comportamiento                                               |
| ---------------------------- | ------------------------------------------------------------ |
| Nombre, piso, numero y label | No pueden quedar vacios                                      |
| Facility                     | Requiere nombre y timezone; HTTP usa `UTC` por default       |
| Wing                         | `sort_order` no puede ser negativo                           |
| Room                         | `type` default de aplicacion: `single`                       |
| Numero de room               | Unico dentro de una wing activa                              |
| `StreamKey`                  | Unico entre rooms activas                                    |
| `MonitorKey`                 | Unico entre beds activas                                     |
| Padres                       | No se puede crear un hijo de un padre inexistente o retirado |
| Tipos de dispositivo         | `StreamKey` y `MonitorKey` son newtypes distintos            |

## Casos de lectura

- Listar facilities activas.
- Obtener una facility activa y sus wings activas.
- Listar wings activas.
- Listar rooms de una wing activa.
- Listar beds de una room activa.

Todas las lecturas exigen `master.structure.read`.

## Casos de escritura

- Crear o actualizar facility.
- Crear o actualizar wing.
- Crear o actualizar room.
- Crear o actualizar bed.

Todas las escrituras exigen `master.structure.write` y producen auditoria con
la accion y entidad correspondiente.

## Contrato de rutas preparado

Los handlers Rust estan registrados para:

- `facilities.list.get`;
- `facilities.detail.get`;
- `facilities.create.post`;
- `facilities.update.patch`;
- `wings.list.get`;
- `facilities.wings.create.post`;
- `wings.update.patch`;
- `wings.rooms.get`;
- `wings.rooms.create.post`;
- `rooms.update.patch`;
- `rooms.beds.get`;
- `rooms.beds.create.post`;
- `beds.update.patch`.

La tabla publica todavia marca estas rutas como Node. Por eso este documento
describe una capacidad Rust aislada, no una migracion publica terminada.

## Estado de retiro

El modelo y las consultas conocen `retired_at` y `retired_by`, y las pruebas
verifican que un padre retirado no expone sus hijos. El comando de retiro
explicito aun no esta expuesto en `mana-app` ni en HTTP.

## Fuera de F2.1

- Planograma.
- Regiones de privacidad.
- Ocupacion de camas.
- Estado actual de sensores.
- Board y Companion.
- Mirror de datos desde la SQLite de Node.

## Verificacion

El test HTTP Rust crea facility, wing, room y bed, luego lee la jerarquia y la
auditoria. Los tests de store cubren unicidad, padres invalidos y ocultamiento de
hijos de padres retirados.
