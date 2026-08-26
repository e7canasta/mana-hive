plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("jose301.MainKt")
}

dependencies {
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.contracts)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sceneEngine.sceneBdd)
    implementation(projects.engines.sceneEngine.sceneTestData)

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
