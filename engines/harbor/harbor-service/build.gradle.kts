plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.harbor.harborDomain)
}
