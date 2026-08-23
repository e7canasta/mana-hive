plugins { id("manahive.pure-domain") }

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    implementation(projects.platform.infrastructure)
}
