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


// ── blueprints: escenarios ejecutables (fallan barato, antes que el bus) ──────
include(":blueprints:ana-e2e-standard")
include(":blueprints:jose-301-e2e-pipeline")
include(":blueprints:jose-301-harbor-delivery")
include(":blueprints:jose-301-recording")
include(":blueprints:jose-301-sentinel-alerts")
include(":blueprints:jose-301-sitting-bed")
include(":blueprints:level-critical")
include(":blueprints:level-fall-risk")
include(":blueprints:level-night-wandering")
include(":blueprints:nats-e2e")
include(":blueprints:two-residents-e2e")

// ── engines: nucleo puro + cascara delgada. night-watch-runtime es el deployable 
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

// ── hub: el System of Record ──────────────────────────────────────────────────
include(":hub:hub-batch")
include(":hub:hub-domain")
include(":hub:hub-service")

// ── platform: kernel compartido, lenguaje publicado, convenciones del bus ─────
include(":platform:batch-io")
include(":platform:blueprint-harness")
include(":platform:contracts")
include(":platform:domain-kernel")
include(":platform:infrastructure")
include(":platform:messaging")
include(":platform:profile-api")
include(":platform:serialization")

// ── examples: pruebas de integración y arranque en frío ─────────────────────
include(":examples:jose-e1")

// Los *-service de motor viven en .archive: night-watch-runtime los reemplaza.
