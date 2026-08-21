plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.platform.messaging)
}
