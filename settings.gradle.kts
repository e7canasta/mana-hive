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

// ── hub: the System of Record (multiple bounded contexts inside) ─────────────
include(":hub:hub-domain")
include(":hub:hub-service")

// ── engines: one deployable per responsibility, pure core + thin shell ───────
include(":engines:scene-engine:scene-domain")
include(":engines:scene-engine:scene-service")
include(":engines:sentinel:sentinel-domain")
include(":engines:sentinel:sentinel-service")
include(":engines:vigia:vigia-domain")
include(":engines:vigia:vigia-service")

// ── tooling ───────────────────────────────────────────────────────────────────
include(":simulator")
