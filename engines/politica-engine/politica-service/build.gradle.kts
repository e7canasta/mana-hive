plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation(projects.platform.messaging)
    implementation(projects.platform.contracts)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
