plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    api(projects.platform.blueprintHarness)
    api(projects.engines.sentinel.sentinelDomain)
}
