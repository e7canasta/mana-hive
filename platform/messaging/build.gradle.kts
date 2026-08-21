plugins { id("manahive.kotlin-common") }

dependencies {
    api(projects.platform.contracts)
    api(libs.jnats)
    implementation(libs.jackson.kotlin)
}
