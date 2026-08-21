plugins { id("manahive.spring-service") }

dependencies {
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.platform.messaging)
}
