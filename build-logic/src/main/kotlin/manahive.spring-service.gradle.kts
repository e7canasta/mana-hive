plugins {
    id("manahive.kotlin-common")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(platform("org.springframework.boot:spring-boot-dependencies:${libs.findVersion("spring-boot").get()}"))
    "implementation"("org.springframework.boot:spring-boot-starter")
    "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}
