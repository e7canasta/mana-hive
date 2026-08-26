plugins {
    application
    id("manahive.kotlin-common")
}

application {
    mainClass.set("com.manahive.pipeline.batch.PipelineBatchAppKt")
}

dependencies {
    implementation(projects.platform.domainKernel)
    implementation(projects.platform.contracts)
    implementation(projects.platform.batchIo)
    implementation(projects.engines.sceneEngine.sceneDomain)
    implementation(projects.engines.sentinel.sentinelDomain)
    implementation(projects.engines.harbor.harborDomain)
    implementation(projects.engines.recorder.recorderDomain)
    implementation(projects.engines.politicaEngine.politicaDomain)

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}
