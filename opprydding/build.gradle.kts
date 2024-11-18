val mainClass = "no.nav.helse.sparsom.opprydding.AppKt"
val rapidsAndRiversVersion = "2024111809201731918023.7c8474fdd5eb"

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
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
