plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":platform:domain-kernel"))
    api(project(":platform:contracts"))
    api(project(":engines:politica-engine:politica-domain"))
    api(project(":engines:scene-engine:scene-domain"))
    api(project(":engines:scene-engine:scene-bdd"))
    api(project(":engines:scene-engine:scene-test-data"))
    api(project(":engines:sentinel:sentinel-domain"))
    api(project(":engines:sentinel:sentinel-bdd"))
    api(project(":engines:harbor:harbor-domain"))
    api(project(":engines:harbor:harbor-bdd"))
    api(project(":engines:harbor:harbor-test-data"))
    api(project(":engines:recorder:recorder-domain"))
    api(project(":engines:recorder:recorder-bdd"))
    api(project(":engines:recorder:recorder-test-data"))
}
