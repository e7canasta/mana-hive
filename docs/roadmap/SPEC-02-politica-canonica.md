# SPEC-02 — Un solo modelo de política

**Decisión estructural.** Todo lo que sigue en el roadmap toca política; hacerlo después de las otras specs obliga a rehacerlas.

**Depende de:** `SPEC-00` · **Bloquea:** `SPEC-03`, `SPEC-04`, `SPEC-05`, `SPEC-06` · **Tamaño:** grande
**Decisión de arquitectura:** `AD-1`

---

## El problema

Hay dos respuestas incompatibles a la misma pregunta — *¿qué reglas gobiernan a este residente ahora?*

| | **Stack A — hub** | **Stack B — politica** |
|---|---|---|
| Paquete | `com.manahive.hub.policy` | `com.manahive.politica` + `contracts.policy` |
| Entrada | `PolicyLayers(level, template, adjustments, windows)` | `DagCatalog` + `AlarmProfile` |
| Niveles | `WatchLevel { STANDARD, ENHANCED, CRITICAL }` | `LevelCatalogs`: STANDARD, NIGHT_WANDERING, FALL_RISK, CRITICAL |
| Salida | `EffectiveRules` — sólo Sentinel | `PolicyCalibration` — Scene, Sentinel, Harbor, Recorder |
| DSL | ninguno | `buildDagCatalog {}`, `buildResidentProfile {}` |
| Procedencia / capas | modelado: nivel → plantilla → ajuste → ventana horaria, con actor y timestamp | catálogo → plantilla → override |
| Quién lo ejercita | **nada** | los blueprints |
| Estado | `PolicyService` devuelve layers vacías y loguea `not production ready` | resuelve de verdad |

Además: **`WatchLevel` no es el vocabulario del director.** Tiene tres valores; el director tiene cuatro, documentados en `NIVELES-MONITOREO.md` y `DECISION-TREE.md`. `ENHANCED` no lo pronuncia nadie en la residencia. Es exactamente el olor que esta arquitectura dice combatir.

---

## Decisión

**Stack B es canónico. El hub deja de resolver política.**

Razones:

1. Es el único que resuelve para los cuatro motores. Stack A sólo produce reglas de Sentinel; Scene, Harbor y Recorder quedarían sin política.
2. Es el único con DSL. El argumento central del producto es que el director lee y escribe sus propias reglas.
3. Es el único que los blueprints ejercitan. Stack A no tiene una sola prueba de punta a punta.
4. Ya contiene los cuatro niveles del director en `LevelCatalogs.kt`.

**Lo que el hub conserva** es lo que sólo el hub puede hacer, y es valioso: ser System of Record de las **capas**. El modelo de `PolicyLayers` — nivel, plantilla, ajustes manuales con actor y timestamp, ventanas horarias — es más rico que el `catálogo → plantilla → override` de Stack B, y es lo que hace contestable la pregunta *"¿de dónde salió ese diez?"*.

Ese modelo **no se tira: cambia de rol.** Deja de ser un resolvedor y pasa a ser el almacén event-sourced que **proyecta** un `AlarmProfile` para que Política resuelva.

```
        ANTES (dos resolvedores)              DESPUÉS (uno)

  hub: PolicyLayers                     hub: PolicyLayers (event-sourced)
         ↓ hub.PolicyResolver                  ↓ proyección
       EffectiveRules  ← nadie             AlarmProfile
                                                ↓ politica.PolicyResolver
  politica: DagCatalog + AlarmProfile      PolicyCalibration
         ↓ politica.PolicyResolver               ↓ adapters
       PolicyCalibration  ← blueprints    Scene · Sentinel · Harbor · Recorder
```

### Qué pasa con cada tipo

| Tipo | Destino |
|---|---|
| `hub.policy.PolicyLayers` | **Se conserva.** Es el modelo de almacenamiento. |
| `hub.policy.ManualAdjustment`, `TimeWindow` | **Se conservan.** Llevan la procedencia. |
| `hub.policy.WatchLevel` | **Se retira.** Reemplazado por el tipo de nivel de `SPEC-03`. |
| `hub.policy.LevelTemplate` | **Se retira** como tipo propio; su papel lo cumple `TemplateId` + el catálogo. |
| `hub.policy.PolicyResolver` (interfaz) | **Se retira.** Reemplazado por una proyección `PolicyLayers → AlarmProfile`. |
| `hub.policy.InMemoryPolicyCatalog`, `InMemoryRawPolicyStore`, `InMemorySemanticBucketStore` | **Se conservan** como almacenes; se revisan en `SPEC-06`. |
| `contracts.policy.EffectiveRules` | **Se conserva** — sigue siendo el contrato hacia Sentinel, ahora producido por Política. |
| `contracts.policy.PolicyCatalog` (creado en `SPEC-00`) | **Se revisa acá.** Si sólo servía a Stack A, se retira. |

### Si se decide lo contrario

Si la dirección técnica elige Stack A como canónico, esta spec se invierte y **`SPEC-03`, `SPEC-04` y `SPEC-05` cambian de contenido por completo**: habría que dotar a Stack A de DSL, de salida para cuatro motores y de blueprints. El costo es sustancialmente mayor. Registrar la decisión y su motivo antes de ejecutar.

---

## Cambios

### 1 · Escribir el ADR

`docs/adr/ADR-001-modelo-de-politica-canonico.md`, formato corto: contexto, decisión, consecuencias, alternativas descartadas. Es el documento que va a explicar, dentro de un año, por qué se borró código que funcionaba.

### 2 · La proyección `PolicyLayers → AlarmProfile`

Módulo: `hub/hub-domain`. Función pura, sin I/O.

```kotlin
public fun PolicyLayers.toAlarmProfile(resident: ResidentId, at: Instant): Explained<AlarmProfile>
```

Debe:

- Elegir la plantilla a partir del nivel.
- Aplicar los ajustes manuales como overrides del perfil.
- Aplicar las ventanas horarias vigentes en `at`.
- Devolver `Explained<...>` con un paso de explicación por cada capa que haya aportado algo, nombrando el actor cuando lo haya. Ésta es la procedencia; sin ella la decisión no es auditable.

**Precedencia:** la misma que ya documenta `hub.policy.PolicyResolver` — nivel → plantilla → ajuste manual → ventana horaria, y ante empate gana la capa más protectora. Conservar esa regla; está bien pensada y es la que el director espera.

### 3 · `PolicyService` deja de fabricar defaults

`hub/hub-service/.../policy/PolicyService.kt` hoy construye `PolicyLayers` vacías y loguea `not production ready`.

Pasa a: leer las capas del almacén event-sourced → proyectar a `AlarmProfile` → delegar en `politica.PolicyResolver` con el catálogo del nivel → devolver `PolicyCalibration`.

Si no hay capas para un residente, **fallar explícitamente**, no devolver un default silencioso. Un residente sin política es un error operativo que alguien tiene que ver, no un residente en STANDARD.

### 4 · Retirar Stack A como resolvedor

Borrar la interfaz `hub.policy.PolicyResolver` y sus implementaciones. Ajustar `PolicyCatalogController` y lo que dependa.

### 5 · Resolver la deuda de `SPEC-00`

`PolicyPayloadDslSpec.kt.pending` vuelve a ser test o se borra, según lo que decida esta spec sobre los payloads. **No puede quedar pendiente al cerrar.**

---

## Criterios de aceptación

1. Existe `docs/adr/ADR-001-*.md` con la decisión y las alternativas descartadas.
2. `grep -rn "class PolicyResolver\|interface PolicyResolver" --include="*.kt" .` devuelve **un solo** resultado.
3. `WatchLevel` no existe en el repositorio.
4. `PolicyService.resolveEffectiveRules` no contiene la palabra `hardcoded` ni `log.warn(... not production ready ...)`.
5. Un test de `hub-domain` demuestra que la proyección explica la procedencia: dado un nivel + una plantilla + un ajuste manual de un actor concreto, el `Explained` nombra las tres capas y el actor.
6. Un test demuestra que un residente sin capas **falla**, no cae a STANDARD.
7. `LANG=C.UTF-8 ./gradlew check` verde y los dos blueprints corren.
8. No queda ningún archivo `.pending`.

## Fuera de alcance

- La API REST de plantillas (`SPEC-06`).
- Los cuatro niveles como tipo (`SPEC-03`) — acá sólo se retira `WatchLevel`; el tipo nuevo lo define la spec siguiente. Coordinar: si se ejecutan juntas, `SPEC-03` provee el tipo y ésta lo consume.
- Persistencia real en Postgres. La proyección se prueba contra el almacén en memoria.
