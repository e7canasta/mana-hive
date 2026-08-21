plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.vigia.vigiaDomain)
    implementation(projects.platform.messaging)
}
