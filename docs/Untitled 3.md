Here is a comprehensive summary of the findings.

---

## Build-Logic Convention Plugin Structure

The `build-logic` directory at `/home/visiona/workspace/mana-hive/build-logic` is a Gradle composite build included via `includeBuild("build-logic")` in the root settings. It defines **three convention plugins** as precompiled script plugins (`.gradle.kts` files in `src/main/kotlin/`):

| Convention Plugin | File Path |
|---|---|
| `manahive.kotlin-common` | `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.kotlin-common.gradle.kts` |
| `manahive.pure-domain` | `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.pure-domain.gradle.kts` |
| `manahive.spring-service` | `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.spring-service.gradle.kts` |

Their plugin descriptors are also present at:
- `/home/visiona/workspace/mana-hive/build-logic/build/pluginDescriptors/manahive.kotlin-common.properties`
- `/home/visiona/workspace/mana-hive/build-logic/build/pluginDescriptors/manahive.pure-domain.properties`
- `/home/visiona/workspace/mana-hive/build-logic/build/pluginDescriptors/manahive.spring-service.properties`

---

### Plugin Dependency Chain

```
manahive.spring-service
  └── manahive.kotlin-common
        └── org.jetbrains.kotlin.jvm

manahive.pure-domain
  └── manahive.kotlin-common
        └── org.jetbrains.kotlin.jvm
```

---

## `manahive.pure-domain` Plugin (Full Content)

**File:** `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.pure-domain.gradle.kts`

```kotlin
plugins {
    id("manahive.kotlin-common")
}

// every public type in a pure module is API — and therefore a design decision
kotlin { explicitApi() }

val kotestVersion = "5.9.1"

dependencies {
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

/**
 * Purity guard: a pure-domain module may depend only on the Kotlin stdlib and
 * other pure modules of this build. Spring, JDBC, NATS: impossible, not
 * discouraged. Fine-grained purity (Instant.now(), mutability) is Konsist's job.
 */
val verifyPurity by tasks.registering {
    val offenders = configurations.named("runtimeClasspath").map { cfg ->
        cfg.allDependencies
            .filterIsInstance<ExternalModuleDependency>()
            .filterNot { it.group == "org.jetbrains.kotlin" }
            .map { "${it.group}:${it.name}" }
    }
    doLast {
        check(offenders.get().isEmpty()) {
            "Pure domain module ${project.path} declares external dependencies: ${offenders.get()}"
        }
    }
}
tasks.named("check") { dependsOn(verifyPurity) }
```

**What it does:**
1. Applies `manahive.kotlin-common` (which sets Kotlin JVM toolchain to 25, `allWarningsAsErrors`, `-Xjsr305=strict`, configures archive names, and JUnit Platform).
2. Enforces `explicitApi()` -- all public types must have explicit visibility modifiers.
3. Adds Kotest for test assertions and runner.
4. Registers a `verifyPurity` task hooked into `check` that **fails the build** if any non-Kotlin external dependency leaks into the runtime classpath. This is the architectural purity guard.

---

## `manahive.kotlin-common` Plugin (Full Content)

**File:** `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.kotlin-common.gradle.kts`

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// nested modules get fully-qualified artifact names (engines-vigia-vigia-domain...)
base.archivesName = project.path.removePrefix(":").replace(":", "-")

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// Test-dependency guard: production CODE must not depend on test modules.
// (modules ending in -bdd or -test-data are test-support modules)
// Blueprints are excluded.
val moduleName = project.name
val isProductionModule = !moduleName.endsWith("-bdd") &&
    !moduleName.endsWith("-test-data")
val isBlueprint = project.path.startsWith(":blueprints:")

if (isProductionModule && !isBlueprint) {
    afterEvaluate {
        val testDeps = configurations
            .filterNot { it.name.startsWith("test") }
            .flatMap { config ->
                config.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .filter { it.name.endsWith("-bdd") || it.name.endsWith("-test-data") }
                    .map { "${config.name} -> ${it.name}" }
            }.distinct()

        if (testDeps.isNotEmpty()) {
            val projectPath = project.path
            val verifyNoTestDeps by tasks.registering {
                doLast {
                    check(false) {
                        "Production module $projectPath depends on test module(s): ${testDeps.joinToString()}. ..."
                    }
                }
            }
            tasks.named("check") { dependsOn(verifyNoTestDeps) }
        }
    }
}
```

---

## `manahive.spring-service` Plugin (Full Content)

**File:** `/home/visiona/workspace/mana-hive/build-logic/src/main/kotlin/manahive.spring-service.gradle.kts`

```kotlin
plugins {
    id("manahive.kotlin-common")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(platform("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("spring-boot").get()}"))
    "implementation"("org.springframework.boot:spring-boot-starter")
    "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}
```

---

## Root Build File

There is **no root `build.gradle.kts` or `build.gradle`** in the project root (`/home/visiona/workspace/mana-hive/`). The root configuration is entirely in:

**File:** `/home/visiona/workspace/mana-hive/settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "mana-hive"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ── blueprints ─────
include(":blueprints:ana-e2e-standard")
include(":blueprints:jose-301-e2e-pipeline")
include(":blueprints:jose-301-harbor-delivery")
include(":blueprints:jose-301-recording")
include(":blueprints:jose-301-sentinel-alerts")
include(":blueprints:level-thresholds")
include(":blueprints:nats-e2e")
include(":blueprints:two-residents-e2e")

// ── engines ─────
include(":engines:harbor:harbor-batch")
include(":engines:harbor:harbor-bdd")
include(":engines:harbor:harbor-domain")
include(":engines:harbor:harbor-test-data")
include(":engines:night-watch-runtime")
include(":engines:pipeline:pipeline-batch")
include(":engines:pipeline:pipeline-bdd")
include(":engines:politica-engine:politica-adapters")
include(":engines:politica-engine:politica-batch")
include(":engines:politica-engine:politica-bdd")
include(":engines:politica-engine:politica-domain")
include(":engines:politica-engine:politica-test-data")
include(":engines:recorder:recorder-batch")
include(":engines:recorder:recorder-bdd")
include(":engines:recorder:recorder-domain")
include(":engines:recorder:recorder-test-data")
include(":engines:scene-engine:scene-batch")
include(":engines:scene-engine:scene-bdd")
include(":engines:scene-engine:scene-domain")
include(":engines:scene-engine:scene-test-data")
include(":engines:sentinel:sentinel-batch")
include(":engines:sentinel:sentinel-bdd")
include(":engines:sentinel:sentinel-domain")

// ── platform ─────
include(":platform:batch-io")
include(":platform:blueprint-harness")
include(":platform:contracts")
include(":platform:domain-kernel")
include(":platform:infrastructure")
include(":platform:messaging")
include(":platform:profile-api")
include(":platform:serialization")

// ── examples ─────
include(":examples:jose-e1")
```

---

## Publication Status

Currently, **only one module** in the entire project has `maven-publish` applied:

**File:** `/home/visiona/workspace/mana-hive/platform/profile-api/build.gradle.kts`

```kotlin
plugins {
    id("manahive.pure-domain")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.manahive"
            artifactId = "profile-api"
            version = "1.0.0-SNAPSHOT"
            from(components["java"])
        }
    }
}
```

There is **no shared publication convention plugin** in `build-logic` yet -- the `maven-publish` plugin and publication block are defined inline in that one module only.

---

## Key Observations for Publication Planning

1. **No root build file** exists -- configuration is entirely in `settings.gradle.kts` and per-module `build.gradle.kts` files.
2. **No shared publication plugin** -- the `maven-publish` setup is ad-hoc in `profile-api`. If you want consistent publication across modules, you would create a new convention plugin (e.g., `manahive.java-library`) in `build-logic/src/main/kotlin/` that applies `maven-publish` and configures the publication block with group/artifact/version from a common source.
3. **The version catalog** (`gradle/libs.versions.toml`) defines shared versions but has no publication-related entries.
4. **Group ID convention**: `com.manahive` is established by the existing profile-api publication.
5. **The `manahive.pure-domain` plugin** already provides a good base -- it is designed for pure Kotlin modules with no external dependencies. Any new publication plugin could build on top of it (similar to how `manahive.spring-service` builds on `manahive.kotlin-common`).
6. **Archive naming** is already well-handled in `manahive.kotlin-common`: `base.archivesName = project.path.removePrefix(":").replace(":", "-")`, which produces names like `engines-sentinel-sentinel-domain`.