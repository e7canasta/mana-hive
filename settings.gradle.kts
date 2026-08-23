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

// ── platform: shared kernel, published language, bus conventions ─────────────
include(":platform:domain-kernel")
include(":platform:contracts")
include(":platform:messaging")
include(":platform:infrastructure")

// ── hub: the System of Record (multiple bounded contexts inside) ─────────────
include(":hub:hub-domain")
include(":hub:hub-batch")
include(":hub:hub-service")

// ── engines: one deployable per responsibility, pure core + thin shell ───────
include(":engines:scene-engine:scene-domain")
include(":engines:scene-engine:scene-batch")
include(":engines:scene-engine:scene-service")
include(":engines:politica-engine:politica-domain")
include(":engines:politica-engine:politica-batch")
include(":engines:politica-engine:politica-service")
include(":engines:sentinel:sentinel-domain")
include(":engines:sentinel:sentinel-batch")
include(":engines:sentinel:sentinel-service")
include(":engines:harbor:harbor-domain")
include(":engines:harbor:harbor-batch")
include(":engines:harbor:harbor-service")
include(":engines:recorder:recorder-domain")
include(":engines:recorder:recorder-batch")

// ── tooling ───────────────────────────────────────────────────────────────────
include(":simulator")
