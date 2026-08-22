plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation(projects.platform.messaging)
}
