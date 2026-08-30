plugins { id("manahive.pure-domain") }

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)

    // El contrato de frontera que implementa el sistema de registro externo.
    // Los dos modulos son pure-domain, asi que el purity guard lo permite: el
    // mapper vive de este lado porque traducir la frontera es trabajo nuestro,
    // no de quien la implementa.
    api(projects.platform.profileApi)
}
