plugins {
    id("manahive.kotlin-common")
    application
}

application {
    mainClass.set("jose301policies.MainKt")
}

dependencies {
    implementation(project(":platform:domain-kernel"))
    implementation(project(":platform:contracts"))
    implementation(project(":engines:politica-engine:politica-domain"))
    implementation(project(":engines:politica-engine:politica-bdd"))
    implementation(project(":engines:politica-engine:politica-test-data"))
}

tasks.test {
    useJUnitPlatform()
}
