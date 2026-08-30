plugins { id("manahive.spring-service") }

dependencies {
    implementation(project(":platform:serialization"))
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.messaging)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.recorder.recorderDomain)
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation(projects.engines.politicaEngine.politicaAdapters)
    implementation(projects.hub.hubDomain)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly(libs.jnats)
    testImplementation(libs.jnats)
}
