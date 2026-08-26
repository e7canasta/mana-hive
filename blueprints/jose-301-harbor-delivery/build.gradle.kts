plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("jose301harbor.MainKt")
}

dependencies {
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.contracts)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.harbor.harborBdd)
    implementation(projects.engines.harbor.harborTestData)

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
