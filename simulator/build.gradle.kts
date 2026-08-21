plugins {
    id("manahive.kotlin-common")
    application
}

dependencies {
    implementation(projects.platform.contracts)
    implementation(projects.platform.messaging)
}

application { mainClass = "com.manahive.simulator.MainKt" }
