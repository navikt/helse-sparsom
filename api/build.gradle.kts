val micrometerRegistryPrometheusVersion = "1.9.0"
val ktorVersion = "2.0.1"
val wireMockVersion = "2.31.0"
val cloudSqlVersion = "1.7.2"
val awaitilityVersion = "4.1.1"
val flywayCoreVersion = "9.1.6"
val testcontainersPostgresqlVersion = "1.17.1"
val mockVersion = "1.12.4"

val mainClass = "no.nav.helse.sparsom.api.AppKt"

dependencies {
    implementation("com.google.cloud.sql:postgres-socket-factory:$cloudSqlVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    testImplementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion") {
        exclude("com.fasterxml.jackson.core")
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}
