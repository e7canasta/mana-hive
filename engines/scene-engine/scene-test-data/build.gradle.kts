plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    api(projects.engines.sceneEngine.sceneDomain)
    api(projects.engines.sceneEngine.sceneBdd)
}
