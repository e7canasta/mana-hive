# Plataforma del Registro

Tipo: soporte tecnico del proceso, no bounded context de negocio.

## Pregunta

Con que configuracion y parametros arranca el hub, y como se evita que una
clave generica cambie silenciosamente el comportamiento del sistema?

## Regla

No se crea una tabla `system_parameters(key, value_json)` para guardar
cualquier cosa. Un valor que modifica una regla importante debe tener un tipo,
un owner y una validacion de arranque.

## Configuracion

La configuracion operativa vive en capas:

1. Variables de entorno para rutas, puertos y secretos.
2. Archivo TOML versionado para catalogos y defaults de proceso.
3. Tablas propias de un contexto cuando un valor es realmente mutable por API.

Ejemplos de valores de proceso:

- ruta SQLite;
- `busy_timeout`;
- limites HTTP;
- umbrales de frescura de observacion;
- ventana por defecto de consultas;
- secreto del bridge y de ingestas.

Cada grupo se carga en un struct tipado (`MonitoringConfig`, `HttpConfig`,
`IngestConfig`, etc.) y falla al arrancar si falta, sobra o viola un rango.

## Sin ownership de negocio

- Los perfiles de alarma pertenecen a `ctx-politica`.
- El catalogo de reglas pertenece a `ctx-politica` como archivo de datos.
- Las capabilities habilitadas son configuracion de identidad/plataforma, pero
  no una tabla de permisos editable desde el panel.
- La retencion de observacion pertenece al subsistema de Observacion.

## Tests

- una configuracion invalida impide arrancar;
- defaults explicitos y documentados;
- secretos nunca aparecen en logs;
- no existe un parser generico que acepte cualquier JSON como parametro de
  negocio.
