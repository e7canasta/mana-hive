plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("simulator.MainKt")
}

dependencies {
    implementation(project(":platform:contracts"))
    implementation(project(":platform:messaging"))
    implementation(project(":platform:domain-kernel"))
    implementation(project(":engines:night-watch-runtime"))
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
}
