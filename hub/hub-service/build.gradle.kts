plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.hub.hubDomain)
    implementation(projects.platform.messaging)
    implementation(projects.platform.contracts)
    // AD-1: Politica es el resolvedor canonico; el hub es su consumidor.
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    runtimeOnly(libs.postgres)

    testImplementation(libs.testcontainers.postgres)
}
