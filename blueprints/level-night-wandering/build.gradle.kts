plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("levelnw.MainKt")
}

dependencies {
    implementation(project(":platform:domain-kernel"))
    implementation(project(":platform:contracts"))
    implementation(project(":engines:scene-engine:scene-domain"))
    implementation(project(":engines:scene-engine:scene-bdd"))
    implementation(project(":engines:scene-engine:scene-test-data"))
    implementation(project(":engines:sentinel:sentinel-domain"))
    implementation(project(":engines:harbor:harbor-domain"))
    implementation(project(":engines:harbor:harbor-test-data"))
    implementation(project(":engines:recorder:recorder-domain"))
    implementation(project(":engines:recorder:recorder-test-data"))
    implementation(project(":engines:politica-engine:politica-domain"))
    implementation(project(":engines:pipeline:pipeline-bdd"))
}
