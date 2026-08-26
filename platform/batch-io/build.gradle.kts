plugins {
    id("manahive.kotlin-common")
}

dependencies {
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.recorder.recorderDomain)
}
