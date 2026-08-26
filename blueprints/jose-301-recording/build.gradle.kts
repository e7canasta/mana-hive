plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("jose301recording.MainKt")
}

dependencies {
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.contracts)
    implementation(projects.engines.recorder.recorderDomain)
    implementation(projects.engines.recorder.recorderBdd)
    implementation(projects.engines.recorder.recorderTestData)

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
