val mainClass = "no.nav.helse.sparsom.AppKt"

val flywayCoreVersion = "9.1.6"
val testcontainersPostgresqlVersion = "1.19.0"
val mockkVersion = "1.12.5"
val rapidsAndRiversVersion = "2023093008351696055717.ffdec6aede3d"
val opensearchClientVersion = "2.1.4"
dependencies {
    implementation(project(":dao"))
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.jillesvangurp:search-client:$opensearchClientVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}

repositories {
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists()) it.copyTo(file)
            }
        }
    }
}
