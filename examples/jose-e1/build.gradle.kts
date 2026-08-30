plugins {
    id("manahive.kotlin-common")
    `java-library`
    application
}

application {
    mainClass.set("jose301.MainPipelineKt")
}

dependencies {
    implementation(project(":engines:scene-engine:scene-domain"))
    implementation(project(":engines:scene-engine:scene-bdd"))
    implementation(project(":engines:scene-engine:scene-batch"))
    implementation(project(":engines:scene-engine:scene-test-data"))
    implementation(project(":engines:sentinel:sentinel-domain"))
    implementation(project(":engines:harbor:harbor-domain"))
    implementation(project(":engines:recorder:recorder-domain"))
    implementation(project(":engines:recorder:recorder-batch"))
    implementation(project(":engines:night-watch-runtime"))
    implementation(project(":platform:contracts"))
    implementation(project(":platform:domain-kernel"))
    implementation(project(":platform:profile-api"))
    implementation(project(":platform:blueprint-harness"))
    implementation(project(":platform:batch-io"))
    implementation(project(":engines:politica-engine:politica-domain"))
    implementation(project(":engines:politica-engine:politica-adapters"))
    
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)
}
