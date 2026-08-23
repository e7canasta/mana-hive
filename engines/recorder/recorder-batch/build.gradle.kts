plugins {
    id("manahive.kotlin-common")
    application
}

dependencies {
    implementation(projects.engines.recorder.recorderDomain)
    implementation(projects.platform.contracts)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.github.ajalt.clikt:clikt:4.2.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

application {
    mainClass.set("com.manahive.recorder.batch.RecorderBatchAppKt")
}
