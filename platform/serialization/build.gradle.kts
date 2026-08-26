plugins {
    id("manahive.kotlin-common")
}

dependencies {
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.recorder.recorderDomain)

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.17.2")

    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
