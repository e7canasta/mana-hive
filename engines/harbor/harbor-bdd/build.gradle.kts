plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    api(projects.engines.harbor.harborDomain)
}
