plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.sparsom.api.AppKt"
    imageName = "helse-sparsom-api"
}

dependencies {
    implementation(libs.tbd.libs.naisful.app)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }
    implementation(libs.search.client)
    implementation(libs.logback.classic)
    implementation(libs.logback.logstash.encoder) {
        exclude("com.fasterxml.jackson.core")
        exclude("com.fasterxml.jackson.dataformat")
    }
    implementation(libs.jackson.module.kotlin) {
        exclude("org.jetbrains.kotlin:kotlin-reflect")
    }
    implementation(libs.jackson.datatype.jsr310)
}
