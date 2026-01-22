val tbdLibsVersion = "2026.01.22-09.16-1d3f6039"
val mainClass = "no.nav.helse.sparsom.api.AppKt"

dependencies {
    implementation("com.github.navikt.tbd-libs:naisful-app:$tbdLibsVersion")
    implementation("io.ktor:ktor-server-auth-jwt") {
        exclude(group = "junit")
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
            val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
            if (!file.exists()) it.copyTo(file)
        }
    }
}
