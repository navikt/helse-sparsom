plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.sparsom.AppKt"
    imageName = "helse-sparsom-sparsom"
}

dependencies {
    implementation(libs.rapids.and.rivers)
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
