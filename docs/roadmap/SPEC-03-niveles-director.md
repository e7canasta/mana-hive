# SPEC-03 — Los cuatro niveles del director, en el código

**Depende de:** `SPEC-02` · **Bloquea:** `SPEC-06` · **Tamaño:** mediano

---

## El problema

El director tiene cuatro niveles. Están documentados con detalle en `docs/NIVELES-MONITOREO.md` y el árbol de decisión en `docs/DECISION-TREE.md`:

```
NIVEL 0: STANDARD          → sin alertas, sólo observar
NIVEL 1: NIGHT-WANDERING   → alertas básicas (20-30 min)
NIVEL 2: FALL-RISK         → alertas rápidas (1-15 min)
NIVEL 3: CRITICAL          → alertas inmediatas (1-10 min)
```

En el código, ese vocabulario está roto en tres pedazos:

| Dónde | Qué hay |
|---|---|
| `hub.policy.WatchLevel` | Tres valores: `STANDARD`, `ENHANCED`, `CRITICAL`. **No es el vocabulario del director.** `ENHANCED` no existe en la residencia; faltan NIGHT-WANDERING y FALL-RISK. |
| `contracts.policy.LevelCatalogs` | Los cuatro catálogos, correctos: `STANDARD_CATALOG`, `NIGHT_WANDERING_CATALOG`, `FALL_RISK_CATALOG`, `CRITICAL_CATALOG`. Pero son cuatro `val` sueltos: **no hay un tipo que diga "un nivel"**, y nada los indexa. |
| `politica-domain/src/test/.../ProductionCatalog.kt` | Las plantillas `night-wandering`, `fall-risk`, `low-mobility` — **en fuentes de test.** `POLITICA-GUIDE.md` las presenta como catálogo maestro de producción. |

Y el efecto medible, verificado corriendo los blueprints:

```
Susan · template("standard") →  Scene: 0 dwell,  Sentinel: 0 alert rules
José  · template("standard") →  Scene: 2 dwell,  Sentinel: 2 alert rules
                                        ↑ ambas de sus overrides, no de la plantilla
```

**La capa de plantilla aporta cero.** De la cadena `catálogo → plantilla → override` sólo funciona el último peldaño. Los tres catálogos que llevan los niveles reales del director — NIGHT_WANDERING, FALL_RISK, CRITICAL — **no los ejercita ningún blueprint**.

Consecuencia sobre la documentación: el "0 episodios, 0 notificaciones" de Susan no demuestra que el nivel STANDARD sea *observar sin alarmar*. Demuestra que su política resolvió vacía. Los checks verdes son vacuamente ciertos.

---

## Objetivo

Que el nivel sea **un tipo de primera clase**, que los cuatro catálogos estén indexados por él, que las plantillas vivan en producción, y que cada nivel tenga un blueprint que lo ejercite con expectativas no vacías.

---

## Cambios

### 1 · El tipo

`platform/contracts/src/main/kotlin/com/manahive/contracts/policy/WatchLevel.kt`

```kotlin
/**
 * El nivel de vigilancia de un residente. Es lo único que el director
 * elige; los tiempos salen del catálogo del nivel.
 *
 * El orden del enum es el orden de protección creciente: se usa para
 * el desempate "gana la capa más protectora".
 */
public enum class WatchLevel(public val label: String) {
    STANDARD("standard"),
    NIGHT_WANDERING("night-wandering"),
    FALL_RISK("fall-risk"),
    CRITICAL("critical"),
}
```

`label` es la forma que ya usan los `TemplateId` y los TOML de `platform/serialization`. Un solo string por nivel, no dos vocabularios.

Reemplaza a `hub.policy.WatchLevel`, que `SPEC-02` retira. Vive en `contracts` porque lo consumen el hub y política.

**No agregar un nivel `LOW_MOBILITY`.** `low-mobility` es una plantilla (un ajuste por movilidad), no un nivel de vigilancia; el árbol de decisión del director no lo ofrece. Se resuelve con `mobility(MobilityAid.WALKER)` en el perfil.

### 2 · Indexar los catálogos

`contracts/policy/LevelCatalogs.kt`

```kotlin
public val CATALOG_BY_LEVEL: Map<WatchLevel, DagCatalog> = mapOf(
    WatchLevel.STANDARD        to STANDARD_CATALOG,
    WatchLevel.NIGHT_WANDERING to NIGHT_WANDERING_CATALOG,
    WatchLevel.FALL_RISK       to FALL_RISK_CATALOG,
    WatchLevel.CRITICAL        to CRITICAL_CATALOG,
)
```

Con un test que verifique que el mapa cubre **todos** los valores del enum (`WatchLevel.entries.all { it in CATALOG_BY_LEVEL }`). Es la red que evita que un nivel nuevo quede sin catálogo.

### 3 · Las plantillas salen de fuentes de test

Mover el contenido de `engines/politica-engine/politica-domain/src/test/kotlin/com/manahive/politica/ProductionCatalog.kt` a producción.

Destino: `platform/contracts/src/main/kotlin/com/manahive/contracts/policy/ProductionDagCatalog.kt`, que ya existe y hoy sólo expone `PRODUCTION_DAG_CATALOG`.

**Antes de mover, contrastar contra `docs/NIVELES-MONITOREO.md`.** Los tiempos de esa tabla son la fuente de verdad clínica. Si el catálogo de test difiere, gana el documento, y la diferencia se anota en el commit.

Tabla a respetar (aviso / episodio):

| Estado | STANDARD | NIGHT-WANDERING | FALL-RISK | CRITICAL |
|---|---|---|---|---|
| `LYING` | — | — | — | — |
| `SITTING_IN_BED` | — | 20 / 30 min | 15 / 20 min | 10 / 15 min |
| `BED_EDGE` | — | 3 / 5 min | 1 / 2 min | 1 / 2 min |
| `STANDING` | — | 10 / 15 min | 2 / 3 min | 2 / 3 min |
| `IN_BATHROOM` | — | 15 / 25 min | 10 / 15 min | 5 / 10 min |
| `ABSENT` | — | 5 / 10 min | 5 / 10 min | 2 / 5 min |

Severidad: `WARNING` en niveles 1 y 2; `CRITICAL` en nivel 3.

Transiciones especiales, en los cuatro niveles: `LYING → STANDING` graba (2 min antes / 5 después; 5 / 10 en CRITICAL). Staff entra → cierra el episodio.

**Coordinar con `SPEC-01`:** cada regla de esta tabla es temporizada (`alertAfter`) salvo que se decida lo contrario para `BED_EDGE` en CRITICAL. Marcar el `triggerOn` en consecuencia.

### 4 · El perfil toma un nivel

`buildResidentProfile` hoy recibe `template("standard")`, un string libre. Pasa a recibir el nivel:

```kotlin
val joseProfile = buildResidentProfile("jose") {
    level(WatchLevel.FALL_RISK)      // ← el director elige esto
    mobility(MobilityAid.NONE)

    resident {
        sitting  { alertAfter(15.minutos) }   // y ajusta esto si hace falta
    }
}
```

`template(String)` queda `@Deprecated` mapeando por `label`, para no romper los blueprints en el mismo commit.

### 5 · Un blueprint por nivel

Hoy los dos blueprints usan `standard`. Agregar escenarios que ejerciten los otros tres, con **expectativas no vacías**:

| Blueprint | Residente | Nivel | Debe demostrar |
|---|---|---|---|
| existente | Susan | STANDARD | 0 episodios — y ahora sí es significativo, porque el nivel resuelve reglas y decide no alertar |
| nuevo | — | NIGHT_WANDERING | sentado 25 min → aviso a los 20, episodio a los 30 |
| nuevo | — | FALL_RISK | borde de cama 90 s → episodio a los 2 min |
| nuevo | — | CRITICAL | baño 12 min → episodio a los 10, severidad `CRITICAL` |

**Regla de no-vacuidad** (criterio global 2 del roadmap): el escenario de Susan en STANDARD sólo cuenta si existe un escenario hermano con la **misma coreografía** en otro nivel que sí abra episodio. Es lo que prueba que el cero viene del nivel y no de una política vacía.

Nombres de residentes: ver `SPEC-07` antes de elegir. No usar "Susan" para un residente nuevo.

---

## Criterios de aceptación

1. `WatchLevel` tiene cuatro valores y vive en `contracts`. `hub.policy.WatchLevel` no existe.
2. `CATALOG_BY_LEVEL` cubre el enum completo, con test que lo verifica.
3. Las plantillas `night-wandering`, `fall-risk`, `low-mobility` están en `src/main`, no en `src/test`.
4. Los tiempos del catálogo coinciden con `docs/NIVELES-MONITOREO.md`, o la diferencia está justificada en el commit.
5. Correr `:blueprints:susan-e2e-standard:run` ya **no** imprime `Sentinel: 0 alert rules` para un nivel que define reglas.
6. Existen los tres blueprints nuevos y sus checks son no vacíos.
7. `LANG=C.UTF-8 ./gradlew check` verde.

## Fuera de alcance

- La API REST para editar plantillas (`SPEC-06`).
- El árbol de decisión como código. El director lo aplica leyendo `DECISION-TREE.md`; convertirlo en función es una idea para más adelante, no una necesidad.
- `RiskLevel` y `MobilityAid`: se conservan como están. Son dimensiones del residente, no niveles de vigilancia.
