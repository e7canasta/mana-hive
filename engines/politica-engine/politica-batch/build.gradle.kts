plugins {
    application
    id("manahive.kotlin-common")
}

application {
    mainClass.set("com.manahive.politica.batch.PoliticaBatchAppKt")
}

val kotestVersion = "5.9.1"

dependencies {
    implementation(projects.engines.politicaEngine.politicaDomain)
    implementation(projects.platform.contracts)
    implementation(projects.platform.domainKernel)

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}
