# SPEC-04 — Sacar los adapters del módulo de pruebas

**Depende de:** `SPEC-02` · **Bloquea:** nada, pero bloquea de hecho cualquier servicio que quiera usar política · **Tamaño:** chico, mecánico

---

## El problema

Las funciones que traducen `PolicyCalibration` a las calibraciones de cada motor viven acá:

```
engines/pipeline/pipeline-bdd/src/main/kotlin/com/manahive/politica/adapters/PolicyAdapters.kt
```

`pipeline-bdd` es un módulo de soporte de pruebas. Sus únicos consumidores:

```
blueprints/jose-301-e2e-pipeline/build.gradle.kts
blueprints/susan-e2e-standard/build.gradle.kts
```

Es decir: **la traducción de política a motores no existe fuera del arnés de test.** Ningún servicio Spring puede resolver la política de un residente y aplicarla, porque la única implementación de ese paso está en un módulo que ningún servicio debería depender.

Las cuatro funciones son producción disfrazada de test:

```kotlin
PolicyCalibration.toSceneCalibration(): SceneCalibration
PolicyCalibration.toSentinelCalibration(): SentinelCalibration
PolicyCalibration.toHarborCalibration(): HarborCalibration
PolicyCalibration.toRecordingCalibration(bed, monitor): RecordingCalibration
```

Son puras y están bien escritas. El problema es sólo dónde viven.

Síntoma relacionado, a atender en el camino: `PolicyResolver.resolve()` devuelve

```kotlin
harbor = HarborPolicy(defaultChannels = emptyMap(), escalationTimeouts = emptyMap())
```

mientras `docs/POLITICA-GUIDE.md` describe *"Budget: 5 warnings por turno · Channels: PUSH + TABLET · Escalation: 30min"* como resueltos por Política. **Harbor no recibe nada de política hoy.** Y `Recorder: 0 transition windows` en los dos blueprints: el de José compensa escribiendo a mano un `recordingCalibration {}`, o sea que el camino de Grabadora esquiva Política por completo.

---

## Cambios

### 1 · Módulo destino

Crear `engines/politica-engine/politica-adapters`, con el plugin de convención `manahive.kotlin-common`.

Por qué ahí y no en `contracts`: los adapters **conocen los tipos de cada motor** (`SceneCalibration`, `HarborCalibration`, …). `contracts` es el lenguaje publicado y no debe depender de los motores. Política sí puede: es su capa anticorrupción hacia ellos, exactamente el papel que `CONTEXT-MAP.md` le asigna.

Dependencias: `contracts`, `politica-domain`, y los cuatro `*-domain` de los motores. **Ningún `*-bdd`, ningún `*-test-data`.**

### 2 · Mover el archivo

`PolicyAdapters.kt` pasa tal cual, conservando el paquete `com.manahive.politica.adapters` para no tocar imports de los blueprints.

`pipeline-bdd` pasa a declarar `api(project(":engines:politica-engine:politica-adapters"))` y deja de contener el archivo. Los blueprints no cambian.

### 3 · Guarda para que no vuelva a pasar

`build-logic/` ya tiene plugins de convención con un *purity guard* para `manahive.pure-domain`. Agregar una verificación análoga: **ningún módulo cuyo nombre no termine en `-bdd` o `-test-data` puede depender de un módulo que sí.**

Es la regla que habría evitado este caso. Sin ella, el arreglo dura hasta el próximo atajo.

### 4 · Cerrar los huecos que el movimiento deja a la vista

Dos huecos reales, ambos con la misma forma: Política declara un campo y lo devuelve vacío.

**Harbor.** `PolicyResolver.resolve()` debe poblar `HarborPolicy.defaultChannels` y `escalationTimeouts` desde el catálogo. El DSL necesita poder expresarlos; hoy no puede. Si el DAG no tiene dónde ponerlos, agregar el bloque correspondiente en `DagDsl`.

**Recorder.** `resolveTransitionWindowsFromDag(catalog)` devuelve vacío para los catálogos actuales, pese a que la guía documenta `record(before = 2.min, after = 5.min)` sobre `lyingToStanding`. Verificar si el catálogo no lo define o si el resolvedor no lo lee, y arreglar el que corresponda.

**Si alguno de los dos excede el tamaño de esta spec, no improvisar:** anotarlo en el roadmap como spec nueva y dejar el campo vacío *con un test que documente que está vacío a propósito*. Lo que no puede quedar es un campo que parece resuelto y no lo está.

### 5 · Sincronizar la documentación

`docs/POLITICA-GUIDE.md` describe el diagrama `PolicyCalibration → cada motor` con valores de Harbor y Recorder que hoy no se producen. Corregir en el mismo commit, o marcar explícitamente qué parte es objetivo y cuál es estado actual. Ver `SPEC-07`.

---

## Criterios de aceptación

1. `PolicyAdapters.kt` está en `engines/politica-engine/politica-adapters`.
2. `grep -rn "pipeline-bdd" --include="*.kts" .` no muestra ningún módulo de producción dependiendo de él.
3. La guarda de `build-logic` falla el build si un módulo de producción declara dependencia sobre uno `-bdd` o `-test-data`. Debe haber una prueba de que la guarda efectivamente falla.
4. Correr los dos blueprints da **exactamente la misma salida** que antes del cambio, salvo por lo que se haya arreglado deliberadamente en la tarea 4.
5. Los campos de Harbor y Recorder están poblados, o hay un test que documenta que están vacíos a propósito y por qué.
6. `LANG=C.UTF-8 ./gradlew check` verde.

## Fuera de alcance

- Cablear los adapters dentro de los servicios Spring. Esta spec habilita esa posibilidad; usarla es otro trabajo.
- El presupuesto de fatiga de Harbor como lógica. Acá sólo se transporta la configuración.
