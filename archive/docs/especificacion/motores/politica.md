# Motor de politica efectiva

## Objetivo de producto

Dado el nivel de vigilancia de una persona, su accesorio, su plantilla y sus
ajustes manuales, responder exactamente qué reglas se aplican de día y de noche.

El equipo no debería tener que interpretar tres configuraciones superpuestas.

## Ubicacion

- Implementacion pura: `crates/mana-motores/src/catalogo/`.
- Persistencia de perfiles: `crates/ctx-politica/`.
- Composicion y read model: `crates/mana-app/src/politica.rs`.
- Fuente de politica: `config/alarm-catalog.toml`.

## API

```text
AlarmCatalog::parse(toml) -> Result<AlarmCatalog, CatalogError>
AlarmCatalog::resolve_rules(level, aid, custom, template, overrides)
    -> BTreeMap<RuleId, ResolvedRule>
AlarmCatalog::validate_overrides(overrides) -> Result<(), CatalogError>
```

Las capas se aplican en este orden:

```text
preset del nivel -> plantilla -> override manual
```

Cada regla efectiva conserva `source` y `customized` para que la UI pueda
explicar por qué quedó así.

## Ejemplo de producto

Una persona con nivel alto y plantilla de deambulación nocturna recibe:

- salida de cama en alarma durante la noche;
- ausencia prolongada de la habitación con umbral reducido;
- reglas de accesorio solo si usa ese accesorio;
- ajustes manuales únicamente si el modo es `custom`.

## Invariantes

- Una regla bloqueada no se puede apagar.
- Un override desconocido se rechaza.
- Un parámetro fuera de rango se rechaza.
- Un accesorio incompatible no recibe reglas que no puede usar.
- El motor no consulta perfiles ni persiste cambios.

## Escena

`politica-blueprint.json` comprueba catálogo, creación de perfil, resolución de
plantilla, override, historial y aplicación de recomendaciones.
