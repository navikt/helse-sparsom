val mainClass = "no.nav.helse.sparsom.AppKt"

val rapidsAndRiversVersion = "2024112511071732529266.253c42b70448"
dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
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
