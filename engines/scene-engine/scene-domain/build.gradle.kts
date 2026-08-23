plugins {
    id("manahive.pure-domain")
    application
}

application {
    mainClass.set("com.manahive.scene.MainKt")
}

dependencies {
    api(projects.platform.domainKernel)
    api(projects.platform.contracts)
    implementation(projects.platform.infrastructure)
}
