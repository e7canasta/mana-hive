plugins {
    id("manahive.pure-domain")
    `maven-publish`
}

dependencies {
    api(projects.platform.domainKernel)
}

// Shared Kernel — publicado como JAR para que mana-hub consuma los mismos tipos.
// Ver ADR-001 en docs/adr/ADR-001-Shared-Kernel-Contracts-JAR.md
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.manahive"
            artifactId = "contracts"
            version = "1.0.0"
            from(components["java"])
        }
    }
}
