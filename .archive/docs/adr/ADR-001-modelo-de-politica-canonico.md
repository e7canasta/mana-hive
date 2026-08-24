# ADR-001 — Un solo modelo de política: Política es canónico

**Estado:** aceptado · **Fecha:** 2026-08-26 · **Spec:** `docs/roadmap/SPEC-02-politica-canonica.md`

---

## Contexto

El sistema tenía **dos modelos de política incompatibles**, ambos vivos, que contestaban la misma pregunta — *¿qué reglas gobiernan a este residente ahora?*

| | Stack A — hub | Stack B — politica |
|---|---|---|
| Entrada | `PolicyLayers(level, template, adjustments, windows)` | `DagCatalog` + `AlarmProfile` |
| Niveles | `WatchLevel { STANDARD, ENHANCED, CRITICAL }` | `LevelCatalogs`: STANDARD, NIGHT_WANDERING, FALL_RISK, CRITICAL |
| Salida | `EffectiveRules` — sólo Sentinel | `PolicyCalibration` — Scene, Sentinel, Faro, Grabadora |
| DSL | ninguno | `buildDagCatalog {}`, `buildResidentProfile {}` |
| Lo ejercitaba | nada | los blueprints |
| Estado | devolvía capas vacías y logueaba `not production ready` | resolvía de verdad |

Además, `WatchLevel` tenía tres valores y **ninguno era el vocabulario del director**, que son cuatro y están documentados en `docs/NIVELES-MONITOREO.md`. El término `ENHANCED` no lo pronuncia nadie en la residencia.

Verificado al auditar: todo residente que pasara por el hub resolvía a plantilla vacía en STANDARD — sin reglas, sin alertas, siempre.

## Decisión

**Política es el resolvedor canónico. El hub deja de resolver.**

El hub conserva lo que sólo él puede hacer: ser **System of Record de las capas**. Almacena qué decidió cada persona, cuándo y por qué; proyecta esas capas a un `AlarmProfile`; y delega la resolución en Política.

```
capas event-sourced (hub)
      ↓ PolicyLayers.toAlarmProfile()      ← procedencia: quién y por qué
   AlarmProfile
      ↓ politica.PolicyResolver.resolve()  ← precedencia: catálogo→plantilla→override
   PolicyCalibration
      ↓ adapters
Scene · Sentinel · Faro · Grabadora
```

Las dos explicaciones se concatenan en ese orden: primero quién lo decidió, después cómo se resolvió.

### Por qué Stack B

1. Es el único que resuelve para los cuatro motores. Stack A sólo producía reglas de Sentinel; Scene, Faro y Grabadora quedaban sin política.
2. Es el único con DSL. El argumento central del producto es que el director lee y escribe sus propias reglas.
3. Es el único con pruebas de punta a punta.
4. Ya contenía los cuatro niveles del director.

## Consecuencias

**Se retira** `hub.policy.PolicyResolver` (interfaz), y `hub.policy.WatchLevel` (3 valores).

**Se conserva y muda** `WatchLevel` a `contracts` con los **cuatro** valores del director, más `CATALOG_BY_LEVEL`. Adelantado de `SPEC-03` porque `SPEC-02` no puede seleccionar catálogo sin él.

**`PolicyCalibration` gana `fingerprint`**, calculado en `PolicyResolver.resolve()` desde la versión del catálogo y el perfil. La huella nace donde nace la decisión; calcularla en el borde la desconecta de lo que realmente se resolvió.

**`PolicyResolver.resolve()` devuelve `Explained<PolicyCalibration>`.** La precedencia catálogo→plantilla→override se decide ahí y antes se perdía en silencio. Alinea a Política con el idiom del kernel y con el resolvedor del hub que reemplaza.

**Un residente sin capas es un error**, no un STANDARD silencioso. `NoPolicyForResident`. Vigilar a alguien con reglas que nadie eligió es peor que no vigilarlo.

**Nueva dependencia `hub-service → politica-domain`.** `politica-domain` es puro y no hay ciclo. La dirección es la correcta: el hub pasa a ser consumidor del lenguaje publicado de Política.

### Desviación registrada respecto de la spec

`SPEC-02` decía conservar los tipos de almacenamiento tal cual. **`ManualAdjustment` y `TimeWindow` se rediseñaron**, porque guardaban `AlertRule`, que **no tiene umbral de tiempo**: el hub no podía almacenar *"avísenme a los quince minutos"*, que es exactamente lo que el director más hace. Ahora guardan `state` + `DwellThreshold`. `ManualAdjustment` además exige `reason` no vacío — un cambio de vigilancia sin motivo es un cambio que nadie puede explicar seis meses después.

`LevelTemplate` guarda la **referencia** a la plantilla, no su contenido: copiar los tiempos crearía una segunda fuente de verdad que se desincroniza en silencio.

## Alternativas descartadas

**Stack A como canónico.** Habría que dotarlo de DSL, de salida para cuatro motores y de blueprints. Costo sustancialmente mayor por un modelo que nada ejercitaba.

**Mantener los dos con un adaptador.** Dos fuentes de verdad sobre la misma pregunta clínica. El adaptador se convierte en el lugar donde se esconde la discrepancia.

**Mapear `WatchLevel` a `RiskLevel`.** Error de categoría: `RiskLevel` describe al residente — qué tan frágil es, una entrada; `WatchLevel` describe la decisión clínica — qué hacemos al respecto. Se cruzan: una residente de riesgo alto puede estar en STANDARD porque la familia rechazó el monitoreo; una de riesgo bajo puede estar en CRITICAL por post-operatorio. Colapsarlos destruye la distinción sobre la que está construido `docs/DECISION-TREE.md`.

## Pendiente

- `PolicyLayerStore` es hoy un puerto sin implementación. El almacén event-sourced y la API son `SPEC-06`.
- `riskLevel` y `mobilityAid` se proyectan con valores fijos: son dimensiones del censo del residente, no de sus capas de política. Cuando el censo esté conectado, salen de ahí.
- Los tests de `PolicyResolverSpec` y `PoliticaCatalogSpec` ejercitan el resolvedor **legacy** (`AlarmCatalog`). El canónico (`DagCatalog`) tiene cobertura más fina. Anotado para `SPEC-03`.
