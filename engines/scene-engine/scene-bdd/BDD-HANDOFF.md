# Handoff: BDD & Test Data Guide para mana-hive

## Arquitectura actual

```
engines/
  <engine>/
    <engine>-domain/      → dominio puro (sin framework)
    <engine>-batch/       → CLI batch tool (run/verify/diff)
    <engine>-bdd/         → DSL BDD reutilizable
    <engine>-test-data/   → "design system" de patrones base
    <engine>-service/     → Spring shell (NATS ingest/egress)

blueprints/
  <resident>-<feature>/   → runnable examples & regression tests
    src/main/kotlin/
      Shared.kt           → constants + BDD context
      Episodes.kt         → episodios (usa base patterns)
      Calibrations.kt     → configs específicas
      Main.kt             → runner/demo
```

## Módulos creados hasta ahora

| Módulo | Contenido | Archivos clave |
|--------|-----------|----------------|
| `scene-bdd/` | Scenario DSL, assertions, FactRenderer | `Scenario.kt`, `FactRenderer.kt` |
| `scene-test-data/` | bathroomJourney(), sittingEpisode() | `BasePatterns.kt` |
| `sentinel-bdd/` | SentinelScenario DSL | `SentinelBdd.kt` |
| `harbor-bdd/` | HarborScenario DSL | `HarborBdd.kt` |
| `harbor-test-data/` | testCalibration(), helpers | `BasePatterns.kt` |

---

## Cómo crear un nuevo módulo BDD

### Paso 1: Crear directorio y build.gradle.kts

```bash
mkdir -p engines/<engine>/<engine>-bdd/src/main/kotlin/com/manahive/<engine>/bdd
```

**build.gradle.kts:**
```kotlin
plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    api(projects.engines.<engine>.<engine>Domain)
}
```

### Paso 2: Registrar en settings.gradle.kts

```kotlin
include(":engines:<engine>:<engine>-bdd")
```

### Paso 3: Crear Context

El Context encapsula los datos específicos del blueprint (bed, resident, calibration, start time).

```kotlin
data class MyEngineContext(
    val bed: BedId,
    val resident: ResidentId,
    val calibration: MyCalibration,
    val start: Instant,
    // ... otros datos específicos
)
```

### Paso 4: Crear ScenarioBuilder

El Builder sigue el patrón:
1. **Acumular inputs** (observations, facts, signals)
2. **Definir assertions** (lazy, solo se evalúan en `run()`)
3. **Ejecutar** (`run()` llama al engine, recolecta outputs, ejecuta assertions)

```kotlin
class MyScenarioBuilder(private val ctx: MyEngineContext) {
    private val inputs = mutableListOf<InputAt>()
    private val assertions = mutableListOf<Pair<String, () -> Unit>>()
    private var lastOutput: List<OutputType> = emptyList()

    // ── Input builders ────────────────────────────────────────
    fun givenSomething(...) { inputs.add(...) }

    // ── Assertion builders ────────────────────────────────────
    fun thenExpectX(block: (OutputType) -> Unit = {}) {
        assertions.add("X" to {
            val result = lastOutput.firstOrNull { it is X }
            val actual = lastOutput.joinToString { it::class.simpleName ?: "?" }
            assert(result != null) { "Expected X but not found.\n     Actual: $actual" }
            block(result!!)
        })
    }

    fun thenExpectXCount(count: Int) {
        assertions.add("$count X" to {
            val actual = lastOutput.filterIsInstance<X>().size
            assert(actual == count) { "Expected $count X, got $actual" }
        })
    }

    // ── Run ───────────────────────────────────────────────────
    fun run(): MyScenarioResult {
        val engine = createEngine(ctx.calibration)
        var state = initialState()
        val allOutputs = mutableListOf<OutputType>()

        for (input in inputs) {
            val result = engine.process(input.value, state, input.at)
            state = result.state
            allOutputs.addAll(result.outputs)
        }

        lastOutput = allOutputs
        // ... evaluar assertions, retornar resultado
    }
}
```

### Paso 5: Crear top-level DSL

```kotlin
fun MyEngineContext.scenario(
    name: String,
    block: MyScenarioBuilder.() -> Unit,
): MyScenarioResult {
    val builder = MyScenarioBuilder(this)
    builder.block()
    return builder.run().let { it.copy(name = name) }
}
```

### Paso 6: Crear result type

```kotlin
data class MyScenarioResult(
    val name: String,
    val outputs: List<OutputType>,
    val checks: List<ScenarioCheck>,
) {
    val passed: Boolean get() = checks.all { it.passed }

    fun report() {
        println("  ── Scenario: $name ──")
        println("  Outputs: ${outputs.size}")
        println()
        checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            println("  $status ${check.description}")
            if (check.error != null) println("     ${check.error}")
        }
        println()
    }
}
```

---

## Cómo crear Test Data (Design System)

### Paso 1: Crear directorio y build.gradle.kts

```bash
mkdir -p engines/<engine>/<engine>-test-data/src/main/kotlin/com/manahive/<engine>/testdata
```

**build.gradle.kts:**
```kotlin
plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    api(projects.engines.<engine>.<engine>Domain)
    api(projects.engines.<engine>.<engine>Bdd)
}
```

### Paso 2: Crear helpers de construcción

Funciones que construyen objetos del dominio de forma declarativa:

```kotlin
// Helper para crear observaciones
fun MyContext.obs(kind: ObservationKind, at: String, confidence: Double): ObservedAt {
    val instant = timeParser(at)
    return ObservedAt(Observation(..., observedAt = instant), instant)
}

// Helper para crear señales
fun episodeOpened(bed: BedId, resident: ResidentId, at: Instant, ...): SentinelSignal {
    return SentinelSignal.EpisodeOpened(bed = bed, resident = resident, at = at, ...)
}
```

### Paso 3: Crear patrones base

Patrones que representan comportamientos del mundo real validados:

```kotlin
// Patrón: baño completo
fun MyContext.bathroomJourney(
    sitting: String, standing: String, inBathroom: String,
    inRoom: String, backInBed: String,
): Episode = Episode(listOf(
    obs(ObservationKind.SITTING_IN_BED, sitting, 0.91),
    obs(ObservationKind.STANDING, standing, 0.88),
    obs(ObservationKind.IN_BATHROOM, inBathroom, 0.85),
    obs(ObservationKind.IN_ROOM, inRoom, 0.87),
    obs(ObservationKind.IN_BED, backInBed, 0.93),
))

// Patrón: calibración de prueba
fun testCalibration(...): MyCalibration = myCalibration {
    resident("test")
    // ... configuración base para tests
}
```

### Paso 4: Registrar en settings.gradle.kts

```kotlin
include(":engines:<engine>:<engine>-test-data")
```

---

## Cómo crear un Blueprint

### Paso 1: Crear directorio y build.gradle.kts

```bash
mkdir -p blueprints/<resident>-<feature>/src/main/kotlin/<pkg>
```

**build.gradle.kts:**
```kotlin
plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("<pkg>.MainKt")
}

dependencies {
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.contracts)
    implementation(projects.engines.<engine>.<engine>Domain)
    implementation(projects.engines.<engine>.<engine>Bdd)
    implementation(projects.engines.<engine>.<engine>TestData)

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
```

### Paso 2: Registrar en settings.gradle.kts

```kotlin
include(":blueprints:<resident>-<feature>")
```

### Paso 3: Crear Shared.kt

```kotlin
package <pkg>

val BED_4 = BedId("bed-4")
val NIGHT = NightId("night-...")
val RESIDENT = ResidentId("...")
val START = Instant.parse("...")

fun myCtx(cal: MyCalibration) = MyContext(
    bed = BED_4,
    resident = RESIDENT,
    calibration = cal,
    start = START,
)
```

### Paso 4: Crear Calibrations.kt

```kotlin
package <pkg>

val configBasica = myCalibration {
    resident("...")
    // ... reglas básicas
}

val configCompleta = myCalibration {
    resident("...")
    // ... reglas con fatigue,umbrella, etc.
}
```

### Paso 5: Crear Episodes.kt (si aplica)

```kotlin
package <pkg>

val e1: Episode = myCtx.sittingEpisode(
    inBed = "0s",
    sitting = "1h15m",
    backInBed = "1h32m",
)

val e2: Episode = myCtx.bathroomJourney(
    sitting = "2h47m", standing = "2h48m",
    inBathroom = "2h50m", inRoom = "3h00m", backInBed = "3h02m",
)
```

### Paso 6: Crear Main.kt

```kotlin
package <pkg>

fun main() {
    val ctx = myCtx(configBasica)

    ctx.scenario("Descripción del escenario") {
        givenSomething(...)
        thenExpectSomething()
    }.report()

    // ... más escenarios
}
```

---

## Reglas de diseño

### Separación de responsabilidades

```
SENTINEL (juicio clínico)
  - SIEMPRE produce episodios (hechos)
  - NO tiene fatigue
  - NO suprime nada

HARBOR (entrega)
  - PUEDE suprimir notificaciones (fatigue)
  - SIEMPRE crea notices (audit)
  - Controla dispatch

SCENE (detección)
  - Detecta hechos del mundo real
  - NO decide si algo merece alerta
```

### Assertion patterns

**ESTÁNDAR:** Usar `assert()` con lazy messages, nunca `throw AssertionError`:

```kotlin
// ✅ BIEN
assert(signal != null) { "Expected X but not found.\n     Actual: $actual" }

// MAL
if (signal == null) throw AssertionError("Expected X")
```

### Naming conventions

- Context: `<Engine>Context` (HarborContext, SentinelContext)
- Scenario builder: `<Engine>ScenarioBuilder`
- Scenario result: `<Engine>ScenarioResult`
- Top-level DSL: `<engine>.scenario("name") { ... }`
- Test data: `<pattern>Episode()`, `test<Calibration>()`

### Fatigue belongs to Harbor

```kotlin
// Harbor calibrations
harborCalibration {
    resident("jose")
    fatigue {
        warning(5)   // max 5 notificaciones WARNING por shift
        info(3)      // max 3 notificaciones INFO por shift
        // CRITICAL: sin límite (implícito)
    }
}
```

---

## Archivos de referencia

| Archivo | Qué contiene |
|---------|-------------|
| `scene-bdd/Scenario.kt` | DSL completo con episodes, assertions, comparison |
| `sentinel-bdd/SentinelBdd.kt` | DSL para Sentinel signals |
| `harbor-bdd/HarborBdd.kt` | DSL para Harbor commands + fatigue |
| `scene-test-data/BasePatterns.kt` | bathroomJourney(), sittingEpisode() |
| `harbor-test-data/BasePatterns.kt` | testCalibration(), episode helpers |
| `blueprints/jose-301-sitting-bed/` | Blueprint completo (5 archivos) |
| `blueprints/jose-301-sentinel-alerts/` | Blueprint Sentinel (3 archivos) |
| `blueprints/jose-301-harbor-delivery/` | Blueprint Harbor (2 archivos) |

---

## Próximos engines

### Recorder
- **Dominio:** grabación, persistencia, NVR
- **BDD:** `recorder-bdd/` → recording {}, playback {}, snapshot {}
- **Test data:** `recorder-test-data/` → fullNightRecording(), partialCapture()
- **Blueprint:** `jose-301-recording`

### Politica
- **Dominio:** reglas de negocio, perfiles, overrides
- **BDD:** `politica-bdd/` → rule {}, condition {}, effectiveRules {}
- **Test data:** `politica-test-data/` → nightPolicy(), attendancePolicy()
- **Blueprint:** `jose-301-policies`
