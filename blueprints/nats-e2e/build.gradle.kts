plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("natse2e.MainKt")
}

dependencies {
    implementation(project(":platform:domain-kernel"))
    implementation(project(":platform:contracts"))
    implementation(project(":platform:messaging"))
    implementation(project(":platform:serialization"))
    implementation(project(":engines:scene-engine:scene-domain"))
    implementation(project(":engines:sentinel:sentinel-domain"))
    implementation(project(":engines:harbor:harbor-domain"))
    implementation(project(":engines:recorder:recorder-domain"))
    implementation(project(":engines:politica-engine:politica-domain"))
    implementation(project(":engines:politica-engine:politica-adapters"))
    implementation(project(":engines:night-watch-runtime"))

    implementation(libs.jnats)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
}
