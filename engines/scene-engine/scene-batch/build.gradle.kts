plugins {
    application
    id("manahive.kotlin-common")
}

application {
    mainClass.set("com.manahive.scene.batch.SceneBatchAppKt")
}

val kotestVersion = "5.9.1"

dependencies {
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}
