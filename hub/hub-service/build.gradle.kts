plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.hub.hubDomain)
    implementation(projects.platform.messaging)
    implementation(projects.platform.contracts)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    runtimeOnly(libs.postgres)

    testImplementation(libs.testcontainers.postgres)
}
