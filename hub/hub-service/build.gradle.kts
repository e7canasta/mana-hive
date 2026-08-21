plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.hub.hubDomain)
    implementation(projects.platform.messaging)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly(libs.postgres)

    testImplementation(libs.testcontainers.postgres)
}
