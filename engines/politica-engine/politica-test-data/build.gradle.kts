plugins {
    id("manahive.kotlin-common")
    `java-library`
}

dependencies {
    api(project(":platform:domain-kernel"))
    api(project(":platform:contracts"))
    api(project(":engines:politica-engine:politica-domain"))
    api(project(":engines:politica-engine:politica-bdd"))
}
