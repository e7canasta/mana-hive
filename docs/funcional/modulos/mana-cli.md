# Funcion: `mana-cli`

## Proposito

Ofrecer el binario `mana` para inspeccionar y operar el hub por dominio,
ademas de las escenas de regresion. Es una capa delgada sobre `mana-sdk`: no
repite reglas de dominio ni genera DTOs.

## Funciones implementadas

- Sesion: `identidad login` guarda el token en `~/.mana/token` (permisos 0600)
  y `identidad logout` lo borra. Precedencia del token:
  `--token` > `MANA_API_TOKEN` > archivo de sesion. `MANA_TOKEN_FILE`
  sobreescribe la ubicacion del archivo.
- Salida: JSON por defecto; tablas para listados; grid ASCII para el
  planograma (`--json` fuerza JSON crudo).
- Comandos por dominio:

```text
api         health
identidad   login logout me usuarios
auditoria   log --limit --action --entity-type --entity-id
residencia  facilities facility wings rooms beds camas planograma privacidad
poblacion   residentes residente alta asignar liberar egreso
cobertura   grilla reemplazar-grilla grupos grupo crear-grupo miembros
            cobertura asignar-cobertura
cuidado     ronda-actual rondas crear-ronda completar-ronda tarea notas nota
politica    catalogo presets perfil historial actualizar autopilot
historia    ingest incidentes incidente secuencia revisar
vigilancia  listar crear detalle transicion entregas
streams     list create get regions set-regions update-region
scene       validate load
```

## Layout

```text
crates/mana-cli/src/
  main.rs          entrada y clientes autenticados
  cli.rs           parseo --clave=valor, especificacion de comandos, usage
  session.rs       archivo de token (0600)
  output.rs        JSON y tablas
  render/          vistas dedicadas (planograma)
  commands/        un modulo por dominio; dispatch central en mod.rs
```

Un dominio nuevo se agrega como modulo en `commands/` con su `dispatch`, una
entrada en `COMMANDS` y un arm en `commands/mod.rs`; el verbo y las opciones
quedan declarados en la especificacion.

## Reglas

- La superficie de comandos es la del SDK; no se agrega logica de dominio.
- `--json` disponible en todos los comandos con salida estructurada.
- El token nunca se imprime en `identidad login` salvo por el JSON de
  respuesta del hub; el runner de escenas sigue redactandolo.

## Comandos de observacion

`board`, `estado`, `eventos`, `timeline`, `sueno`, `movilidad`, `bano`,
`habitaciones`, `mirar`, `reporte` e `ingerir`.

Dos detalles deliberados: `board` muestra `SIN VINCULAR` cuando una cama no
tiene `monitor_key` —porque esa cama no genera un solo aviso y tiene que verse—
e `ingerir` avisa por stdout cuando el evento quedo sin resolver.

`ingerir` es el unico comando que no usa sesion: la ingesta autentica con el
secreto del bridge.

## Comandos de streams

Gestion de camaras y regiones de interes (ROI) poligonales.

```text
streams list        --room-id=<id>                        listar streams de una room
streams create      --room-id=<id> --stream-key=<key>     registrar cámara
                    [--name=<nombre>]
streams get         --stream-id=<id>                      detalle de un stream
streams regions     --stream-id=<id>                      listar regiones ROI
streams set-regions --stream-id=<id> --body=<json>        reemplazar regiones
streams update-region --stream-id=<id> --region-id=<id>   actualizar polígono
                    --points=<json> [--label=<texto>]
```

El body de `set-regions` acepta JSON inline o desde archivo:

```bash
# inline
mana-hub streams set-regions --stream-id stream-... --body='[...]'

# desde archivo
mana-hub streams set-regions --stream-id stream-... --body="$(cat regions.json)"
```

Formato del body:

```json
[
  {"region_type":"bed","points":[[0.1,0.2],[0.5,0.2],[0.5,0.8],[0.1,0.8]],"label":"Cama 101-A"},
  {"region_type":"hallway","points":[[0.6,0.1],[0.9,0.1],[0.9,0.9],[0.6,0.9]],"label":"Pasillo"}
]
```

Tipos de región válidos: `bed`, `bathroom`, `hallway`, `exit`, `furniture`, `person`, `object`.
Los puntos son polígonos normalizados (0.0-1.0), mínimo 3 puntos.

## Verificacion

Los tests cubren el render del planograma (leyenda y superposiciones). La
regresion de comandos se hace contra el hub con `MANA_HUB_SEED_DEMO=1`.
