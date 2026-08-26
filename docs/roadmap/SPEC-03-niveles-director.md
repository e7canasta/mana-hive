# SPEC-03 — Los cuatro niveles del director, en el código

**Depende de:** `SPEC-02` (cerrada) · **Bloquea:** `SPEC-06` · **Tamaño:** mediano

> **Revisada 2026-08-26, al cerrar `SPEC-02`.** Parte de esta spec se ejecutó allá porque `SPEC-02` no podía seleccionar catálogo sin ella. Lo que sigue es lo que **queda**, verificado contra el código.

---

## Ya hecho en `SPEC-02`

| Pieza | Estado |
|---|---|
| `WatchLevel` con los cuatro valores, en `contracts` | ✅ `platform/contracts/.../policy/WatchLevel.kt` |
| `hub.policy.WatchLevel` (3 valores, con `ENHANCED`) retirado | ✅ no existe |
| `CATALOG_BY_LEVEL` + `catalogFor(level)` | ✅ en `LevelCatalogs.kt` |
| Test de cobertura del enum | ✅ `LevelCatalogsSpec` |
| **Los tiempos de los cuatro catálogos coinciden con `NIVELES-MONITOREO.md`** | ✅ verificado nivel por nivel |
| Severidad `WARNING` en niveles 1-2, `CRITICAL` en nivel 3 | ✅ |
| Ventanas de grabación en `LYING → STANDING` | ✅ 2/5 min, y 5/10 en CRITICAL |

La tabla clínica **no hay que volver a contrastarla**. Está bien.

---

## Lo que queda

### 1 · El perfil toma un nivel, no un string

**Estado:** `DagDsl.kt:270` sólo ofrece `template(id: String)`. El director elige un nivel; el perfil recibe un string libre que nadie valida.

```kotlin
// hoy
val joseProfile = buildResidentProfile("jose") {
    risk(RiskLevel.HIGH)
    template("standard")        // ← string libre
}

// objetivo
val joseProfile = buildResidentProfile("jose") {
    level(WatchLevel.FALL_RISK) // ← la decisión del director, tipada
    mobility(MobilityAid.NONE)
    resident { sitting { alertAfter(15.minutos) } }
}
```

- Agregar `level(WatchLevel)` a `ResidentProfileBuilder`.
- `template(String)` queda `@Deprecated`, mapeando por `WatchLevel.fromLabel()`, para no romper los blueprints en el mismo commit.
- El perfil resultante tiene que llevar el nivel, de modo que `PolicyService` pueda hacer `catalogFor(profile.level)` sin consultar las capas otra vez.

**Ojo con `RiskLevel`:** se conserva y **no** se deriva del nivel. Son ejes distintos — ver el encabezado de `WatchLevel.kt` y `ADR-001`. `risk()` y `mobility()` siguen siendo dimensiones del residente.

### 2 · Las plantillas salen de fuentes de test

**Estado:** `engines/politica-engine/politica-domain/src/test/kotlin/com/manahive/politica/ProductionCatalog.kt` define `night-wandering`, `fall-risk` y `low-mobility`. Están en `src/test`. `POLITICA-GUIDE.md` las presenta como catálogo maestro de producción.

`platform/contracts/.../ProductionDagCatalog.kt` existe en `main` y sólo expone `PRODUCTION_DAG_CATALOG` — ninguna plantilla.

- Mover las tres plantillas a `main`.
- **No dupliquen los tiempos de `LevelCatalogs`.** Las tres primeras plantillas son el catálogo de su nivel; si una plantilla repite los números, hay dos fuentes de verdad. Preferir que la plantilla **referencie** el nivel y aporte sólo lo que agrega.
- `low-mobility` es la excepción: no es un nivel, es un ajuste por movilidad. Se resuelve con `mobility(MobilityAid.WALKER)`, y si necesita tiempos propios, van explícitos y documentados como tales.

### 3 · Un blueprint por nivel

**Estado:** los dos blueprints usan `standard`. `NIGHT_WANDERING_CATALOG`, `FALL_RISK_CATALOG` y `CRITICAL_CATALOG` **no los ejercita nada de punta a punta.**

| Blueprint | Nivel | Debe demostrar |
|---|---|---|
| existente (Susan → ver punto 5) | STANDARD | 0 episodios — significativo sólo si existe el hermano de abajo |
| nuevo | NIGHT_WANDERING | sentado 25 min → aviso a los 20, episodio a los 30 |
| nuevo | FALL_RISK | borde de cama 90 s → episodio a los 2 min |
| nuevo | CRITICAL | baño 12 min → episodio a los 10, severidad `CRITICAL` |

**Regla de no-vacuidad:** el escenario STANDARD sólo cuenta si existe uno hermano con la **misma coreografía** en otro nivel que sí abra episodio. Es lo que prueba que el cero viene del nivel y no de una política vacía. `LevelCatalogsSpec` ya lo verifica a nivel de catálogo; falta a nivel de pipeline.

### 4 · Cobertura del resolvedor canónico

**Deuda heredada de `SPEC-02`.** `PolicyResolverSpec` y `PoliticaCatalogSpec` ejercitan el resolvedor **legacy** (`AlarmCatalog`). El canónico —`resolve(DagCatalog, AlarmProfile)`, el que usa producción— sólo lo cubren `TriggerSemanticsSpec` y los blueprints.

Agregar cobertura directa del camino DAG: precedencia catálogo→plantilla→override, huella distinta ante catálogos de versión distinta, y que `Explained` nombre las capas que aportaron.

Decidir además si el overload `resolve(AlarmCatalog, ...)` sigue vivo. Si nada de producción lo usa, retirarlo — es el mismo criterio con que se retiró `SentinelCalibration.ruleFor`.

### 5 · Renombrar a la residente Susan

**Es el punto 2 de `SPEC-07`, y se cierra acá** porque esta spec agrega blueprints y hay que fijar la convención antes de elegir nombres.

`Susan` nombra dos cosas: la enfermera de guardia citada en `blueprints/jose-301-sitting-bed/README.md`, y la residente de `bed-5`. Renombrar **la residente** — la cita de la enfermera es material clínico textual.

Sugerido: **Elena**, 401. Alcance: directorio, `settings.gradle.kts`, paquete `susane2e`, `ResidentId`, `NightId`, los `println`, y la tabla de escenarios de `POLITICA-GUIDE.md`.

Dejar escrita en `blueprints/README.md` la convención: residentes de prueba con nombre + habitación (`jose-301`, `elena-401`); el personal se nombra por rol salvo en citas textuales.

---

## Criterios de aceptación

1. `buildResidentProfile { level(WatchLevel.FALL_RISK) }` compila y resuelve contra `FALL_RISK_CATALOG`.
2. Las plantillas `night-wandering`, `fall-risk`, `low-mobility` están en `src/main`, sin duplicar los tiempos de `LevelCatalogs`.
3. Existen los tres blueprints nuevos y sus checks son no vacíos.
4. Hay cobertura directa del resolvedor DAG, incluida la huella.
5. `grep -rni "susan" --include="*.kt" .` sólo aparece en la cita de la enfermera.
6. `./gradlew check` verde y los blueprints corren.

## Fuera de alcance

- La API REST para editar plantillas (`SPEC-06`).
- El árbol de decisión como código. El director lo aplica leyendo `DECISION-TREE.md`.
- `riskLevel`/`mobilityAid` desde el censo: hoy `PolicyProjection` los fija; salen del censo cuando el censo esté conectado (`SPEC-06`).
