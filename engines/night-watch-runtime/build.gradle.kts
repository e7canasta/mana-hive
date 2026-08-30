plugins { id("manahive.spring-service") }

dependencies {
    implementation(project(":platform:serialization"))
    implementation(project(":platform:batch-io"))
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.messaging)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.recorder.recorderDomain)
    implementation(projects.engines.recorder.recorderBatch) {
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
    }
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation(projects.engines.politicaEngine.politicaAdapters)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly(libs.jnats)
    testImplementation(libs.jnats)
}

// JNA nunca en runtime: mordant/jna es CLI puro de batch, no debe contaminar el servicio 24/7 ni Leyden/GraalVM
configurations.named("runtimeClasspath") {
    exclude(group = "net.java.dev.jna", module = "jna")
    exclude(group = "net.java.dev.jna", module = "jna-platform")
}
