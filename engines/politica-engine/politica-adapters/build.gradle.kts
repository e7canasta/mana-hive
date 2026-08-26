plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":platform:domain-kernel"))
    api(project(":platform:contracts"))
    api(project(":engines:politica-engine:politica-domain"))
    api(project(":engines:scene-engine:scene-domain"))
    api(project(":engines:sentinel:sentinel-domain"))
    api(project(":engines:harbor:harbor-domain"))
    api(project(":engines:recorder:recorder-domain"))

    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}
